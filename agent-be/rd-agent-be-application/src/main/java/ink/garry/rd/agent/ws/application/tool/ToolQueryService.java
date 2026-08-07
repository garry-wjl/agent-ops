package ink.garry.rd.agent.ws.application.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.tool.dto.AgentBriefDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.EndpointMetaDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ProxyHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDetailDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolPageQueryParamDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.tool.Tool;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiEndpoint;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiParam;
import ink.garry.rd.agent.ws.domain.tool.valueobject.EndpointMeta;
import ink.garry.rd.agent.ws.domain.tool.valueobject.PackageMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ProxyHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolStatus;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.tool.entity.ToolEntity;
import ink.garry.rd.agent.ws.infra.tool.mapper.ToolMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool 读侧应用服务。
 * <p>
 * 参照 {@code SandboxQueryService} / {@code SkillQueryService}：读查询走 MyBatis-Plus
 * {@link LambdaQueryWrapper} + {@code BaseMapper} 的 selectOne / selectList / selectPage /
 * selectCount，<b>不写自定义 SQL</b>；Entity 经 {@link ToolEntity#toDomain} 转 domain Tool
 * 后再映射为 client DTO，复用 entity 内 fastjson2 JSON 反序列化，避免重复解析逻辑。
 * <p>
 * <b>跨聚合只读</b>：reuseCount / 挂载 Agent 列表需扫 {@code agent} 表的 {@code config_snapshot.mcpNums}，
 * 故注入 {@link AgentMapper} 做只读查询（QueryService 允许注入 infra Mapper；CQRS 约束仅禁止
 * domain Repository / Gateway）。
 *
 * <h3>方法集（工具管理技术方案 §6.2.1）</h3>
 * <ul>
 *   <li>列表 / 详情：{@link #pageList} / {@link #detail}</li>
 *   <li>挂载：{@link #listMountable}（Agent Step4 已发布工具列表）</li>
 *   <li>复用数：{@link #reuseCount} / {@link #listMountedAgents}</li>
 *   <li>校验：{@link #existsByName}（唯一性预检） / {@link #existsReferencedByMcp}（引用检查）</li>
 * </ul>
 */
@Slf4j
@Service
public class ToolQueryService {

    /** 默认分页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 最大分页大小（PRD §9）。 */
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private ToolMapper toolMapper;

    @Resource
    private AgentMapper agentMapper;

    // ============================================================
    // 列表 / 详情
    // ============================================================

    /**
     * 分页查询工具列表（按 workspaceNum + type / creationMode / status / tag / keyword 筛选，
     * 按 update_time DESC），并为每行填充实时 reuseCount。
     *
     * @param param        筛选条件
     * @param workspaceNum 当前工作空间业务编号（由 adapter 经上下文传入）
     * @return 分页结果，元素为 {@link ToolDTO}（含 reuseCount）
     */
    public PageVO<ToolDTO> pageList(ToolPageQueryParamDTO param, String workspaceNum) {
        Assert.notNull(param, "查询参数不能为空");
        requireWorkspace(workspaceNum);
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = normalizePageSize(param.getPageSize());

        LambdaQueryWrapper<ToolEntity> wrapper = Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(param.getType()), ToolEntity::getType, param.getType())
                .eq(StrUtil.isNotBlank(param.getCreationMode()), ToolEntity::getCreationMode, param.getCreationMode())
                .eq(StrUtil.isNotBlank(param.getStatus()), ToolEntity::getStatus, param.getStatus())
                // tag 在 JSON 标签数组内 LIKE 命中（粗匹配，FE 侧已有精确 facet）
                .like(StrUtil.isNotBlank(param.getTag()), ToolEntity::getTags, param.getTag())
                .and(StrUtil.isNotBlank(param.getKeyword()), w -> w
                        .like(ToolEntity::getNum, param.getKeyword())
                        .or().like(ToolEntity::getName, param.getKeyword())
                        .or().like(ToolEntity::getDescription, param.getKeyword()))
                .orderByDesc(ToolEntity::getUpdateTime);

        Page<ToolEntity> page = new Page<>(pageNo, pageSize);
        IPage<ToolEntity> result = toolMapper.selectPage(page, wrapper);

        if (CollUtil.isEmpty(result.getRecords())) {
            return PageVO.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }
        // 一次性聚合已发布 Agent 的挂载计数，避免逐行 N+1 查询
        Map<String, Integer> reuseCountMap = buildReuseCountMap();
        List<ToolDTO> items = result.getRecords().stream()
                .map(e -> {
                    Tool tool = ToolEntity.toDomain(e);
                    return toDTO(tool, reuseCountMap.getOrDefault(tool.getNum(), 0));
                })
                .collect(Collectors.toList());
        return PageVO.of(items, result.getTotal(), pageNo, pageSize);
    }

    /**
     * 加载工具详情（全字段 + reuseCount）。
     *
     * @param num          工具业务编号
     * @param workspaceNum 当前工作空间业务编号；不能为空（跨空间访问拦截）
     * @return 详情 DTO；不存在抛 {@link BusinessException}(TOOL_NOT_FOUND)
     */
    public ToolDetailDTO detail(String num, String workspaceNum) {
        Assert.notBlank(num, "工具业务编号不能为空");
        requireWorkspace(workspaceNum);
        ToolEntity entity = toolMapper.selectOne(Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.TOOL_NOT_FOUND.getCode(), "工具不存在 num=" + num);
        }
        if (!workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间的工具");
        }
        Tool tool = ToolEntity.toDomain(entity);
        return ToolDetailDTO.builder()
                .tool(toDTO(tool, reuseCount(num)))
                .build();
    }

    /**
     * 按业务编号加载工具全字段 DTO（不带工作空间约束，运行时装配用）。
     * <p>
     * 镜像 {@code AgentQueryService.findAgentByNum}：供 {@code ToolRunnerFactory} 等运行时场景按 num
     * 取工具元数据，无前端工作空间上下文，故不做跨空间校验，也不填充 reuseCount。
     *
     * @param num 工具业务编号，非空
     * @return 工具 DTO；不存在抛 {@link BusinessException}(TOOL_NOT_FOUND)
     */
    public ToolDTO findByNum(String num) {
        Assert.notBlank(num, "工具业务编号不能为空");
        ToolEntity entity = toolMapper.selectOne(Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.TOOL_NOT_FOUND.getCode(), "工具不存在 num=" + num);
        }
        return toDTO(ToolEntity.toDomain(entity), null);
    }

    // ============================================================
    // 挂载（Agent Step4 数据源）
    // ============================================================

    /**
     * 列出可挂载工具（仅 status=PUBLISHED），供 Agent CONFIG 模式 Step4 多选挂载。
     * <p>
     * 隐藏 DRAFT / DEPRECATED；按 update_time DESC 返回。FE 侧按 type（MCP / FunctionCall）分组展示。
     *
     * @param workspaceNum 当前工作空间业务编号（由 adapter 经上下文传入）
     * @return 已发布工具 DTO 列表（不填充 reuseCount，挂载下拉无需）
     */
    public List<ToolDTO> listMountable(String workspaceNum) {
        requireWorkspace(workspaceNum);
        List<ToolEntity> entities = toolMapper.selectList(Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getWorkspaceNum, workspaceNum)
                .eq(ToolEntity::getStatus, ToolStatus.PUBLISHED.name())
                .orderByDesc(ToolEntity::getUpdateTime));
        if (CollUtil.isEmpty(entities)) {
            return new ArrayList<>();
        }
        return entities.stream()
                .map(e -> toDTO(ToolEntity.toDomain(e), null))
                .collect(Collectors.toList());
    }

    // ============================================================
    // 复用数
    // ============================================================

    /**
     * 实时统计某工具被多少已发布 Agent 挂载（扫 {@code agent.config_snapshot.mcpNums}）。
     *
     * @param toolNum 工具业务编号
     * @return 挂载该工具的已发布 Agent 数量
     */
    public int reuseCount(String toolNum) {
        if (StrUtil.isBlank(toolNum)) {
            return 0;
        }
        return buildReuseCountMap().getOrDefault(toolNum, 0);
    }

    /**
     * 复用数下钻：列出挂载某工具的已发布 Agent 简表。
     *
     * @param toolNum 工具业务编号
     * @return 挂载该工具的已发布 Agent 简表
     */
    public List<AgentBriefDTO> listMountedAgents(String toolNum) {
        if (StrUtil.isBlank(toolNum)) {
            return new ArrayList<>();
        }
        return listPublishedAgents().stream()
                .filter(e -> toolNumsOf(e).contains(toolNum))
                .map(e -> AgentBriefDTO.builder()
                        .num(e.getNum())
                        .name(e.getName())
                        .ownerUserId(e.getOwnerUserId())
                        .status(e.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    // ============================================================
    // 校验
    // ============================================================

    /**
     * 判断同一工作空间内是否已存在同名工具（不区分类型；用于 createTool / updateTool 唯一性预检
     * 与前端 checkName 失焦校验）。
     *
     * @param workspaceNum 工作空间业务编号；不能为空（缺失直接抛异常，不兜底默认空间）
     * @param name         工具名称
     * @param excludeNum   需排除的工具 num（编辑时排除自身；创建传 null）
     * @return 存在返回 true，否则 false
     */
    public boolean existsByName(String workspaceNum, String name, String excludeNum) {
        Assert.notBlank(name, "name 不能为空");
        requireWorkspace(workspaceNum);
        Long count = toolMapper.selectCount(Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getWorkspaceNum, workspaceNum)
                .eq(ToolEntity::getName, name)
                .ne(StrUtil.isNotBlank(excludeNum), ToolEntity::getNum, excludeNum));
        return count != null && count > 0;
    }

    /**
     * 判断某 FunctionCall 工具是否被任何 MCP API 打包工具（packageMode=EXISTING_API）引用。
     * <p>
     * 用于弃用 / 删除 FC 工具前的引用检查（工具管理技术方案 §0 共识 #8 / PRD §附4）；
     * 被引用则拒绝弃用。已废弃的引用方不再计入（引用关系随其下线失效）。
     *
     * @param fcToolNum 被引用的 FunctionCall 工具 num
     * @return 被引用返回 true，否则 false
     */
    public boolean existsReferencedByMcp(String fcToolNum) {
        if (StrUtil.isBlank(fcToolNum)) {
            return false;
        }
        Long count = toolMapper.selectCount(Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getPackageMode, PackageMode.EXISTING_API.name())
                .eq(ToolEntity::getSourceFcToolNum, fcToolNum)
                .ne(ToolEntity::getStatus, ToolStatus.DEPRECATED.name()));
        return count != null && count > 0;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 规整分页大小到 [1, 100]，默认 20。 */
    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 校验工作空间上下文必须存在；缺失直接拒绝（不兜底默认空间）。 */
    private static void requireWorkspace(String workspaceNum) {
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "未指定工作空间，请先选择工作空间后再操作");
        }
    }

    /** 查全部已发布 Agent（reuseCount / 挂载列表的共同数据源）。 */
    private List<AgentEntity> listPublishedAgents() {
        List<AgentEntity> agents = agentMapper.selectList(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getStatus, AgentStatus.PUBLISHED.name()));
        return agents == null ? new ArrayList<>() : agents;
    }

    /** 聚合「toolNum → 已发布 Agent 挂载数」映射，列表场景一次扫描避免 N+1。 */
    private Map<String, Integer> buildReuseCountMap() {
        Map<String, Integer> counter = new java.util.HashMap<>();
        for (AgentEntity agent : listPublishedAgents()) {
            for (String toolNum : toolNumsOf(agent)) {
                counter.merge(toolNum, 1, Integer::sum);
            }
        }
        return counter;
    }

    /** 从 Agent 的 config_snapshot JSON 解析出挂载的工具编号（v4.0：字段由 mcpNums 改为 toolNums；容错：解析失败返回空）。 */
    private List<String> toolNumsOf(AgentEntity agent) {
        String snapshotJson = agent.getConfigSnapshot();
        if (StrUtil.isBlank(snapshotJson)) {
            return Collections.emptyList();
        }
        try {
            ConfigSnapshot snapshot = JSON.parseObject(snapshotJson, ConfigSnapshot.class);
            if (snapshot == null || CollUtil.isEmpty(snapshot.getToolNums())) {
                return Collections.emptyList();
            }
            return snapshot.getToolNums();
        } catch (Exception e) {
            log.warn("解析 Agent config_snapshot 失败 agentNum={}: {}", agent.getNum(), e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================================================
    // domain Tool → client ToolDTO 映射（CommandService 复用）
    // ============================================================

    /**
     * 领域 Tool → ToolDTO（命令返回 / 列表 / 详情共用，纯字段映射 + 值对象转换）。
     *
     * @param t          领域聚合根（已带全字段，可来自 Entity.toDomain 或 CommandService 落库后对象）
     * @param reuseCount 复用数；挂载下拉等无需统计的场景传 null
     * @return ToolDTO
     */
    public static ToolDTO toDTO(Tool t, Integer reuseCount) {
        if (t == null) {
            return null;
        }
        return ToolDTO.builder()
                .num(t.getNum())
                .workspaceNum(t.getWorkspaceNum())
                .name(t.getName())
                .description(t.getDescription())
                .type(t.getType() == null ? null : t.getType().name())
                .creationMode(t.getCreationMode() == null ? null : t.getCreationMode().name())
                .tags(t.getTags())
                .status(t.getStatus() == null ? null : t.getStatus().name())
                .reuseCount(reuseCount)
                .mcpConfigType(t.getMcpConfigType() == null ? null : t.getMcpConfigType().name())
                .mcpConfig(t.getMcpConfig())
                .proxyEnabled(t.getProxyEnabled())
                .proxyHeaders(toProxyHeaderDTOs(t.getProxyHeaders()))
                .packageMode(t.getPackageMode() == null ? null : t.getPackageMode().name())
                .sourceFcToolNum(t.getSourceFcToolNum())
                .openApiSpec(t.getOpenApiSpec())
                .baseUrl(t.getBaseUrl())
                .endpoints(toEndpointDTOs(t.getEndpoints()))
                .endpointMeta(toEndpointMetaDTO(t.getEndpointMeta()))
                .ownerUserId(t.getOwnerUserId())
                .createNo(t.getCreateNo())
                .updateNo(t.getUpdateNo())
                .createTime(t.getCreateTime())
                .updateTime(t.getUpdateTime())
                .build();
    }

    /** ProxyHeader 值对象列表 → DTO 列表。 */
    private static List<ProxyHeaderDTO> toProxyHeaderDTOs(List<ProxyHeader> headers) {
        if (CollUtil.isEmpty(headers)) {
            return null;
        }
        return headers.stream()
                .map(h -> ProxyHeaderDTO.builder()
                        .name(h.getName())
                        .value(h.getValue())
                        .description(h.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** ApiEndpoint 值对象列表 → DTO 列表（含 query / path / header 嵌套）。 */
    private static List<ApiEndpointDTO> toEndpointDTOs(List<ApiEndpoint> endpoints) {
        if (CollUtil.isEmpty(endpoints)) {
            return null;
        }
        return endpoints.stream()
                .map(ep -> ApiEndpointDTO.builder()
                        .method(ep.getMethod() == null ? null : ep.getMethod().name())
                        .path(ep.getPath())
                        .description(ep.getDescription())
                        .queryParams(toParamDTOs(ep.getQueryParams()))
                        .pathParams(toParamDTOs(ep.getPathParams()))
                        .headers(toHeaderDTOs(ep.getHeaders()))
                        .build())
                .collect(Collectors.toList());
    }

    /** ApiParam 值对象列表 → DTO 列表。 */
    private static List<ApiParamDTO> toParamDTOs(List<ApiParam> params) {
        if (CollUtil.isEmpty(params)) {
            return null;
        }
        return params.stream()
                .map(p -> ApiParamDTO.builder()
                        .name(p.getName())
                        .type(p.getType() == null ? null : p.getType().name())
                        .defaultValue(p.getDefaultValue())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** ApiHeader 值对象列表 → DTO 列表。 */
    private static List<ApiHeaderDTO> toHeaderDTOs(List<ApiHeader> headers) {
        if (CollUtil.isEmpty(headers)) {
            return null;
        }
        return headers.stream()
                .map(h -> ApiHeaderDTO.builder()
                        .name(h.getName())
                        .defaultValue(h.getDefaultValue())
                        .description(h.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** EndpointMeta 值对象 → DTO。 */
    private static EndpointMetaDTO toEndpointMetaDTO(EndpointMeta meta) {
        if (meta == null) {
            return null;
        }
        List<EndpointMetaDTO.EndpointSummaryDTO> summaries = CollUtil.isEmpty(meta.getSummaries())
                ? null
                : meta.getSummaries().stream()
                        .map(s -> EndpointMetaDTO.EndpointSummaryDTO.builder()
                                .path(s.getPath())
                                .method(s.getMethod())
                                .summary(s.getSummary())
                                .build())
                        .collect(Collectors.toList());
        return EndpointMetaDTO.builder()
                .endpointCount(meta.getEndpointCount())
                .summaries(summaries)
                .build();
    }
}
