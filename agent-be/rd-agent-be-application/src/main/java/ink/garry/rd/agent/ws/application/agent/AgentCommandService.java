package ink.garry.rd.agent.ws.application.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.agent.A2aResyncVO;
import ink.garry.rd.agent.ws.client.agent.AgentCreateParam;
import ink.garry.rd.agent.ws.client.agent.PublishParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.skill.dto.SkillDetailDTO;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentVersionFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentVersionGateway;
import ink.garry.rd.agent.ws.domain.agent.gateway.DraftLockGateway;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentType;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.MemoryConfig;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SkillRef;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ToolRef;
import ink.garry.rd.agent.ws.domain.agent.valueobject.Version;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.application.skill.SkillQueryService;
import ink.garry.rd.agent.ws.application.tool.ToolQueryService;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Agent 写侧应用服务（v3.2：对齐 {@code SkillCommandService} 范式重构）。
 * <p>
 * 负责 Agent / AgentVersion（v3.0 含 DRAFT）/ A2A 同步的所有写入用例编排。
 * <ul>
 *   <li>{@link #create} — 创建 CONFIG Agent + 首发 v1.0.0</li>
 *   <li>{@link #createVersion} — 创建 DRAFT 版本（拷贝当前在线 snapshot）</li>
 *   <li>{@link #editDraftVersion} — 覆盖更新 DRAFT 行 snapshot</li>
 *   <li>{@link #deleteDraftVersion} — 物理删除 DRAFT 行</li>
 *   <li>{@link #publish} — DRAFT → PUBLISHED 升级 + 旧 PUBLISHED → ARCHIVED + 同步 agent 主表镜像</li>
 *   <li>{@link #offline} — 下线；A2A 同步用例见 createA2aFromNacos / syncByNum / manualResync</li>
 * </ul>
 *
 * <h3>v3.2 重构要点（对齐 SkillCommandService）</h3>
 * <ol>
 *   <li><b>分布式锁</b>：每个用户触发的写方法用 {@link #runWithLock} 包裹（创建按
 *       {@code AGENT_CREATE_LOCK_PREFIX + workspace:name}，其余按 {@code AGENT_COMMAND_LOCK_PREFIX + agentNum}），
 *       防止用户连点 / 重试触发状态机错乱或唯一冲突。A2A 同步任务（createA2aFromNacos / syncByNum）
 *       由 Nacos 推送驱动、按 serviceKey 幂等，<b>不加用户级用例锁</b>，避免与同步任务串行化冲突。</li>
 *   <li><b>CQRS</b>：写侧不直接持有读网关 / Mapper；所有非命令式读经 {@link AgentQueryService}
 *       取“编号 / 布尔”，再用 {@link AgentVersionFactory#createByNum} 重建领域对象去 save。
 *       例外：{@code agentVersionGateway.switchCurrent} 是发布事务内的写操作，仍由本服务直接调用。</li>
 *   <li><b>入参校验</b>：每个写方法开头 {@code Assert} 显式校验。</li>
 *   <li><b>事务</b>：写方法均标 {@code @Transactional(rollbackFor = Exception.class)}。</li>
 * </ol>
 * <p>
 * <b>装配</b>：FactoryImpl 承担 DomainEventPublisher 注入，本服务不持有 Publisher、不写 wire helper。
 */
@Slf4j
@Service
public class AgentCommandService {

    /** 草稿编辑锁默认 TTL（30 分钟），与领域规则一致 */
    private static final long DRAFT_LOCK_TTL_SECONDS = 1800L;

    /** A2A 同步任务统一的操作人标识（v2.4：原 A2aSyncService 常量迁移于此） */
    public static final String SYNC_OPERATOR = "nacos-sync";

    /** 无 workspace 上下文时回退的默认空间（与 AgentFactoryImpl 一致） */
    private static final String DEFAULT_WORKSPACE_NUM = "WS-DEFAULT";

    /** 用例锁等待时长（秒）：抢不到锁最多再等 3s（与 SkillCommandService 统一） */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /** 用例锁租约时长（秒）：30s 覆盖多步 DB 操作 + 事件发布，超时由 Redisson 自动释放避免死锁 */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private AgentFactory agentFactory;
    @Resource
    private AgentVersionFactory agentVersionFactory;
    @Resource
    private AgentQueryService agentQueryService;
    @Resource
    private AgentVersionGateway agentVersionGateway;
    @Resource
    private DraftLockGateway draftLockGateway;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ToolQueryService toolQueryService;
    @Resource
    private SkillQueryService skillQueryService;
    @Resource
    private ink.garry.rd.agent.ws.application.model.ModelQueryService modelQueryService;
    @Resource
    private ink.garry.rd.agent.ws.application.agent.A2aSyncApplicationService a2aSyncApplicationService;

    // ============================================================
    // create
    // ============================================================

    /**
     * 创建配置模式 Agent（首次发布 v1.0.0）。
     * <p>
     * 锁粒度：(workspaceNum, name) —— create 阶段 Agent num 尚未生成，按业务唯一组合互斥，
     * 防止用户连点 / 重试创建出多条同空间同名 Agent（V33 后已下线 uq_agent_ws_name DDL 约束，
     * 本锁 + 下方 existsByWorkspaceAndName 是同空间唯一的唯一兜底）。
     *
     * @param param      创建参数；name / agentType 必填
     * @param operatorId 操作人 userId
     * @return 新建 Agent 业务编号
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(AgentCreateParam param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(param.getName(), "Agent 名称不能为空");
        Assert.notBlank(param.getAgentType(), "Agent 类型不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        String workspaceNum = resolveWorkspaceNum();
        String lockKey = LockKeyConstant.AGENT_CREATE_LOCK_PREFIX + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            // 1. 唯一性预检（同空间 name 唯一；经 QueryService，CQRS 约束）
            if (agentQueryService.existsByWorkspaceAndName(workspaceNum, param.getName())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "名称在当前工作空间内已存在：" + param.getName());
            }

            // 2. 构建并保存 Agent 主表
            AgentType type = AgentType.valueOf(param.getAgentType());
            Agent agent = agentFactory.createConfigAgent(
                    param.getName(), param.getDescription(), type, operatorId, param.getTags());
            agent.save(operatorId);

            // 3. 首版 v1.0.0：构造 PUBLISHED 版本 + 翻转 agent 主表镜像
            ConfigSnapshot snapshot = buildSnapshotFromParam(param, type);
            Version initial = Version.initial();
            AgentVersion firstVersion = agentVersionFactory.create(
                    agent.getNum(), initial, snapshot, "首次发布 v1.0.0（自动）", operatorId);
            firstVersion.setStatus(AgentVersionStatus.PUBLISHED);
            firstVersion.setCurrent(true);
            firstVersion.save(operatorId);

            agent.promotePublished(initial.toStr(), snapshot, operatorId);

            log.info("[AgentCommandService] create ok num={} operator={}", agent.getNum(), operatorId);
            return agent.getNum();
        });
    }

    // ============================================================
    // createVersion
    // ============================================================

    /**
     * v3.0：创建 DRAFT 版本（基于当前在线版本 snapshot 拷贝）。
     *
     * @param agentNum   Agent 业务编号
     * @param operatorId 操作人 userId
     * @return 新建草稿版本业务编号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createVersion(String agentNum, String operatorId) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        return runWithLock(LockKeyConstant.AGENT_COMMAND_LOCK_PREFIX + agentNum, () -> {
            Agent agent = loadAgent(agentNum);
            if (agent.getCreationMode() != CreationMode.CONFIG) {
                throw new BusinessException(BizCode.AGENT_MODE_UNSUPPORTED.getCode(),
                        "仅 CONFIG Agent 支持版本化 num=" + agentNum);
            }
            // 1. 草稿唯一性预检（经 QueryService，CQRS 约束）
            if (agentQueryService.hasDraftVersion(agentNum)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "已存在草稿版本，请先编辑或丢弃 agentNum=" + agentNum);
            }

            // 2. 以当前在线版本 snapshot 为基底（无在线版本则按 agentType 空快照起步）
            String currentVersionNum = agentQueryService.findCurrentVersionNum(agentNum);
            ConfigSnapshot baseSnapshot;
            if (currentVersionNum == null) {
                baseSnapshot = ConfigSnapshot.builder().agentType(agent.getAgentType()).build();
            } else {
                AgentVersion current = agentVersionFactory.createByNum(currentVersionNum);
                baseSnapshot = current.getConfigSnapshot();
            }

            // 3. 构造 DRAFT 行 + 落库
            AgentVersion draft = agentVersionFactory.create(agentNum, null, baseSnapshot, null, null);
            draft.setStatus(AgentVersionStatus.DRAFT);
            draft.setEditorUserId(operatorId);
            draft.setLockUntil(LocalDateTime.now().plusSeconds(DRAFT_LOCK_TTL_SECONDS));
            draft.save(operatorId);

            // 4. 抢草稿编辑锁（领域特有热路径，与用例锁互为兜底）
            draftLockGateway.tryLock(agentNum, operatorId, DRAFT_LOCK_TTL_SECONDS);
            log.info("[AgentCommandService] createVersion ok agentNum={} versionId={} baseVersion={}",
                    agentNum, draft.getNum(), currentVersionNum == null ? "<initial>" : currentVersionNum);
            return draft.getNum();
        });
    }

    // ============================================================
    // editDraftVersion
    // ============================================================

    /**
     * v3.0：覆盖更新 DRAFT 行的 configSnapshot。
     *
     * @param versionId   草稿版本业务编号
     * @param configDraft 新配置快照（Map → ConfigSnapshot）
     * @param operatorId  操作人 userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void editDraftVersion(String versionId, Map<String, Object> configDraft, String operatorId) {
        Assert.notBlank(versionId, "版本业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        AgentVersion draft = loadDraftVersion(versionId);
        runWithLock(LockKeyConstant.AGENT_COMMAND_LOCK_PREFIX + draft.getAgentNum(), () -> {
            // 草稿编辑锁：非持有者且抢锁失败则拒绝
            boolean lockOk = draftLockGateway.tryLock(draft.getAgentNum(), operatorId, DRAFT_LOCK_TTL_SECONDS);
            if (!lockOk && !operatorId.equals(draftLockGateway.currentHolder(draft.getAgentNum()))) {
                throw new BusinessException(BizCode.DRAFT_LOCKED.getCode(),
                        "草稿被锁定: holder=" + draftLockGateway.currentHolder(draft.getAgentNum()));
            }
            draft.setConfigSnapshot(mapToSnapshot(configDraft));
            draft.setEditorUserId(operatorId);
            draft.setLockUntil(LocalDateTime.now().plusSeconds(DRAFT_LOCK_TTL_SECONDS));
            draft.save(operatorId);
            log.info("[AgentCommandService] editDraftVersion ok versionId={} operator={}", versionId, operatorId);
        });
    }

    // ============================================================
    // deleteDraftVersion
    // ============================================================

    /**
     * v3.0：物理删除 DRAFT 行。
     *
     * @param versionId  草稿版本业务编号
     * @param operatorId 操作人 userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraftVersion(String versionId, String operatorId) {
        Assert.notBlank(versionId, "版本业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        AgentVersion draft = agentVersionFactory.createByNum(versionId);
        if (draft == null) {
            return;
        }
        runWithLock(LockKeyConstant.AGENT_COMMAND_LOCK_PREFIX + draft.getAgentNum(), () -> {
            Assert.isTrue(draft.getStatus() == AgentVersionStatus.DRAFT,
                    "目标版本不是 DRAFT 状态，不能删除 versionId={} status={}", versionId, draft.getStatus());
            draft.delete(operatorId);
            draftLockGateway.unlock(draft.getAgentNum(), operatorId);
            log.info("[AgentCommandService] deleteDraftVersion ok versionId={} operator={}", versionId, operatorId);
        });
    }

    // ============================================================
    // publish
    // ============================================================

    /**
     * v3.0：发布版本（事务原子完成：DRAFT → PUBLISHED + 旧在线 → ARCHIVED + agent 主表镜像）。
     * <p>
     * v3.1：去 ChangeLevel，版本号固定 patch+1（首发取 initial）。
     *
     * @param param      发布参数；versionId / agentNum 二选一
     * @param operatorId 操作人 userId
     * @return 发布后的新版本号字符串
     */
    @Transactional(rollbackFor = Exception.class)
    public String publish(PublishParam param, String operatorId) {
        Assert.notNull(param, "发布参数不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        // 先定位待发布草稿的 agentNum，再按 agentNum 抢用例锁
        String draftVersionId = resolveDraftVersionId(param);
        AgentVersion draft = loadDraftVersion(draftVersionId);
        String agentNum = draft.getAgentNum();

        return runWithLock(LockKeyConstant.AGENT_COMMAND_LOCK_PREFIX + agentNum, () -> {
            Agent agent = loadAgent(agentNum);
            agent.assertMutableByPlatform();

            // 1. 计算新版本号（经 QueryService 取当前在线版本号；CQRS 约束）
            String currentVersionNum = agentQueryService.findCurrentVersionNum(agentNum);
            Version next;
            Long oldId;
            if (currentVersionNum == null) {
                next = Version.initial();
                oldId = null;
            } else {
                AgentVersion currentVersion = agentVersionFactory.createByNum(currentVersionNum);
                next = currentVersion.getVersion().next();
                oldId = agentQueryService.findCurrentVersionId(agentNum);
            }
            ConfigSnapshot snapshot = normalizeSnapshot(draft.getConfigSnapshot(), resolveWorkspaceNum());
            draft.setConfigSnapshot(snapshot);

            // 2. 翻转 current 标记（发布事务内的写操作，直接调网关）
            agentVersionGateway.switchCurrent(oldId, draft.getId());

            // 3. 草稿行升级为 PUBLISHED
            draft.setVersion(next);
            draft.setVersionNum(next.toStr());
            draft.setRemark(param.getRemark());
            draft.setPublishedBy(operatorId);
            draft.setPublishedAt(LocalDateTime.now());
            draft.setEditorUserId(null);
            draft.setLockUntil(null);
            draft.setStatus(AgentVersionStatus.PUBLISHED);
            draft.setCurrent(true);
            draft.save(operatorId);

            // 4. agent 主表镜像同步（name / description / snapshot）
            if (StrUtil.isNotBlank(snapshot.getName())) {
                agent.setName(snapshot.getName());
            }
            if (snapshot.getDescription() != null) {
                agent.setDescription(snapshot.getDescription());
            }
            agent.promotePublished(next.toStr(), snapshot, operatorId);

            // 5. 释放草稿编辑锁
            draftLockGateway.unlock(agentNum, operatorId);
            log.info("[AgentCommandService] publish ok agentNum={} version={} operator={}",
                    agentNum, next.toStr(), operatorId);
            return next.toStr();
        });
    }

    // ============================================================
    // offline
    // ============================================================

    /**
     * 下线 Agent（仅 CONFIG；A2A 由 Nacos 同步驱动）。
     *
     * @param agentNum   Agent 业务编号
     * @param operatorId 操作人 userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void offline(String agentNum, String operatorId) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");
        runWithLock(LockKeyConstant.AGENT_COMMAND_LOCK_PREFIX + agentNum, () -> {
            Agent agent = loadAgent(agentNum);
            agent.offline(operatorId);
            log.info("[AgentCommandService] offline ok agentNum={} operator={}", agentNum, operatorId);
        });
    }

    // ============================================================
    // A2A Nacos 同步用例（同步任务驱动 / 按 serviceKey 幂等，不加用户级用例锁）
    // ============================================================

    /**
     * v2.6 / 架构重构：首次发现新的 A2A Agent。
     * <p>由 Nacos 同步任务调用，非用户触发；不规范入参直接忽略（返回 null）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createA2aFromNacos(String name, String description, A2aSourceInfo source, AgentStatus status) {
        if (source == null || StrUtil.isBlank(source.resolveServiceKey())) {
            log.warn("[A2aSync] createA2aFromNacos 忽略不规范的 source={}", source);
            return null;
        }
        if (StrUtil.isBlank(name) || status == null) {
            log.warn("[A2aSync] createA2aFromNacos 入参不完整 name={} status={}", name, status);
            return null;
        }
        Agent created = agentFactory.createA2aAgent(source, name, description, status);
        created.save(SYNC_OPERATOR);
        log.info("[A2aSync] 新建 A2A Agent num={} serviceKey={}",
                created.getNum(), source.resolveServiceKey());
        return created.getNum();
    }

    /**
     * v2.6 / 架构重构：按业务编号同步已存在的 A2A Agent。
     * <p>由 Nacos 同步任务调用，非用户触发；不完整 / 非 A2A 数据直接跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncByNum(String agentNum, String name, String description, A2aSourceInfo source,
                          SyncEventType eventType, AgentStatus status) {
        if (StrUtil.isBlank(agentNum) || source == null || eventType == null || status == null) {
            log.warn("[A2aSync] syncByNum 入参不完整 agentNum={} source={} eventType={} status={}",
                    agentNum, source, eventType, status);
            return;
        }
        Agent agent = agentFactory.createByNum(agentNum);
        if (agent == null) {
            log.warn("[A2aSync] syncByNum agentNum={} 不存在(可能已被删除)，跳过", agentNum);
            return;
        }
        if (agent.getCreationMode() != CreationMode.A2A) {
            log.warn("[A2aSync] syncByNum agentNum={} creationMode={} 非 A2A，跳过(异常数据)",
                    agentNum, agent.getCreationMode());
            return;
        }
        String effectiveName = StrUtil.isNotBlank(name) ? name : agent.getName();
        String effectiveDescription = description != null ? description : agent.getDescription();
        agent.applyNacosSync(effectiveName, effectiveDescription, source, status,
                eventType, SYNC_OPERATOR);
        log.info("[A2aSync] syncByNum 成功 agentNum={} status={} eventType={}",
                agentNum, status, eventType);
    }

    /**
     * 详情页「[手动重新同步]」按钮触发（用户操作，加用例锁）。
     * <p>
     * v2.6 重构：与 {@link A2aSyncApplicationService#syncOne} 对齐为同一份逻辑
     * （fetcher.fetch(name) → applyNacosSync(newSource, PUBLISHED, MANUAL_RESYNC, operatorId)），
     * 区别仅三点：
     * <ol>
     *   <li>入口粒度：单条按 num（用户操作），syncOne 是按 candidate（轮询）</li>
     *   <li>事件类型：MANUAL_RESYNC（用户意图）vs POLLING_RECONCILE（兜底）</li>
     *   <li>操作人：当前用户 id（用户操作）vs nacos-sync（系统兜底）</li>
     * </ol>
     * 改"两入口同逻辑"是为了避免后续 Nacos 拉取策略调整时只改一处而漏改另一处。
     *
     * @param agentNum   Agent 业务编号
     * @param operatorId 操作人 userId
     * @return 同步结果（含最新 lastSyncedAt）
     */
    @Transactional(rollbackFor = Exception.class)
    public A2aResyncVO manualResync(String agentNum, String operatorId) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");
        return runWithLock(LockKeyConstant.AGENT_COMMAND_LOCK_PREFIX + agentNum, () -> {
            Agent agent = loadAgent(agentNum);
            if (agent.getCreationMode() != CreationMode.A2A) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(),
                        "仅 A2A Agent 支持手动重新同步，当前 creationMode=" + agent.getCreationMode());
            }
            String nacosName = agent.getA2aSource() == null ? null : agent.getA2aSource().getNacosService();
            Assert.notBlank(nacosName,
                    "A2A Agent 缺 Nacos service 名称，无法拉取远端 num=" + agentNum);

            A2aSourceInfo newSource = a2aSyncApplicationService.resyncByNum(
                    agentNum, nacosName, SyncEventType.MANUAL_RESYNC, operatorId);

            A2aResyncVO result = new A2aResyncVO();
            result.setNum(agent.getNum());
            result.setLastSyncedAt(newSource == null ? null : newSource.getLastSyncedAt());
            log.info("[AgentCommandService] manualResync ok agentNum={} operator={}",
                    agentNum, operatorId);
            return result;
        });
    }

    /** 兜底全量对账（M3 真实实现） */
    public void pollAll() {
        log.debug("[A2aSync] pollAll skeleton — M3 wires Nacos enumeration here");
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * 按 num 加载 Agent；不存在抛 {@link BizCode#AGENT_NOT_FOUND}。
     *
     * @param agentNum Agent 业务编号
     * @return 已装配依赖的 Agent 领域对象
     */
    private Agent loadAgent(String agentNum) {
        Agent agent = agentFactory.createByNum(agentNum);
        if (agent == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在 num=" + agentNum);
        }
        return agent;
    }

    /**
     * 按 num 加载 DRAFT 版本；不存在抛 {@link BizCode#NOT_FOUND}，非 DRAFT 抛 {@link BizCode#INVALID_PARAM}。
     *
     * @param versionId 版本业务编号
     * @return 已装配依赖且状态为 DRAFT 的 AgentVersion
     */
    private AgentVersion loadDraftVersion(String versionId) {
        AgentVersion draft = agentVersionFactory.createByNum(versionId);
        if (draft == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "版本不存在 versionId=" + versionId);
        }
        if (draft.getStatus() != AgentVersionStatus.DRAFT) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "目标版本不是 DRAFT 状态 versionId=" + versionId + " status=" + draft.getStatus());
        }
        return draft;
    }

    /**
     * 解析待发布草稿版本的业务编号：优先 versionId；否则按 agentNum 经 QueryService 找 DRAFT 行（CQRS 约束）。
     *
     * @param param 发布参数
     * @return 草稿版本业务编号
     */
    private String resolveDraftVersionId(PublishParam param) {
        if (StrUtil.isNotBlank(param.getVersionId())) {
            return param.getVersionId();
        }
        if (StrUtil.isBlank(param.getAgentNum())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "publish 入参缺失 versionId / agentNum");
        }
        String draftNum = agentQueryService.findDraftVersionNum(param.getAgentNum());
        if (draftNum == null) {
            throw new BusinessException(BizCode.DRAFT_NOT_FOUND.getCode(),
                    "无可发布草稿 agentNum=" + param.getAgentNum());
        }
        return draftNum;
    }

    /**
     * 解析当前请求的工作空间编号；无 workspace 上下文时回退默认空间（与 AgentFactoryImpl 一致）。
     *
     * @return 工作空间业务编号
     */
    private String resolveWorkspaceNum() {
        String ws = WorkspaceContextHolder.currentWorkspaceNum();
        return (ws == null || ws.isBlank()) ? DEFAULT_WORKSPACE_NUM : ws;
    }

    private ConfigSnapshot buildSnapshotFromParam(AgentCreateParam p, AgentType type) {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .name(p.getName())
                .description(p.getDescription())
                .agentType(type)
                .systemPrompt(p.getSystemPrompt())
                .userPrompt(p.getUserPrompt())
                .modelId(p.getModelId())
                .temperature(p.getTemperature())
                .enablePlan(p.getEnablePlan())
                .maxIters(p.getMaxIters())
                .skillNums(p.getSkillNums())
                .toolNums(p.getToolNums())
                .skillRefs(toSkillRefs(p.getSkillRefs()))
                .toolRefs(toToolRefs(p.getToolRefs()))
                .sandboxRef(p.getSandboxRef())
                .childAgentNums(p.getChildAgentNums())
                .memoryConfig(memoryFromMap(p.getMemoryConfig()))
                .qps(p.getQps())
                .dailyBudget(p.getDailyBudget())
                .build();
        return normalizeSnapshot(snapshot, resolveWorkspaceNum());
    }

    private ConfigSnapshot mapToSnapshot(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        ConfigSnapshot snapshot = JSON.parseObject(JSON.toJSONString(raw), ConfigSnapshot.class);
        return normalizeSnapshot(snapshot, resolveWorkspaceNum());
    }

    /**
     * 归一化 ConfigSnapshot：补默认值、校验模型归属、补齐 Skill/Tool 版本引用。
     */
    private ConfigSnapshot normalizeSnapshot(ConfigSnapshot snapshot, String workspaceNum) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.getEnablePlan() == null) {
            snapshot.setEnablePlan(false);
        }
        if (snapshot.getMaxIters() == null) {
            snapshot.setMaxIters(10);
        }
        validateModelEnabled(snapshot.getModelId(), workspaceNum);
        snapshot.setSkillRefs(resolveSkillRefs(snapshot.getSkillNums(), snapshot.getSkillRefs(), workspaceNum));
        snapshot.setToolRefs(resolveToolRefs(snapshot.getToolNums(), snapshot.getToolRefs()));
        if ((snapshot.getSkillNums() == null || snapshot.getSkillNums().isEmpty()) && snapshot.getSkillRefs() != null) {
            snapshot.setSkillNums(snapshot.getSkillRefs().stream().map(SkillRef::getSkillNum).toList());
        }
        if ((snapshot.getToolNums() == null || snapshot.getToolNums().isEmpty()) && snapshot.getToolRefs() != null) {
            snapshot.setToolNums(snapshot.getToolRefs().stream().map(ToolRef::getToolNum).toList());
        }
        validateMountedToolsPublished(snapshot.getToolNums());
        return snapshot;
    }

    /**
     * 校验 Agent 挂载的工具（toolNums）均为已发布（PUBLISHED）工具。
     * <p>
     * v4.0：原字段 mcpNums 重命名为 toolNums（含 MCP / FunctionCall）。保存配置时拦截挂载了
     * 草稿 / 已废弃 / 不存在的工具；逐一经 {@link ToolQueryService#findByNum} 校验状态
     * （工作空间无关查询：Agent 编辑流内无工具工作空间上下文）。
     *
     * @param toolNums 挂载的工具业务编号列表（可空 / 空表示未挂载工具）
     * @throws BusinessException 存在不可挂载（非 PUBLISHED / 不存在）的工具时
     */
    private void validateMountedToolsPublished(java.util.List<String> toolNums) {
        if (toolNums == null || toolNums.isEmpty()) {
            return;
        }
        for (String toolNum : toolNums) {
            if (StrUtil.isBlank(toolNum)) {
                continue;
            }
            // findByNum 不存在时抛 TOOL_NOT_FOUND；存在则校验状态必须 PUBLISHED
            // （用工作空间无关的 findByNum：本校验在 Agent 编辑流内无工具工作空间上下文，
            //  detail(num, ws) 强制要求 ws 会误报「未指定工作空间」）
            String status = toolQueryService.findByNum(toolNum).getStatus();
            if (!ToolStatus.PUBLISHED.name().equals(status)) {
                throw new BusinessException(BizCode.TOOL_STATUS_INVALID.getCode(),
                        "挂载的工具不可用（非已发布状态）num=" + toolNum + " status=" + status);
            }
        }
    }

    /**
     * 校验 Agent 关联的模型（modelId = 模型管理 num）为已启用（ENABLED）模型（v4.0 新增）。
     * <p>
     * 模型从模型管理资产化引用后，保存配置时须拦截关联了草稿 / 禁用 / 不存在的模型；
     * 经 {@link ink.garry.rd.agent.ws.application.model.ModelQueryService#getDetail} 取状态校验。
     *
     * @param modelId 关联模型业务编号（可空表示草稿尚未选模型，留到发布时拦截）
     * @throws BusinessException 模型不存在或非 ENABLED 时
     */
    private void validateModelEnabled(String modelId, String workspaceNum) {
        if (StrUtil.isBlank(modelId)) {
            return;
        }
        modelQueryService.requireSelectableEnabled(modelId, workspaceNum);
    }

    private List<SkillRef> toSkillRefs(List<ink.garry.rd.agent.ws.client.agent.SkillRefParam> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        List<SkillRef> result = new ArrayList<>();
        for (var ref : refs) {
            if (ref == null || StrUtil.isBlank(ref.getSkillNum())) {
                continue;
            }
            result.add(SkillRef.builder()
                    .skillNum(ref.getSkillNum())
                    .versionNum(ref.getVersionNum())
                    .build());
        }
        return result;
    }

    private List<ToolRef> toToolRefs(List<ink.garry.rd.agent.ws.client.agent.ToolRefParam> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        List<ToolRef> result = new ArrayList<>();
        for (var ref : refs) {
            if (ref == null || StrUtil.isBlank(ref.getToolNum())) {
                continue;
            }
            result.add(ToolRef.builder()
                    .toolNum(ref.getToolNum())
                    .versionNum(ref.getVersionNum())
                    .build());
        }
        return result;
    }

    private List<SkillRef> resolveSkillRefs(List<String> skillNums, List<SkillRef> refs, String workspaceNum) {
        if (refs != null && !refs.isEmpty()) {
            for (SkillRef ref : refs) {
                if (ref == null || StrUtil.isBlank(ref.getSkillNum()) || StrUtil.isBlank(ref.getVersionNum())) {
                    throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "Skill 引用必须包含 skillNum 和 versionNum");
                }
                skillQueryService.versionDetail(ref.getSkillNum(), ref.getVersionNum());
            }
            return dedupSkillRefs(refs);
        }
        if (skillNums == null || skillNums.isEmpty()) {
            return refs;
        }
        List<SkillRef> result = new ArrayList<>();
        for (String skillNum : skillNums) {
            if (StrUtil.isBlank(skillNum)) {
                continue;
            }
            SkillDetailDTO detail = skillQueryService.detail(skillNum, workspaceNum);
            if (detail.getSkill() == null || !SkillStatus.PUBLISHED.name().equals(detail.getSkill().getStatus())) {
                throw new BusinessException(BizCode.SKILL_NOT_FOUND.getCode(), "挂载的 Skill 不可用 num=" + skillNum);
            }
            if (StrUtil.isBlank(detail.getSkill().getCurrentVersionNum())) {
                throw new BusinessException(BizCode.SKILL_NOT_FOUND.getCode(), "挂载的 Skill 缺少当前发布版本 num=" + skillNum);
            }
            result.add(SkillRef.builder()
                    .skillNum(skillNum)
                    .versionNum(detail.getSkill().getCurrentVersionNum())
                    .build());
        }
        return dedupSkillRefs(result);
    }

    private List<ToolRef> resolveToolRefs(List<String> toolNums, List<ToolRef> refs) {
        if (refs != null && !refs.isEmpty()) {
            for (ToolRef ref : refs) {
                if (ref == null || StrUtil.isBlank(ref.getToolNum())) {
                    throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "Tool 引用必须包含 toolNum");
                }
            }
            return dedupToolRefs(refs);
        }
        if (toolNums == null || toolNums.isEmpty()) {
            return refs;
        }
        List<ToolRef> result = new ArrayList<>();
        for (String toolNum : toolNums) {
            if (StrUtil.isBlank(toolNum)) {
                continue;
            }
            result.add(ToolRef.builder().toolNum(toolNum).build());
        }
        return dedupToolRefs(result);
    }

    /**
     * Skill 引用去重：按 {@code skillNum + versionNum} 维度保留首次出现项（方案 §6.3.1 要求 refs 不重复）。
     *
     * @param refs 待去重的 Skill 引用列表（可空）
     * @return 去重后的列表；入参为空时原样返回
     */
    private List<SkillRef> dedupSkillRefs(List<SkillRef> refs) {
        if (refs == null || refs.size() <= 1) {
            return refs;
        }
        List<SkillRef> deduped = new ArrayList<>(refs.size());
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (SkillRef ref : refs) {
            if (ref == null || StrUtil.isBlank(ref.getSkillNum())) {
                continue;
            }
            String key = ref.getSkillNum() + ':' + (ref.getVersionNum() == null ? "" : ref.getVersionNum());
            if (seen.add(key)) {
                deduped.add(ref);
            }
        }
        return deduped;
    }

    /**
     * Tool 引用去重：按 {@code toolNum + versionNum} 维度保留首次出现项（方案 §6.3.1 要求 refs 不重复）。
     *
     * @param refs 待去重的 Tool 引用列表（可空）
     * @return 去重后的列表；入参为空时原样返回
     */
    private List<ToolRef> dedupToolRefs(List<ToolRef> refs) {
        if (refs == null || refs.size() <= 1) {
            return refs;
        }
        List<ToolRef> deduped = new ArrayList<>(refs.size());
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (ToolRef ref : refs) {
            if (ref == null || StrUtil.isBlank(ref.getToolNum())) {
                continue;
            }
            String key = ref.getToolNum() + ':' + (ref.getVersionNum() == null ? "" : ref.getVersionNum());
            if (seen.add(key)) {
                deduped.add(ref);
            }
        }
        return deduped;
    }

    private MemoryConfig memoryFromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(m), MemoryConfig.class);
    }

    // ============================================================
    // 分布式锁 helper（统一收口业务用例的并发互斥；与 SkillCommandService 同范式）
    // ============================================================

    /**
     * 以给定 key 抢分布式锁后执行带返回值的用例。
     * <ul>
     *   <li>waitTime={@value #COMMAND_LOCK_WAIT_SECONDS}s：抢不到锁最多再等 3s 再放弃；</li>
     *   <li>leaseTime={@value #COMMAND_LOCK_LEASE_SECONDS}s：超时由 Redisson 自动释放避免死锁；</li>
     *   <li>finally 释放前 {@link RLock#isHeldByCurrentThread} 校验，规避 lease 超时后误 unlock 他线程的锁。</li>
     * </ul>
     *
     * @param key    完整锁 key（已拼好前缀 + 业务 ID）
     * @param action 临界区操作
     * @param <T>    返回值类型
     * @return action 的返回值
     * @throws BusinessException 抢锁失败或线程中断（{@link BizCode#CONFLICT}）
     */
    private <T> T runWithLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "Agent 用例编排被中断");
        }
        if (!acquired) {
            log.warn("agent command lock busy key={}", key);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "Agent 正在处理中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * {@link #runWithLock(String, Supplier)} 的无返回值重载，便于 void 写方法。
     *
     * @param key    完整锁 key
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithLock(String key, Runnable action) {
        runWithLock(key, () -> {
            action.run();
            return null;
        });
    }
}
