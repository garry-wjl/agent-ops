package ink.garry.rd.agent.ws.application.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.agent.A2aSyncCandidateVO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.repository.A2aSyncHistoryRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.a2a.NacosAgentCardFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * A2A 同步应用层编排服务（hotfix_20260625_a2a-create-endpoints 同步能力补充）。
 * <p>
 * <b>定位</b>：本服务不属于"写侧命令"，而是"读 → 写"的协调层；放在 application 层而非
 * {@link AgentCommandService} 内，避免 CommandService 进一步膨胀。Controller / Scheduler
 * 等入口都打到本服务同一方法 {@link #syncPendingBatch(int)} 上。
 * <p>
 * <b>职责</b>：批量推进 status=PENDING_SYNC 的 A2A Agent 至 PUBLISHED / OFFLINE。区别于：
 * <ul>
 *   <li>{@code NacosAgentCardFetcher}（infra）—— 拉远端 AgentCard 单条同步</li>
 *   <li>{@link AgentCommandService#manualResync} —— 单条手动重试（用户操作，加锁）</li>
 *   <li>{@link AgentCommandService#syncByNum} —— Nacos 推送回调（已含源信息，无需再 fetch）</li>
 *   <li>本服务 —— 兜底轮询，无源信息、需要主动 fetch，含冷却窗口与失败容错</li>
 * </ul>
 * <p>
 * <b>失败容错</b>：每条独立事务，单条失败仅记日志 + 写历史，循环继续；不阻断后续 Agent。
 * 这是兜底对账任务的合理选择 —— 一次轮询的目的是"尽可能推进多数 Agent"，单条失败属于
 * 异常情况，留给下次轮询 / 手动 / Nacos 推送自行恢复。
 */
@Slf4j
@Service
public class A2aSyncApplicationService {

    /** 操作人标识，与 {@link AgentCommandService#SYNC_OPERATOR} 一致（nacos-sync） */
    private static final String SYNC_OPERATOR = "nacos-sync";

    /** 单次批量对账默认上限：50（防 Nacos 限流 + 单轮跑完耗时可控） */
    public static final int DEFAULT_BATCH_SIZE = 50;

    private final AgentQueryService agentQueryService;
    private final AgentFactory agentFactory;
    private final A2aSyncHistoryRepository a2aSyncHistoryRepository;
    /**
     * Nacos 拉取器懒依赖：fetcher 在 {@code agentscope.a2a.nacos.discovery.enabled=false} 时
     * 不被注册,这里用 {@link ObjectProvider} 允许本服务在 discovery 关闭的环境下也能装配,
     * 同步动作退化为"无可用 fetcher,本轮跳过"——与 syncOne / resyncByNum 现有的容错语义一致。
     */
    private final ObjectProvider<NacosAgentCardFetcher> fetcherProvider;

    public A2aSyncApplicationService(AgentQueryService agentQueryService,
                                      AgentFactory agentFactory,
                                      A2aSyncHistoryRepository a2aSyncHistoryRepository,
                                      ObjectProvider<NacosAgentCardFetcher> fetcherProvider) {
        this.agentQueryService = agentQueryService;
        this.agentFactory = agentFactory;
        this.a2aSyncHistoryRepository = a2aSyncHistoryRepository;
        this.fetcherProvider = fetcherProvider;
    }

    /**
     * 批量推进 PENDING_SYNC Agent 至 PUBLISHED / OFFLINE。
     * <p>
     * <b>流程</b>：
     * <ol>
     *   <li>调 {@link AgentQueryService#listPendingSyncCandidates} 取候选（已含 1min 冷却窗口过滤）</li>
     *   <li>逐条：{@code fetcher.fetch(name)} 拉远端；失败仅记日志 + 写历史 + 继续</li>
     *   <li>成功：重建 Agent，调 {@code applyNacosSync} 写回（healthy 字段缺失时按"远端可达=PUBLISHED"默认映射）</li>
     *   <li>每条独立事务，失败不阻断后续</li>
     * </ol>
     * <p>
     * <b>触发错误码</b>：远端 Nacos 不可达 → {@link BizCode#A2A_REMOTE_UNREACHABLE}(2011)。
     * 本方法不抛该异常到调用方（兜底任务不希望因为单条不可达而中断整轮），仅记日志 + 历史后跳过。
     *
     * @param batchSize 单次批量上限（≤0 时取 {@link #DEFAULT_BATCH_SIZE}）
     * @return 实际处理条数（含失败条数；调用方按需打 INFO 监控）
     */
    public int syncPendingBatch(int batchSize) {
        int limit = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        NacosAgentCardFetcher fetcher = fetcherProvider.getIfAvailable();
        if (fetcher == null) {
            log.info("[A2aSync] syncPendingBatch NacosAgentCardFetcher 未装配(agentscope.a2a.nacos.discovery.enabled=false),本轮跳过");
            return 0;
        }
        List<A2aSyncCandidateVO> candidates = agentQueryService.listPendingSyncCandidates(limit);
        if (candidates.isEmpty()) {
            log.debug("[A2aSync] syncPendingBatch 无候选 limit={}", limit);
            return 0;
        }

        log.info("[A2aSync] syncPendingBatch 开始 limit={} actual={}", limit, candidates.size());
        int processed = 0;
        for (A2aSyncCandidateVO candidate : candidates) {
            try {
                syncOne(candidate);
            } catch (Exception e) {
                // 单条失败不影响后续；写历史时已尽量兜底，这里再保险一次
                log.warn("[A2aSync] syncPendingBatch 单条失败 num={} name={} err={}",
                        candidate.getNum(), candidate.getName(), e.getMessage(), e);
            } finally {
                processed++;
            }
        }
        log.info("[A2aSync] syncPendingBatch 完成 processed={} limit={}", processed, limit);
        return processed;
    }

    /**
     * 单条同步（每条独立事务）。
     * <p>
     * <b>状态映射</b>：当前 fetcher 没有 healthy 字段时，统一视为 PUBLISHED
     * （"远端可达即认为在线"）；后续接 Nacos 实例健康度时再扩展
     * {@code AgentCardDetailInfo.getRegistrationType()} 判断。
     * <p>
     * <b>复用关系</b>：{@link #resyncByNum} 是本方法的对外入口版（允许指定 eventType / operator），
     * 详见该方法注释。{@code syncOne} 仅作为兜底轮询路径使用（{@link SyncEventType#POLLING_RECONCILE} +
     * {@link #SYNC_OPERATOR}），保持兜底对账失败不影响用户主动操作的语义清晰。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void syncOne(A2aSyncCandidateVO candidate) {
        Assert.notNull(candidate, "candidate 不能为空");
        if (StrUtil.isBlank(candidate.getNum()) || StrUtil.isBlank(candidate.getName())) {
            log.warn("[A2aSync] syncOne 入参不完整 candidate={}", candidate);
            return;
        }

        resyncByNum(candidate.getNum(), candidate.getName(),
                SyncEventType.POLLING_RECONCILE, SYNC_OPERATOR);
    }

    /**
     * 单条同步的核心实现：fetcher 拉远端 → 校验 A2A → applyNacosSync → 写历史。
     * <p>
     * 本方法是 {@link AgentCommandService#manualResync}（用户主动重试）和
     * {@link #syncOne}（定时兜底轮询）共同依赖的"同一份逻辑"，避免两条路径各自实现一套
     * Nacos 拉取 / 状态推进 / 历史写入流程。
     *
     * @param agentNum   Agent 业务编号
     * @param nacosName  Nacos 服务名（= AgentCard.name / a2aSource.nacosService）
     * @param eventType  同步事件来源（MANUAL_RESYNC / POLLING_RECONCILE / INSTANCE_ADDED ...）
     * @param operatorId 操作人（用户 id 或 nacos-sync）
     * @return 写入后的新 A2aSourceInfo（含 lastSyncedAt）；agent 不存在 / 已下线 / 非 A2A 时返回 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public A2aSourceInfo resyncByNum(String agentNum, String nacosName,
                                     SyncEventType eventType, String operatorId) {
        Assert.notBlank(agentNum, "agentNum 不能为空");
        Assert.notBlank(nacosName, "nacosName 不能为空");
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        A2aSourceInfo source;
        try {
            NacosAgentCardFetcher fetcher = fetcherProvider.getIfAvailable();
            if (fetcher == null) {
                log.warn("[A2aSync] resyncByNum NacosAgentCardFetcher 未装配(agentscope.a2a.nacos.discovery.enabled=false),num={} 跳过", agentNum);
                recordSyncHistory(agentNum, null, "NacosAgentCardFetcher 未启用");
                return null;
            }
            source = fetcher.fetch(nacosName);
        } catch (BusinessException e) {
            // 远端不可达 / 关键字段缺失：写一条历史跳过，不阻断（与 syncOne 同容错语义）
            log.warn("[A2aSync] resyncByNum 拉取失败 num={} name={} code={} msg={}",
                    agentNum, nacosName, e.getCode(), e.getMessage());
            recordSyncHistory(agentNum, null, e.getMessage());
            return null;
        }

        Agent agent = agentFactory.createByNum(agentNum);
        if (agent == null) {
            log.warn("[A2aSync] resyncByNum num={} 不存在(可能已被删除)，跳过", agentNum);
            return null;
        }
        if (agent.getCreationMode() != CreationMode.A2A) {
            // 数据竞态 / 异常数据：被并发改为非 A2A；跳过即可
            log.info("[A2aSync] resyncByNum num={} 非 A2A 模式 creationMode={}，跳过",
                    agentNum, agent.getCreationMode());
            return null;
        }

        // 远端可达 = PUBLISHED（M2 阶段 fetcher 无 healthy 字段时统一按在线处理）
        AgentStatus newStatus = AgentStatus.PUBLISHED;
        agent.applyNacosSync(source.getNacosService(), agent.getDescription(),
                source, newStatus, eventType, operatorId);

        recordSyncHistory(agent.getNum(), source, null);
        log.info("[A2aSync] resyncByNum 成功 num={} status={} eventType={} operator={}",
                agent.getNum(), newStatus, eventType, operatorId);
        return source;
    }

    /**
     * 写一条同步历史。失败 / 远端不可达时 source 可能为 null，仅落 agentNum + errorMessage。
     */
    private void recordSyncHistory(String agentNum, A2aSourceInfo source, String errorMessage) {
        A2aSyncHistory history = new A2aSyncHistory();
        history.setAgentNum(agentNum);
        history.setSyncEventType(SyncEventType.POLLING_RECONCILE);
        if (source != null) {
            history.setRemoteVersion(source.getRemoteVersion());
            history.setAgentCardJson(source.getAgentCardJson());
        }
        history.setSyncedAt(java.time.LocalDateTime.now());
        history.setRepository(a2aSyncHistoryRepository);
        // 失败原因存到 triggeredBy 字段之外的"附注"路径已无；本服务简化：失败也归 SYNC_OPERATOR
        history.save(SYNC_OPERATOR);
        if (errorMessage != null) {
            log.debug("[A2aSync] syncOne 失败历史已落 num={} reason={}", agentNum, errorMessage);
        }
    }

    /**
     * 仅返回当前 PENDING_SYNC 候选数（监控 / 调试用，不写库）。
     *
     * @param limit 探测上限
     * @return 候选数
     */
    public int countPendingCandidates(int limit) {
        List<A2aSyncCandidateVO> list = agentQueryService.listPendingSyncCandidates(limit);
        return list == null ? 0 : list.size();
    }

    /** 空集合常量，避免方法签名处反复 Collections.emptyList() */
    private static final List<A2aSyncCandidateVO> EMPTY = Collections.emptyList();
}