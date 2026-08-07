package ink.garry.rd.agent.ws.application.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.agent.A2aCreateParam;
import ink.garry.rd.agent.ws.client.agent.A2aDraftParam;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentRepository;
import ink.garry.rd.agent.ws.domain.agent.repository.A2aSyncHistoryRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.a2a.NacosAgentCardFetcher;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * A2A Agent 用户侧创建 / 草稿 / 取消订阅用例编排（v2.6 §6.2 第 2 节）。
 * <p>
 * 区别于 {@link AgentCommandService#createA2aFromNacos}（由 Nacos 同步任务驱动）：
 * 本服务由用户在管理后台触发，需要做远端 AgentCard 校验、nacosServiceKey 唯一性、
 * 草稿转正等用户语义。
 * <p>
 * <b>入口与端点对应</b>：
 * <ul>
 *   <li>{@link #createA2a} —— {@code POST /api/v1/agents/createA2a}「[校验并接入]」</li>
 *   <li>{@link #saveA2aDraft} —— {@code POST /api/v1/agents/saveA2aDraft}「[保存草稿]」</li>
 *   <li>{@link #unsubscribeA2a} —— {@code POST /api/v1/agents/unsubscribeA2a}「[取消订阅]」</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2aUserCreateService {

    private final AgentFactory agentFactory;
    private final AgentRepository agentRepository;
    private final A2aSyncHistoryRepository a2aSyncHistoryRepository;
    private final NacosAgentCardFetcher fetcher;
    private final RedissonClient redissonClient;

    /**
     * 注入用 @Lazy 打破与 AgentQueryService 的循环依赖（本服务在 createA2a 成功后
     * 调 queryService.detail 拼装返回 DTO；queryService 也可能注入本服务做 cancel 联动）。
     */
    @Autowired
    @Lazy
    private AgentQueryService agentQueryService;

    // ============================================================
    // createA2a —「[校验并接入]」
    // ============================================================

    /**
     * 校验并接入 A2A Agent。
     * <ol>
     *   <li>远端拉取 AgentCard；失败抛 {@code 2011}。</li>
     *   <li>同 nacosServiceKey 已存在非草稿 → 抛 {@code 2012}。</li>
     *   <li>{@code draftAgentNum} 非空：把草稿升级为 PENDING_SYNC；否则新建。</li>
     *   <li>写一条 A2aSyncHistory（eventType=MANUAL_RESYNC）便于详情页历史 Tab 追溯。</li>
     * </ol>
     *
     * @param param      入参；{@code nacosAgentName} 必填
     * @param operatorId 操作人 userId
     * @return Agent 详情 DTO（与 {@code GET /detail} 同形）
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentDetailViewDTO createA2a(A2aCreateParam param, String operatorId) {
        Assert.notNull(param, "请求参数不能为空");
        Assert.notBlank(param.getNacosAgentName(), "nacosAgentName 不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        // 1. 拉取远端 AgentCard（fetcher 缺 bean 或 Nacos 不可达 → 抛 2011）
        A2aSourceInfo source = fetcher.fetch(param.getNacosAgentName());
        String serviceKey = source.resolveServiceKey();
        Assert.notBlank(serviceKey, "Nacos 拉取的 AgentCard 缺 group/service，无法生成幂等键");

        // 2. 同 nacosServiceKey 唯一性预检（仅拒绝非草稿；草稿态视为"可被转正"）
        String existedNum = agentQueryService.findNumByNacosServiceKey(serviceKey);
        if (existedNum != null
                && (StrUtil.isBlank(param.getDraftAgentNum()) || !existedNum.equals(param.getDraftAgentNum()))) {
            Agent existed = agentRepository.findByNum(existedNum);
            if (existed != null && existed.getStatus() != AgentStatus.DRAFT_ONLY) {
                throw new BusinessException(BizCode.A2A_AGENT_ALREADY_SUBSCRIBED.getCode(),
                        "Agent already subscribed: " + existedNum);
            }
        }

        // 3. 分布式锁兜底
        String lockKey = LockKeyConstant.AGENT_CREATE_LOCK_PREFIX + "a2a:" + serviceKey;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(),
                        "A2A 接入操作繁忙，请稍后重试");
            }

            String effectiveName = StrUtil.blankToDefault(param.getDisplayName(), source.getNacosService());
            String effectiveDescription = StrUtil.blankToDefault(param.getDescription(), null);

            String agentNum;
            if (StrUtil.isNotBlank(param.getDraftAgentNum())) {
                // 草稿转正
                agentNum = promoteDraft(param.getDraftAgentNum(), source, effectiveName, effectiveDescription, operatorId);
            } else {
                // 新建
                agentNum = createNewA2aAgent(source, effectiveName, effectiveDescription, operatorId);
            }

            // 4. 写一条同步历史（详情页「A2A 历史」Tab 展示）
            recordSyncHistory(agentNum, source, operatorId);

            log.info("[A2aUserCreate] createA2a ok num={} serviceKey={} operator={}",
                    agentNum, serviceKey, operatorId);
            return agentQueryService.detail(agentNum, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(), "获取 A2A 接入锁被中断");
        } finally {
            if (locked) {
                try {
                    lock.unlock();
                } catch (Exception unlockEx) {
                    log.warn("[A2aUserCreate] 释放 A2A 接入锁失败 key={}", lockKey, unlockEx);
                }
            }
        }
    }

    /**
     * 草稿转正：复用 {@link Agent#applyNacosSync} 覆盖 a2aSource / status / name / description，
     * 不会产生新 num（更新已有行）；status 流转 DRAFT_ONLY → PENDING_SYNC。
     */
    private String promoteDraft(String draftAgentNum, A2aSourceInfo source, String name, String description,
                                String operatorId) {
        Agent draft = agentRepository.findByNum(draftAgentNum);
        if (draft == null) {
            throw new BusinessException(BizCode.A2A_AGENT_DRAFT_NOT_FOUND.getCode(),
                    "A2A 草稿不存在 num=" + draftAgentNum);
        }
        if (draft.getCreationMode() != CreationMode.A2A) {
            throw new BusinessException(BizCode.AGENT_MODE_UNSUPPORTED.getCode(),
                    "草稿不是 A2A 模式 num=" + draftAgentNum);
        }
        if (draft.getStatus() != AgentStatus.DRAFT_ONLY) {
            throw new BusinessException(BizCode.A2A_AGENT_UNMODIFIABLE.getCode(),
                    "草稿已被转正或订阅 num=" + draftAgentNum + " status=" + draft.getStatus());
        }
        // 走 applyNacosSync 而非 save：触发完整的 nacosServiceKey 同步 / 校验 / 事件
        draft.applyNacosSync(name, description, source, AgentStatus.PENDING_SYNC,
                SyncEventType.MANUAL_RESYNC, operatorId);
        return draft.getNum();
    }

    /**
     * 新建 A2A Agent：复用 {@link AgentFactory#createA2aAgent} 工厂装配依赖，
     * 落到 status=PENDING_SYNC（v2.6 状态机：DRAFT_ONLY → PENDING_SYNC → PUBLISHED/OFFLINE）。
     */
    private String createNewA2aAgent(A2aSourceInfo source, String name, String description, String operatorId) {
        Agent created = agentFactory.createA2aAgent(source, name, description, AgentStatus.PENDING_SYNC);
        created.save(operatorId);
        return created.getNum();
    }

    private void recordSyncHistory(String agentNum, A2aSourceInfo source, String operatorId) {
        A2aSyncHistory history = new A2aSyncHistory();
        history.setAgentNum(agentNum);
        history.setRemoteVersion(source.getRemoteVersion());
        history.setSyncEventType(SyncEventType.MANUAL_RESYNC);
        history.setAgentCardJson(source.getAgentCardJson());
        history.setSyncedAt(LocalDateTime.now());
        history.setRepository(a2aSyncHistoryRepository);
        // save(triggeredBy) 内部会再次兜底设置 triggeredBy / syncedAt
        history.save(operatorId);
        log.debug("[A2aUserCreate] 写入同步历史 num={} eventType={}", agentNum, SyncEventType.MANUAL_RESYNC);
    }

    // ============================================================
    // saveA2aDraft —「[保存草稿]」
    // ============================================================

    /**
     * 保存 A2A 草稿。
     * <ul>
     *   <li>{@code agentNum} 非空 → 更新已有草稿 name/description（不动 a2aSource）。</li>
     *   <li>{@code agentNum} 空 → 新建草稿（status=DRAFT_ONLY, a2aSource=null），
     *       由后续「[校验并接入]」流程拉远端补全 a2aSource。</li>
     * </ul>
     * <p>
     * 草稿态放宽 domain 校验：见 {@link Agent#domainValidate()} 注释（v2.6）。
     *
     * @param param      入参
     * @param operatorId 操作人 userId
     * @return Agent num（新建或更新后）
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveA2aDraft(A2aDraftParam param, String operatorId) {
        Assert.notNull(param, "请求参数不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        String name = StrUtil.blankToDefault(param.getDisplayName(), "A2A 草稿");

        if (StrUtil.isNotBlank(param.getAgentNum())) {
            // 更新已有草稿
            Agent draft = agentRepository.findByNum(param.getAgentNum());
            if (draft == null) {
                throw new BusinessException(BizCode.A2A_AGENT_DRAFT_NOT_FOUND.getCode(),
                        "A2A 草稿不存在 num=" + param.getAgentNum());
            }
            if (draft.getCreationMode() != CreationMode.A2A) {
                throw new BusinessException(BizCode.AGENT_MODE_UNSUPPORTED.getCode(),
                        "目标 Agent 不是 A2A 模式 num=" + param.getAgentNum());
            }
            if (draft.getStatus() != AgentStatus.DRAFT_ONLY) {
                throw new BusinessException(BizCode.A2A_AGENT_UNMODIFIABLE.getCode(),
                        "草稿已被转正，不可更新 num=" + param.getAgentNum() + " status=" + draft.getStatus());
            }
            // 覆盖 name/description；不动 a2aSource（草稿态为空，校验已允许）
            draft.setName(name);
            if (param.getDescription() != null) {
                draft.setDescription(param.getDescription());
            }
            draft.save(operatorId);
            log.info("[A2aUserCreate] saveA2aDraft update num={} operator={}", draft.getNum(), operatorId);
            return draft.getNum();
        }

        // 新建草稿：a2aSource=null（Domain.validate 已在 v2.6 放宽 DRAFT_ONLY 校验）
        Agent created = agentFactory.createA2aAgent(null, name, param.getDescription(), AgentStatus.DRAFT_ONLY);
        created.save(operatorId);
        log.info("[A2aUserCreate] saveA2aDraft new num={} operator={}", created.getNum(), operatorId);
        return created.getNum();
    }

    // ============================================================
    // unsubscribeA2a —「[取消订阅]」
    // ============================================================

    /**
     * 取消订阅 A2A Agent。允许 DRAFT_ONLY / PENDING_SYNC / PUBLISHED 三态；
     * OFFLINE 视为"已下线 ≠ 取消订阅"，拒绝。
     * <p>
     * 复用 {@link Agent#delete}（v2.6 已放开 A2A 走此路径），
     * 不写同步历史（取消订阅是一次性动作，不是 Nacos 同步事件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unsubscribeA2a(String agentNum, String operatorId) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        Agent agent = agentFactory.createByNum(agentNum);
        if (agent == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(),
                    "Agent 不存在 num=" + agentNum);
        }
        if (agent.getCreationMode() != CreationMode.A2A) {
            throw new BusinessException(BizCode.AGENT_MODE_UNSUPPORTED.getCode(),
                    "非 A2A Agent 不允许走取消订阅 num=" + agentNum);
        }
        if (agent.getStatus() == AgentStatus.OFFLINE) {
            throw new BusinessException(BizCode.AGENT_MODE_UNSUPPORTED.getCode(),
                    "已下线的 A2A Agent 不需要取消订阅，请先恢复 num=" + agentNum);
        }

        agent.delete(operatorId);
        log.info("[A2aUserCreate] unsubscribeA2a ok num={} operator={}", agentNum, operatorId);
    }
}
