package ink.garry.rd.agent.ws.application.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.agent.A2aSyncCandidateVO;
import ink.garry.rd.agent.ws.client.agent.A2aSyncHistoryVO;
import ink.garry.rd.agent.ws.client.agent.AgentDebugVersionVO;
import ink.garry.rd.agent.ws.client.agent.AgentPageQuery;
import ink.garry.rd.agent.ws.client.agent.AgentSkillBindingStatusVO;
import ink.garry.rd.agent.ws.client.agent.dto.A2aSourceViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentA2aSyncHistoryDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentListItemDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentVersionDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentVersionDetailViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentVersionViewDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.facade.agent.AgentInvokeDTO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.entity.A2aSyncHistoryEntity;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentVersionEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.A2aSyncHistoryMapper;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentVersionMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillVersionEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 查询服务（只读，§5.1/§5.3 新规：直接走 infra Mapper，不再经 Repository/Gateway/Factory 跳转）。
 * <p>
 * v3.0 重构：
 * <ul>
 *   <li>领域不再有 {@code AgentDraft} 实体，草稿走 agent_version 表 status=DRAFT 行；</li>
 *   <li>{@link #detail} 优先读 {@code agent.config_snapshot} 镜像，避免 join agent_version；</li>
 *   <li>详情草稿提示（hasDraft / draftEditor / draftLockUntil）由
 *       agent_version status=DRAFT 行提供；</li>
 *   <li>{@link #versionList} 出参 {@link AgentVersionVO#getConfigSnapshot()} 含完整 snapshot
 *       （已脱敏 modelApiKey），前端编辑 DRAFT 行不需再拉 versionDetail。</li>
 * </ul>
 */
@Service
public class AgentQueryService {

    /** v2.2 派生：CONFIG → 人工创建 */
    private static final String AGENT_SOURCE_MANUAL = "MANUAL";
    /** v2.2 派生：A2A → Nacos 注册 */
    private static final String AGENT_SOURCE_NACOS = "NACOS";

    /** v1.2 §10.2：modelApiKey 出参遮罩串，避免泄露明文 API Key */
    private static final String API_KEY_MASKED = "***";

    /** v2.6：A2A 同步历史默认返回上限 */
    private static final int A2A_HISTORY_DEFAULT_LIMIT = 100;

    /** 版本历史默认上限（与历史 Gateway 等价：调用方传 limit 为主，此处仅做兜底） */
    private static final int DEFAULT_VERSION_LIMIT = 50;

    /** 调试目标版本字面量：表示调试草稿态版本（DebugInvokeRequest.targetVersion 约定值）。 */
    private static final String DEBUG_DRAFT_TOKEN = "DRAFT";

    @Resource
    private AgentMapper agentMapper;
    @Resource
    private AgentVersionMapper agentVersionMapper;
    @Resource
    private A2aSyncHistoryMapper a2aSyncHistoryMapper;
    @Resource
    private SkillMapper skillMapper;
    @Resource
    private SkillVersionMapper skillVersionMapper;

    /**
     * 列表查询：多条件分页 + CONFIG 镜像 snapshot + skill 名称反查。
     * <p>
     * CONFIG Agent 的 ConfigSnapshot 直接读 {@code agent.config_snapshot} 镜像；
     * 镜像为 null（旧数据兜底）时回落到 agent_version current 行。
     */
    public PageVO<AgentListItemDTO> pageList(AgentPageQuery q, String workspaceNum) {
        Assert.notNull(q, "查询参数不能为空");
        int safePageNo = q.getPageNo() == null ? 1 : q.getPageNo();
        int safePageSize = q.getPageSize() == null ? 20 : q.getPageSize();
        Page<AgentEntity> page = Page.of(safePageNo, safePageSize);
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getDeleted, 0)
                // 工作空间条件过滤：由 Controller 经 BaseController 取得后传入
                .eq(StrUtil.isNotBlank(workspaceNum), AgentEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(q.getCreationMode()), AgentEntity::getCreationMode, q.getCreationMode())
                .eq(StrUtil.isNotBlank(q.getAgentType()), AgentEntity::getAgentType, q.getAgentType())
                .eq(StrUtil.isNotBlank(q.getStatus()), AgentEntity::getStatus, q.getStatus())
                .and(StrUtil.isNotBlank(q.getKeyword()), w -> w
                        .like(AgentEntity::getName, q.getKeyword())
                        .or().like(AgentEntity::getDescription, q.getKeyword()))
                .orderByDesc(AgentEntity::getUpdateTime);
        if (StrUtil.isNotBlank(q.getModel())) {
            // model 字段在 config_snapshot JSON 内，MySQL JSON 搜索成本较高且 v2 仅做关键字过滤；此处忽略 model 条件以保持单表轻查询
            // 如需精确 model 过滤，应由前端在结果集再做或后续引入 QueryMapper
        }
        Page<AgentEntity> result = agentMapper.selectPage(page, wrapper);
        List<AgentEntity> rows = result.getRecords();

        // 1. CONFIG Agent 加载 ConfigSnapshot（优先 agent.config_snapshot 镜像，否则回落 current 版本）
        Map<String, ConfigSnapshot> snapshotByAgentNum = loadSnapshotsForConfig(rows);

        // 2. 收集所有需要反查的 skillNums，一次查 name 映射
        Set<String> allSkillNums = new HashSet<>();
        snapshotByAgentNum.values().forEach(snap -> {
            if (snap != null && snap.getSkillNums() != null) {
                allSkillNums.addAll(snap.getSkillNums());
            }
        });
        Map<String, String> skillNameByNum = allSkillNums.isEmpty()
                ? Collections.emptyMap()
                : findSkillNamesByNums(new ArrayList<>(allSkillNums));

        // 3. 组装 DTO
        List<AgentListItemDTO> list = rows.stream()
                .map(e -> toListItemDTO(e, snapshotByAgentNum.get(e.getNum()), skillNameByNum))
                .toList();
        return PageVO.of(list, result.getTotal(), safePageNo, safePageSize);
    }

    /**
     * 详情：按 creationMode 分支装配。
     * <p>
     * CONFIG Agent 优先读 {@code agent.config_snapshot} 镜像；草稿信息读 agent_version status=DRAFT 行。
     */
    public AgentDetailViewDTO detail(String agentNum, String workspaceNum) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在");
        }
        // 跨空间访问拦截：传入空间编号且与资源归属不一致时拒绝
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间的 Agent");
        }
        AgentDetailViewDTO vo = new AgentDetailViewDTO();
        vo.setNum(entity.getNum());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        // 业务标签 JSON → List（CONFIG / A2A 共用）
        if (entity.getTags() != null && !entity.getTags().isEmpty()) {
            vo.setTags(JSON.parseArray(entity.getTags(), String.class));
        }
        vo.setCreationMode(entity.getCreationMode());
        vo.setAgentType(entity.getAgentType());
        vo.setOwnerUserId(entity.getOwnerUserId());
        vo.setStatus(entity.getStatus());
        vo.setCurrentVersionNum(entity.getCurrentVersionNum());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        CreationMode mode = CreationMode.valueOf(entity.getCreationMode());
        if (mode == CreationMode.A2A) {
            A2aSourceInfo source = parseA2aSource(entity.getA2aSource());
            vo.setA2aSource(toA2aSourceViewDTO(source));
            vo.setHasDraft(false);
        } else {
            ConfigSnapshot snapshot = parseConfigSnapshot(entity.getConfigSnapshot());
            AgentVersionEntity current = findCurrentVersion(agentNum);
            if (snapshot == null && current != null) {
                snapshot = parseConfigSnapshot(current.getConfigSnapshot());
            }
            if (snapshot != null) {
                Map<String, Object> snapshotMap = snapshotToMap(snapshot);
                maskApiKey(snapshotMap);
                vo.setCurrentSnapshot(snapshotMap);
            }
            if (current != null) {
                vo.setCurrentVersion(toVersionDetailViewDTO(current));
            }

            AgentVersionEntity draft = findDraftVersion(agentNum);
            vo.setHasDraft(draft != null);
            if (draft != null) {
                vo.setDraftEditor(draft.getEditorUserId());
                vo.setDraftLockUntil(draft.getLockUntil());
            }
        }
        return vo;
    }

    /** 版本历史（含 DRAFT / PUBLISHED / ARCHIVED）；A2A 直接返回空。 */
    public List<AgentVersionViewDTO> versionList(String agentNum, int limit) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .select(AgentEntity::getNum, AgentEntity::getCreationMode)
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在");
        }
        if (CreationMode.valueOf(entity.getCreationMode()) == CreationMode.A2A) {
            return Collections.emptyList();
        }
        int safeLimit = limit <= 0 ? DEFAULT_VERSION_LIMIT : limit;
        return agentVersionMapper.selectList(new LambdaQueryWrapper<AgentVersionEntity>()
                        .eq(AgentVersionEntity::getAgentNum, agentNum)
                        .eq(AgentVersionEntity::getDeleted, 0)
                        .orderByDesc(AgentVersionEntity::getPublishedAt)
                        .orderByDesc(AgentVersionEntity::getCreateTime)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toVersionViewDTO)
                .toList();
    }

    /** 历史版本只读详情。 */
    public AgentVersionDetailViewDTO versionDetail(String agentNum, String versionNum) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        Assert.notBlank(versionNum, "版本号不能为空");
        AgentVersionEntity v = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getVersionNum, versionNum)
                .eq(AgentVersionEntity::getDeleted, 0));
        if (v == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "版本不存在");
        }
        return toVersionDetailViewDTO(v);
    }

    /**
     * v2.6：A2A Nacos 同步历史列表。
     */
    public List<AgentA2aSyncHistoryDTO> a2aSyncHistory(String agentNum, Integer limit) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .select(AgentEntity::getNum, AgentEntity::getCreationMode)
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在");
        }
        if (CreationMode.valueOf(entity.getCreationMode()) != CreationMode.A2A) {
            return Collections.emptyList();
        }
        int safeLimit = limit == null || limit <= 0
                ? A2A_HISTORY_DEFAULT_LIMIT
                : Math.min(limit, A2A_HISTORY_DEFAULT_LIMIT);
        List<A2aSyncHistoryEntity> rows = a2aSyncHistoryMapper.selectList(
                new LambdaQueryWrapper<A2aSyncHistoryEntity>()
                        .eq(A2aSyncHistoryEntity::getAgentNum, agentNum)
                        .orderByDesc(A2aSyncHistoryEntity::getSyncedAt)
                        .orderByDesc(A2aSyncHistoryEntity::getId)
                        .last("LIMIT " + safeLimit));
        List<AgentA2aSyncHistoryDTO> result = new ArrayList<>(rows.size());
        for (A2aSyncHistoryEntity h : rows) {
            result.add(toSyncHistoryDTO(h));
        }
        return result;
    }

    /**
     * v2.6 / 架构重构：列出所有需要 Nacos 兜底对账的 A2A Agent。
     * <p>
     * 业务规则：{@code creationMode = A2A} 且 {@code status ∈ {PENDING_SYNC, PUBLISHED}}。
     */
    public List<A2aSyncCandidateVO> listA2aSyncCandidates() {
        List<AgentEntity> rows = agentMapper.selectList(
                new LambdaQueryWrapper<AgentEntity>()
                        .select(AgentEntity::getNum, AgentEntity::getName)
                        .eq(AgentEntity::getCreationMode, CreationMode.A2A.name())
                        .in(AgentEntity::getStatus,
                                AgentStatus.PENDING_SYNC.name(),
                                AgentStatus.PUBLISHED.name())
                        .eq(AgentEntity::getDeleted, 0));
        List<A2aSyncCandidateVO> result = new ArrayList<>(rows.size());
        for (AgentEntity e : rows) {
            result.add(new A2aSyncCandidateVO(e.getNum(), e.getName()));
        }
        return result;
    }

    /**
     * 列出仅 PENDING_SYNC 状态的 A2A Agent 候选（hotfix_20260625_a2a-create-endpoints 同步能力补充）。
     * <p>
     * 区别于 {@link #listA2aSyncCandidates()}（含 PENDING_SYNC + PUBLISHED），本方法专供
     * {@code A2aSyncApplicationService.syncPendingBatch} 推进「已确认接入但 Nacos 尚未首次回写」的中间态
     * Agent。PUBLISHED 状态走 Nacos 推送或人工手动重新同步，本方法不参与。
     * <p>
     * <b>冷却窗口</b>：{@code createTime >= 1 minute ago} 的行被过滤，避免刚 createA2a 的行
     * 在 Nacos 收到推送之前被反复拉取报错（fetcher.fetch 此时拿到 null / 远端不可达）。
     *
     * @param limit 返回条数上限（保护对账任务单次负载）
     * @return PENDING_SYNC 候选（按 id 升序，先入先出）
     */
    public List<A2aSyncCandidateVO> listPendingSyncCandidates(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusMinutes(1L);
        List<AgentEntity> rows = agentMapper.selectList(
                new LambdaQueryWrapper<AgentEntity>()
                        .select(AgentEntity::getNum, AgentEntity::getName)
                        .eq(AgentEntity::getCreationMode, CreationMode.A2A.name())
                        .eq(AgentEntity::getStatus, AgentStatus.PENDING_SYNC.name())
                        .lt(AgentEntity::getCreateTime, threshold)
                        .eq(AgentEntity::getDeleted, 0)
                        .orderByAsc(AgentEntity::getId)
                        .last("LIMIT " + limit));
        List<A2aSyncCandidateVO> result = new ArrayList<>(rows.size());
        for (AgentEntity e : rows) {
            result.add(new A2aSyncCandidateVO(e.getNum(), e.getName()));
        }
        return result;
    }

    /**
     * v2.6 / 架构重构：按 Nacos 服务幂等键查 Agent 业务编号。
     *
     * @param nacosServiceKey 幂等键 = nacosGroup@@nacosService（非空）
     * @return 命中行 num；未命中返回 {@code null}
     */
    public String findNumByNacosServiceKey(String nacosServiceKey) {
        if (nacosServiceKey == null || nacosServiceKey.isBlank()) {
            return null;
        }
        AgentEntity row = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentEntity>()
                        .select(AgentEntity::getNum)
                        .eq(AgentEntity::getNacosServiceKey, nacosServiceKey)
                        .eq(AgentEntity::getDeleted, 0));
        return row == null ? null : row.getNum();
    }

    /**
     * v2.7 / Runner 调用专用:按 num 加载 Agent 调用上下文 DTO。
     * <p>
     * 用途:{@code ConfigAgentRunner} / {@code A2aAgentRunner} 等 application 层 Strategy
     * 不允许直接注入 {@code AgentRepository} / {@code AgentMapper}(详见
     * {@code docs/CODING-CONVENTIONS.md §3.2}),必须通过本方法拿到 {@link AgentInvokeDTO} 后
     * 再传给 {@code ConfigAgentBuilder} 等下游。
     *
     * @param agentNum Agent 业务编号,非空
     * @return 仅含 num / name / description / agentType / creationMode / status 的最小 DTO
     * @throws BusinessException Agent 不存在
     */
    public AgentInvokeDTO loadAgentForInvoke(String agentNum) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .select(AgentEntity::getNum, AgentEntity::getName, AgentEntity::getDescription,
                        AgentEntity::getAgentType, AgentEntity::getCreationMode, AgentEntity::getStatus)
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在 num=" + agentNum);
        }
        return AgentInvokeDTO.builder()
                .num(entity.getNum())
                .name(entity.getName())
                .description(entity.getDescription())
                .agentType(entity.getAgentType())
                .creationMode(entity.getCreationMode())
                .status(entity.getStatus())
                .build();
    }

    /**
     * 按 num 查 Agent 元信息 DTO(全字段)。
     * <p>
     * 与 {@link #loadAgentForInvoke(String)} 区别:本方法返回全字段(含 ownerUserId / configSnapshot /
     * a2aSource / 时间戳等),用于 application 内部业务编排;{@code loadAgentForInvoke} 仅返回
     * Runner 调用所需的最小集合。
     * <p>
     * <b>结构化字段</b>:{@code configSnapshot} / {@code a2aSource} 已由本方法用 fastjson2
     * 反序列化为 {@link AgentDTO.ConfigSnapshot} / {@link AgentDTO.A2aSource} 强类型对象;
     * 调用方无需再次解析 JSON。{@code modelApiKey} 等敏感字段不在本方法做脱敏,由出参 VO 转换层处理。
     *
     * @param agentNum Agent 业务编号,非空
     * @return AgentDTO(永不为 null)
     * @throws BusinessException Agent 不存在
     */
    public AgentDTO findAgentByNum(String agentNum) {
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在 num=" + agentNum);
        }
        return toAgentDTO(entity);
    }

    /**
     * 按 num 查 Agent 当前在线版本快照 DTO。
     * <p>
     * 命中规则:{@code agent_num = ? AND current_flag = 1 AND deleted = 0};理论上至多一行。
     * 若 Agent 尚未发布(纯 DRAFT 状态),返回 {@code null}。
     * <p>
     * <b>JSON 字段</b>:{@code configSnapshotJson} 原样透传 DB 中的 JSON 字符串,
     * 不在本方法做反序列化或脱敏;调用方按需处理(详见 {@link AgentVersionDTO} 字段策略说明)。
     *
     * @param agentNum Agent 业务编号,非空
     * @return AgentVersionDTO;无当前在线版本时返回 {@code null}
     */
    public AgentVersionDTO findCurrentVersionByAgentNum(String agentNum) {
        AgentVersionEntity entity = findCurrentVersion(agentNum);
        return entity == null ? null : toVersionDTO(entity);
    }

    // ============================================================
    // Command 用例支撑读（CQRS：写侧 AgentCommandService 不直接碰 Mapper / 读网关，
    // 一律经本服务取“编号 / 布尔”，再用 factory.createByNum 重建领域对象去 save）
    // ============================================================

    /**
     * 校验同 workspace 下 name 是否已存在（创建/重命名前置检查；排除 deleted=1 / sandbox=1）。
     *
     * @param workspaceNum 归属工作空间业务编号
     * @param name         Agent 名称
     * @return true=已存在
     */
    public boolean existsByWorkspaceAndName(String workspaceNum, String name) {
        Long cnt = agentMapper.selectCount(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceNum, workspaceNum)
                .eq(AgentEntity::getName, name)
                .eq(AgentEntity::getSandbox, 0)
                .eq(AgentEntity::getDeleted, 0));
        return cnt != null && cnt > 0;
    }

    /**
     * 某 Agent 当前是否已有草稿版本（status=DRAFT）。
     *
     * @param agentNum Agent 业务编号
     * @return true=已存在草稿
     */
    public boolean hasDraftVersion(String agentNum) {
        return findDraftVersion(agentNum) != null;
    }

    /**
     * 查找某 Agent 草稿版本的业务编号（status=DRAFT）；无草稿返回 null。
     * <p>供 Command 在 publish / editDraft 时拿到 num 后用 {@code agentVersionFactory.createByNum} 重建领域对象。
     *
     * @param agentNum Agent 业务编号
     * @return 草稿版本 num；无则 null
     */
    public String findDraftVersionNum(String agentNum) {
        AgentVersionEntity e = findDraftVersion(agentNum);
        return e == null ? null : e.getNum();
    }

    /**
     * 查找某 Agent 当前在线版本的业务编号（current_flag=1）；无则返回 null。
     *
     * @param agentNum Agent 业务编号
     * @return 当前在线版本 num；无则 null
     */
    public String findCurrentVersionNum(String agentNum) {
        AgentVersionEntity e = findCurrentVersion(agentNum);
        return e == null ? null : e.getNum();
    }

    /**
     * 查找某 Agent 当前在线版本的主键 id（current_flag=1）；无则返回 null。
     * <p>供 Command 在发布事务内调用 {@code agentVersionGateway.switchCurrent(oldId, newId)} 翻转 current 标记。
     *
     * @param agentNum Agent 业务编号
     * @return 当前在线版本主键 id；无则 null
     */
    public Long findCurrentVersionId(String agentNum) {
        AgentVersionEntity e = findCurrentVersion(agentNum);
        return e == null ? null : e.getId();
    }

    /**
     * 按 num 查 A2A 同步历史 DTO 列表(倒序,默认上限 {@value #A2A_HISTORY_DEFAULT_LIMIT})。
     * <p>
     * 排序:{@code synced_at DESC, id DESC};本表是 append-only 审计表,无 deleted 字段。
     * 非 A2A Agent / 无历史返回空列表(永不为 null)。
     * <p>
     * <b>JSON 字段</b>:{@code agentCardJson} 原样透传 DB 中的 JSON 字符串,
     * 不在本方法做反序列化(详见 {@link AgentA2aSyncHistoryDTO} 字段策略说明)。
     *
     * @param agentNum Agent 业务编号,非空
     * @return 同步历史 DTO 列表(可空但非 null);最多 {@value #A2A_HISTORY_DEFAULT_LIMIT} 条
     */
    public List<AgentA2aSyncHistoryDTO> findA2aSyncHistoryByAgentNum(String agentNum) {
        List<A2aSyncHistoryEntity> rows = a2aSyncHistoryMapper.selectList(
                new LambdaQueryWrapper<A2aSyncHistoryEntity>()
                        .eq(A2aSyncHistoryEntity::getAgentNum, agentNum)
                        .orderByDesc(A2aSyncHistoryEntity::getSyncedAt)
                        .orderByDesc(A2aSyncHistoryEntity::getId)
                        .last("LIMIT " + A2A_HISTORY_DEFAULT_LIMIT));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentA2aSyncHistoryDTO> result = new ArrayList<>(rows.size());
        for (A2aSyncHistoryEntity h : rows) {
            result.add(toSyncHistoryDTO(h));
        }
        return result;
    }

    // ============================================================
    // 调试台版本化调试支撑（Agent 绑定 Skill 版本与版本化调试）
    // ============================================================

    /**
     * 调试台版本选择器数据源：列出该 Agent 全部可调试版本（含草稿）。
     * <p>
     * 覆盖 DRAFT + PUBLISHED + ARCHIVED 三态；排序 <b>草稿态 → 当前在线 → 历史（发布时间倒序）</b>。
     * 每项带 {@code statusLabel}（草稿态 / 发布态 / 历史态）供前端标注。A2A 不参与版本化，直接返回空。
     *
     * @param agentNum Agent 业务编号
     * @return 可调试版本列表；A2A / 无版本时返回空列表
     */
    public List<AgentDebugVersionVO> debugVersionList(String agentNum) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .select(AgentEntity::getNum, AgentEntity::getCreationMode)
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在");
        }
        if (CreationMode.valueOf(entity.getCreationMode()) == CreationMode.A2A) {
            return Collections.emptyList();
        }
        List<AgentVersionEntity> rows = agentVersionMapper.selectList(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getDeleted, 0));
        return rows.stream()
                .sorted(Comparator
                        .comparingInt((AgentVersionEntity v) -> statusRank(v.getStatus()))
                        .thenComparing(v -> v.getPublishedAt() == null
                                        ? java.time.LocalDateTime.MIN : v.getPublishedAt(),
                                Comparator.reverseOrder()))
                .map(this::toDebugVersionVO)
                .collect(Collectors.toList());
    }

    /**
     * 加载指定目标版本的 Agent 调用上下文 DTO（供调试运行时按版本装配 Runner）。
     * <p>
     * <ul>
     *   <li>A2A：不参与版本化，忽略 {@code targetVersion}，返回当前全字段 DTO（含 a2aSource）；</li>
     *   <li>{@code targetVersion} 为空：返回当前在线版本镜像（生产 / 最新在线行为，不变）；</li>
     *   <li>{@code targetVersion == "DRAFT"}：返回草稿态版本快照；</li>
     *   <li>否则：按版本号返回对应已发布 / 历史版本快照。</li>
     * </ul>
     * {@code configSnapshot} 取自目标版本（含其绑定的 {@code skillRefs}）；{@code name} 优先取快照。
     *
     * @param agentNum      Agent 业务编号
     * @param targetVersion 目标版本（空 / vX.Y.Z / DRAFT）
     * @return AgentDTO（永不为 null）
     * @throws BusinessException Agent / 目标版本不存在
     */
    public AgentDTO loadAgentForDebug(String agentNum, String targetVersion) {
        Assert.notBlank(agentNum, "Agent 业务编号不能为空");
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getNum, agentNum)
                .eq(AgentEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在 num=" + agentNum);
        }
        // A2A 不版本化 / 空目标版本 → 走当前在线镜像（与 findAgentByNum 一致）
        if (CreationMode.valueOf(entity.getCreationMode()) == CreationMode.A2A
                || StrUtil.isBlank(targetVersion)) {
            return toAgentDTO(entity);
        }
        // CONFIG + 指定目标版本：解析目标版本快照
        AgentVersionEntity ve;
        if (DEBUG_DRAFT_TOKEN.equalsIgnoreCase(targetVersion)) {
            ve = findDraftVersion(agentNum);
            if (ve == null) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(),
                        "草稿版本不存在 agentNum=" + agentNum);
            }
        } else {
            ve = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                    .eq(AgentVersionEntity::getAgentNum, agentNum)
                    .eq(AgentVersionEntity::getVersionNum, targetVersion)
                    .eq(AgentVersionEntity::getDeleted, 0));
            if (ve == null) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(),
                        "版本不存在 agentNum=" + agentNum + " version=" + targetVersion);
            }
        }
        AgentDTO.ConfigSnapshot cs = parseClientConfigSnapshot(ve.getConfigSnapshot());
        String name = (cs != null && StrUtil.isNotBlank(cs.getName())) ? cs.getName() : entity.getName();
        return AgentDTO.builder()
                .id(entity.getId())
                .num(entity.getNum())
                .name(name)
                .description(entity.getDescription())
                .creationMode(entity.getCreationMode())
                .agentType(entity.getAgentType())
                .ownerUserId(entity.getOwnerUserId())
                .status(entity.getStatus())
                .currentVersionNum(ve.getVersionNum())
                .configSnapshot(cs)
                .sandbox(entity.getSandbox())
                .a2aSource(null)
                .nacosServiceKey(entity.getNacosServiceKey())
                .createNo(entity.getCreateNo())
                .updateNo(entity.getUpdateNo())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 已绑定 Skill 的版本状态（新版本提示）：逐个比较绑定版本与最新发布版。
     * <p>
     * 数据取自目标版本快照的 {@code skillRefs}（无 refs 的 legacy 快照回落 {@code skillNums}，
     * 此时 boundVersion 为 null、hasNewer=false）。{@code latestVersion} = {@code Skill.currentVersionNum}；
     * {@code boundDeprecated} = 绑定版本对应 SkillVersion 不存在或状态 DEPRECATED。
     *
     * @param agentNum      Agent 业务编号
     * @param targetVersion 目标版本（空 / vX.Y.Z / DRAFT）
     * @return 已绑定 Skill 的版本状态列表（可空但非 null）
     */
    public List<AgentSkillBindingStatusVO> skillBindingStatus(String agentNum, String targetVersion) {
        AgentDTO dto = loadAgentForDebug(agentNum, targetVersion);
        AgentDTO.ConfigSnapshot cs = dto.getConfigSnapshot();
        if (cs == null) {
            return Collections.emptyList();
        }
        List<AgentSkillBindingStatusVO> result = new ArrayList<>();
        if (CollUtil.isNotEmpty(cs.getSkillRefs())) {
            for (AgentDTO.ConfigSnapshot.SkillRef ref : cs.getSkillRefs()) {
                if (ref == null || StrUtil.isBlank(ref.getSkillNum())) {
                    continue;
                }
                result.add(buildBindingStatus(ref.getSkillNum(), ref.getVersionNum()));
            }
        } else if (CollUtil.isNotEmpty(cs.getSkillNums())) {
            for (String skillNum : cs.getSkillNums()) {
                if (StrUtil.isBlank(skillNum)) {
                    continue;
                }
                result.add(buildBindingStatus(skillNum, null));
            }
        }
        return result;
    }

    /** 单个已绑定 Skill 的版本状态组装。 */
    private AgentSkillBindingStatusVO buildBindingStatus(String skillNum, String boundVersion) {
        SkillEntity skill = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getNum, skillNum)
                .eq(SkillEntity::getDeleted, 0));
        String latestVersion = skill == null ? null : skill.getCurrentVersionNum();
        String skillName = skill == null ? null : skill.getName();
        boolean hasNewer = StrUtil.isNotBlank(latestVersion)
                && StrUtil.isNotBlank(boundVersion)
                && !latestVersion.equals(boundVersion);
        boolean boundDeprecated = false;
        if (StrUtil.isNotBlank(boundVersion)) {
            SkillVersionEntity bv = skillVersionMapper.selectOne(new LambdaQueryWrapper<SkillVersionEntity>()
                    .eq(SkillVersionEntity::getSkillNum, skillNum)
                    .eq(SkillVersionEntity::getVersion, boundVersion));
            boundDeprecated = bv == null || SkillStatus.DEPRECATED.name().equals(bv.getStatus());
        }
        return AgentSkillBindingStatusVO.builder()
                .skillNum(skillNum)
                .skillName(skillName)
                .boundVersion(boundVersion)
                .latestVersion(latestVersion)
                .hasNewer(hasNewer)
                .boundDeprecated(boundDeprecated)
                .build();
    }

    /** AgentVersionEntity → AgentDebugVersionVO（含中文状态标签）。 */
    private AgentDebugVersionVO toDebugVersionVO(AgentVersionEntity v) {
        return AgentDebugVersionVO.builder()
                .versionNum(v.getVersionNum())
                .status(v.getStatus())
                .statusLabel(statusLabel(v.getStatus()))
                .current(v.getCurrentFlag() != null && v.getCurrentFlag() == 1)
                .publishedTime(v.getPublishedAt())
                .remark(v.getRemark())
                .build();
    }

    /** 版本排序权重：草稿态最前，其次当前在线（PUBLISHED），最后历史（ARCHIVED）。 */
    private int statusRank(String status) {
        if (AgentVersionStatus.DRAFT.name().equals(status)) {
            return 0;
        }
        if (AgentVersionStatus.PUBLISHED.name().equals(status)) {
            return 1;
        }
        if (AgentVersionStatus.ARCHIVED.name().equals(status)) {
            return 2;
        }
        return 3;
    }

    /** 版本状态 → 中文标签：草稿态 / 发布态 / 历史态。 */
    private String statusLabel(String status) {
        if (AgentVersionStatus.DRAFT.name().equals(status)) {
            return "草稿态";
        }
        if (AgentVersionStatus.PUBLISHED.name().equals(status)) {
            return "发布态";
        }
        if (AgentVersionStatus.ARCHIVED.name().equals(status)) {
            return "历史态";
        }
        return status;
    }

    // ---- private helpers ----

    /**
     * AgentEntity → AgentDTO 全字段拷贝。
     * <p>
     * 枚举字段保留 {@code name()} 字符串;{@code config_snapshot} / {@code a2a_source} JSON 列
     * 由 fastjson2 直接反序列化为 {@link AgentDTO.ConfigSnapshot} / {@link AgentDTO.A2aSource}
     * 客户端强类型镜像(字段命名与 domain VO 完全一致,enum 自动以 {@code name()} 装入 String 字段)。
     */
    private AgentDTO toAgentDTO(AgentEntity e) {
        return AgentDTO.builder()
                .id(e.getId())
                .num(e.getNum())
                .name(e.getName())
                .description(e.getDescription())
                .creationMode(e.getCreationMode())
                .agentType(e.getAgentType())
                .ownerUserId(e.getOwnerUserId())
                .status(e.getStatus())
                .currentVersionNum(e.getCurrentVersionNum())
                .configSnapshot(parseClientConfigSnapshot(e.getConfigSnapshot()))
                .sandbox(e.getSandbox())
                .a2aSource(parseClientA2aSource(e.getA2aSource()))
                .nacosServiceKey(e.getNacosServiceKey())
                .createNo(e.getCreateNo())
                .updateNo(e.getUpdateNo())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    /** AgentVersionEntity → AgentVersionDTO 全字段拷贝;JSON 列原样透传,枚举字段保留 name() 字符串。 */
    private AgentVersionDTO toVersionDTO(AgentVersionEntity v) {
        return AgentVersionDTO.builder()
                .id(v.getId())
                .num(v.getNum())
                .agentNum(v.getAgentNum())
                .status(v.getStatus())
                .versionNum(v.getVersionNum())
                .semverMajor(v.getSemverMajor())
                .semverMinor(v.getSemverMinor())
                .semverPatch(v.getSemverPatch())
                .configSnapshotJson(v.getConfigSnapshot())
                .remark(v.getRemark())
                .publishedBy(v.getPublishedBy())
                .publishedAt(v.getPublishedAt())
                .currentFlag(v.getCurrentFlag())
                .editorUserId(v.getEditorUserId())
                .lockUntil(v.getLockUntil())
                .createNo(v.getCreateNo())
                .updateNo(v.getUpdateNo())
                .createTime(v.getCreateTime())
                .updateTime(v.getUpdateTime())
                .build();
    }

    /** A2aSyncHistoryEntity → AgentA2aSyncHistoryDTO 全字段拷贝;JSON 原样透传。 */
    private AgentA2aSyncHistoryDTO toSyncHistoryDTO(A2aSyncHistoryEntity h) {
        return AgentA2aSyncHistoryDTO.builder()
                .id(h.getId())
                .agentNum(h.getAgentNum())
                .remoteVersion(h.getRemoteVersion())
                .syncEventType(h.getSyncEventType())
                .triggeredBy(h.getTriggeredBy())
                .agentCardJson(h.getAgentCardJson())
                .syncedAt(h.getSyncedAt())
                .build();
    }

    private AgentVersionEntity findCurrentVersion(String agentNum) {
        return agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getCurrentFlag, 1)
                .eq(AgentVersionEntity::getDeleted, 0));
    }

    private AgentVersionEntity findDraftVersion(String agentNum) {
        return agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getStatus, AgentVersionStatus.DRAFT.name())
                .eq(AgentVersionEntity::getDeleted, 0));
    }

    /** 批量按 num 反查 Skill 名称，返回 num → name 映射。 */
    private Map<String, String> findSkillNamesByNums(List<String> nums) {
        List<SkillEntity> rows = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .select(SkillEntity::getNum, SkillEntity::getName)
                .in(SkillEntity::getNum, nums)
                .eq(SkillEntity::getDeleted, 0));
        return rows.stream().collect(Collectors.toMap(SkillEntity::getNum, SkillEntity::getName, (a, b) -> a));
    }

    /**
     * 批量加载 CONFIG Agent 的 ConfigSnapshot。
     * <p>
     * 优先读 {@code agent.config_snapshot} 镜像；为 null（旧数据未回填）时回落到 current 版本。
     */
    private Map<String, ConfigSnapshot> loadSnapshotsForConfig(List<AgentEntity> agents) {
        Set<String> needFallback = new LinkedHashSet<>();
        Map<String, ConfigSnapshot> map = new HashMap<>();
        for (AgentEntity a : agents) {
            if (CreationMode.valueOf(a.getCreationMode()) == CreationMode.A2A) {
                continue;
            }
            ConfigSnapshot snapshot = parseConfigSnapshot(a.getConfigSnapshot());
            if (snapshot != null) {
                map.put(a.getNum(), snapshot);
            } else if (a.getCurrentVersionNum() != null) {
                needFallback.add(a.getNum());
            }
        }
        if (!needFallback.isEmpty()) {
            List<AgentVersionEntity> currents = agentVersionMapper.selectList(
                    new LambdaQueryWrapper<AgentVersionEntity>()
                            .in(AgentVersionEntity::getAgentNum, needFallback)
                            .eq(AgentVersionEntity::getCurrentFlag, 1)
                            .eq(AgentVersionEntity::getDeleted, 0));
            for (AgentVersionEntity v : currents) {
                ConfigSnapshot snapshot = parseConfigSnapshot(v.getConfigSnapshot());
                if (snapshot != null) {
                    map.put(v.getAgentNum(), snapshot);
                }
            }
        }
        return map;
    }

    /** 列表 DTO 组装。 */
    private AgentListItemDTO toListItemDTO(AgentEntity a, ConfigSnapshot snapshot, Map<String, String> skillNameByNum) {
        AgentListItemDTO vo = new AgentListItemDTO();
        vo.setNum(a.getNum());
        vo.setName(a.getName());
        vo.setDescription(a.getDescription());
        vo.setStatus(a.getStatus());
        vo.setCreationMode(a.getCreationMode());
        CreationMode mode = CreationMode.valueOf(a.getCreationMode());
        vo.setAgentSource(deriveAgentSource(mode));
        vo.setCreateTime(a.getCreateTime());
        vo.setUpdateTime(a.getUpdateTime());

        List<String> skillNames = new ArrayList<>();
        if (mode == CreationMode.A2A) {
            A2aSourceInfo source = parseA2aSource(a.getA2aSource());
            if (source != null && source.getRemoteSkills() != null) {
                source.getRemoteSkills().forEach(rs -> {
                    if (rs.getName() != null) {
                        skillNames.add(rs.getName());
                    }
                });
            }
        } else if (snapshot != null && snapshot.getSkillNums() != null) {
            for (String num : snapshot.getSkillNums()) {
                String name = skillNameByNum.get(num);
                if (name != null) {
                    skillNames.add(name);
                }
            }
        }
        vo.setSkillNames(skillNames);
        vo.setSkillNum(skillNames.size());
        return vo;
    }

    /** v2.2 派生规则：CONFIG → MANUAL，A2A → NACOS。 */
    private String deriveAgentSource(CreationMode mode) {
        return mode == CreationMode.A2A ? AGENT_SOURCE_NACOS : AGENT_SOURCE_MANUAL;
    }

    /**
     * v1.2 §10.2：详情接口出参兜底脱敏。
     */
    private void maskApiKey(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return;
        }
        Object apiKey = snapshot.get("modelApiKey");
        if (apiKey != null && !apiKey.toString().isEmpty()) {
            snapshot.put("modelApiKey", API_KEY_MASKED);
        }
    }

    /** AgentVersionEntity → AgentVersionViewDTO（含 status / editorUserId / lockUntil / configSnapshot）。 */
    private AgentVersionViewDTO toVersionViewDTO(AgentVersionEntity v) {
        AgentVersionViewDTO vo = new AgentVersionViewDTO();
        vo.setNum(v.getNum());
        vo.setAgentNum(v.getAgentNum());
        vo.setStatus(v.getStatus());
        vo.setVersionNum(v.getVersionNum());
        vo.setRemark(v.getRemark());
        vo.setPublishedBy(v.getPublishedBy());
        vo.setPublishedAt(v.getPublishedAt());
        vo.setCurrent(v.getCurrentFlag() != null && v.getCurrentFlag() == 1);
        vo.setEditorUserId(v.getEditorUserId());
        vo.setLockUntil(v.getLockUntil());
        ConfigSnapshot snapshot = parseConfigSnapshot(v.getConfigSnapshot());
        if (snapshot != null) {
            Map<String, Object> snapshotMap = snapshotToMap(snapshot);
            maskApiKey(snapshotMap);
            vo.setConfigSnapshot(snapshotMap);
        }
        return vo;
    }

    /** AgentVersionEntity → AgentVersionDetailViewDTO（含 snapshot 双字段：snapshot / configSnapshot）。 */
    private AgentVersionDetailViewDTO toVersionDetailViewDTO(AgentVersionEntity v) {
        AgentVersionDetailViewDTO vo = new AgentVersionDetailViewDTO();
        vo.setNum(v.getNum());
        vo.setAgentNum(v.getAgentNum());
        vo.setStatus(v.getStatus());
        vo.setVersionNum(v.getVersionNum());
        vo.setRemark(v.getRemark());
        vo.setPublishedBy(v.getPublishedBy());
        vo.setPublishedAt(v.getPublishedAt());
        vo.setCurrent(v.getCurrentFlag() != null && v.getCurrentFlag() == 1);
        vo.setEditorUserId(v.getEditorUserId());
        vo.setLockUntil(v.getLockUntil());
        ConfigSnapshot snapshot = parseConfigSnapshot(v.getConfigSnapshot());
        Map<String, Object> snapshotMap = snapshot == null ? null : snapshotToMap(snapshot);
        maskApiKey(snapshotMap);
        vo.setSnapshot(snapshotMap);
        vo.setConfigSnapshot(snapshotMap);
        return vo;
    }

    private A2aSourceViewDTO toA2aSourceViewDTO(A2aSourceInfo s) {
        if (s == null) {
            return null;
        }
        A2aSourceViewDTO vo = new A2aSourceViewDTO();
        vo.setNacosGroup(s.getNacosGroup());
        vo.setNacosService(s.getNacosService());
        vo.setInstanceIp(s.getInstanceIp());
        vo.setInstancePort(s.getInstancePort());
        vo.setEndpointPath(s.getEndpointPath());
        vo.setRemoteVersion(s.getRemoteVersion());
        vo.setAgentCardJson(s.getAgentCardJson());
        vo.setLastSyncedAt(s.getLastSyncedAt());
        vo.setLastSyncEventType(s.getLastSyncEventType() == null ? null : s.getLastSyncEventType().name());
        if (s.getRemoteSkills() != null) {
            vo.setRemoteSkills(s.getRemoteSkills().stream()
                    .map(rs -> {
                        A2aSourceViewDTO.RemoteSkill out = new A2aSourceViewDTO.RemoteSkill();
                        out.setName(rs.getName());
                        out.setDescription(rs.getDescription());
                        return out;
                    })
                    .toList());
        }
        if (s.getRemoteMcps() != null) {
            vo.setRemoteMcps(s.getRemoteMcps().stream()
                    .map(rm -> {
                        A2aSourceViewDTO.RemoteMcp out = new A2aSourceViewDTO.RemoteMcp();
                        out.setName(rm.getName());
                        out.setDescription(rm.getDescription());
                        out.setServerUrl(rm.getServerUrl());
                        return out;
                    })
                    .toList());
        }
        return vo;
    }

    /** config_snapshot JSON 反序列化；空或解析失败返回 null。 */
    private ConfigSnapshot parseConfigSnapshot(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, ConfigSnapshot.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** a2a_source JSON 反序列化；空或解析失败返回 null。 */
    private A2aSourceInfo parseA2aSource(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, A2aSourceInfo.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * config_snapshot JSON → client 强类型镜像 {@link AgentDTO.ConfigSnapshot}。
     * <p>
     * 字段命名与 domain {@code ConfigSnapshot} 完全一致;enum 字段(agentType /
     * memoryConfig.shortTermStrategy / memoryConfig.longTermStrategy)在 client 镜像
     * 是 String,fastjson2 自动按 {@code name()} 装入。空或解析失败返回 null。
     */
    private AgentDTO.ConfigSnapshot parseClientConfigSnapshot(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, AgentDTO.ConfigSnapshot.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * a2a_source JSON → client 强类型镜像 {@link AgentDTO.A2aSource}。
     * <p>
     * 字段命名与 domain {@code A2aSourceInfo} 完全一致;{@code lastSyncEventType} 在 client
     * 镜像是 String,fastjson2 自动按 {@code name()} 装入。空或解析失败返回 null。
     */
    private AgentDTO.A2aSource parseClientA2aSource(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, AgentDTO.A2aSource.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** ConfigSnapshot → Map<String, Object> 中转（出参字段一律字符串可读化）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotToMap(ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(snapshot), LinkedHashMap.class);
    }
}
