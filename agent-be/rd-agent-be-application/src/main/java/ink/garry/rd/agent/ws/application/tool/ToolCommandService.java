package ink.garry.rd.agent.ws.application.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.tool.factory.ToolRunnerFactory;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.McpTestConnectionParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.McpTestConnectionResultDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ProxyHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolCreateParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.tool.Tool;
import ink.garry.rd.agent.ws.domain.tool.factory.ToolFactory;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiEndpoint;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiParam;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiParamType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.HttpMethod;
import ink.garry.rd.agent.ws.domain.tool.valueobject.McpConfigType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.PackageMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ProxyHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolStatus;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tool 写侧应用服务。
 * <p>
 * 参照 {@code SandboxCommandService} / {@code SkillCommandService}：注入 domain
 * {@link ToolFactory}（拿聚合 / 调领域动作）+ 读侧 {@link ToolQueryService}
 * （CQRS：唯一性预检 / 引用检查走 QueryService，不直接调 Mapper）+ {@link RedissonClient}
 * （用例级互斥锁）。所有写方法均标 {@code @Transactional(rollbackFor = Exception.class)}，
 * 事务范围内嵌在分布式锁区域内（先锁后事务）。
 *
 * <h3>方法集（工具管理技术方案 §6.2.1）</h3>
 * <ul>
 *   <li>{@link #createTool}：创建草稿（仅 DRAFT；发布走独立 publish）</li>
 *   <li>{@link #updateTool}：编辑字段 → 状态切回 DRAFT；type / creationMode 只读</li>
 *   <li>{@link #publish}：草稿 → 已发布（含 OpenAPI 端点解析）</li>
 *   <li>{@link #unpublish}：已发布 → 已废弃（FC 工具前置引用检查）</li>
 *   <li>{@link #republish}：已废弃 → 已发布</li>
 *   <li>{@link #deleteDraft}：删除草稿（仅 DRAFT）</li>
 * </ul>
 *
 * <h3>分层约束</h3>
 * 禁止注入 / 调用 domain Repository 或 Gateway；加载 / 创建聚合统一经 {@link ToolFactory}，
 * 读查询统一经 {@link ToolQueryService}。operatorId 由 adapter 从 UserContext 注入传入。
 */
@Slf4j
@Service
public class ToolCommandService {

    /** 用例锁等待时长（秒）：抢不到锁时最多再等 3s。 */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /** 用例锁租约时长（秒）：30s 覆盖多步 DB 操作 + 事件发布的整条用例，超时由 Redisson 自动释放。 */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private ToolFactory toolFactory;
    @Resource
    private ToolQueryService toolQueryService;
    @Resource
    private RedissonClient redissonClient;

    /** MCP 测试连接委托给 ToolRunnerFactory。 */
    @Resource
    private ToolRunnerFactory toolRunnerFactory;

    // ============================================================
    // createTool
    // ============================================================

    /**
     * 新建工具（仅落草稿态）。
     * <p>
     * 创建仅 DRAFT，发布走独立 {@link #publish}（工具管理技术方案 §0 共识 #11）。
     * 草稿态仅校验 name / type / creationMode 等基础字段，形态完整性留待发布校验。
     *
     * @param param      创建入参（含 workspaceNum / ownerUserId，由 adapter 从上下文注入）
     * @param operatorId 操作人用户 ID
     * @return 新工具 DTO（含生成的 num，status=DRAFT）
     * @throws BusinessException 参数非法 / 同空间名称冲突 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public ToolDTO createTool(ToolCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        Assert.notBlank(param.getName(), "工具名称不能为空");
        ToolType type = resolveType(param.getType());
        CreationMode mode = resolveCreationMode(param.getCreationMode());
        // 工作空间归属：必须有空间上下文，缺失直接拒绝（不兜底默认空间）
        if (StrUtil.isBlank(param.getWorkspaceNum())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "未指定工作空间，请先选择工作空间后再操作");
        }
        String workspaceNum = param.getWorkspaceNum();

        // 按 (workspaceNum, name) 抢创建锁：num 尚未生成，防连点 / 重试创建同空间同名工具
        String lockKey = LockKeyConstant.TOOL_CREATE_LOCK_PREFIX
                + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            // 名称唯一性预检（CQRS：走 QueryService）
            if (toolQueryService.existsByName(workspaceNum, param.getName(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同一空间内已存在同名工具，请更换");
            }
            Tool tool = toolFactory.buildTool(
                    workspaceNum,
                    param.getName(),
                    param.getDescription(),
                    type,
                    mode,
                    param.getTags(),
                    param.getOwnerUserId(),
                    resolveMcpConfigType(param.getMcpConfigType()),
                    param.getMcpConfig(),
                    param.getProxyEnabled(),
                    toProxyHeaders(param.getProxyHeaders()),
                    resolvePackageMode(param.getPackageMode()),
                    param.getSourceFcToolNum(),
                    param.getOpenApiSpec(),
                    param.getBaseUrl(),
                    toEndpoints(param.getEndpoints()));
            // 领域动作：聚合内统一校验基础不变量 + 生成 num + 落库 + 发 TOOL_SAVED（status 兜底 DRAFT）
            tool.save(operatorId);
            return ToolQueryService.toDTO(tool, 0);
        });
    }

    // ============================================================
    // updateTool
    // ============================================================

    /**
     * 编辑工具（任何字段变更后状态切回 DRAFT；type / creationMode 只读）。
     * <p>
     * 按工具当前形态 set 对应专有字段；编辑后调用 {@code save} 整聚合覆盖落库（PRD §7.7.2）。
     *
     * @param param      编辑入参（num + 待改字段）
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 工具不存在 / 名称冲突 / 参数非法 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTool(ToolUpdateParamDTO param, String operatorId) {
        Assert.notNull(param, "编辑参数不能为空");
        Assert.notBlank(param.getNum(), "工具业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.TOOL_COMMAND_LOCK_PREFIX + param.getNum();
        runWithLock(lockKey, () -> {
            Tool tool = requireByNum(param.getNum());

            // 名称有变化时做同空间唯一性预检（排除自身）
            if (StrUtil.isNotBlank(param.getName()) && !StrUtil.equals(tool.getName(), param.getName())
                    && toolQueryService.existsByName(tool.getWorkspaceNum(), param.getName(), tool.getNum())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同一空间内已存在同名工具，请更换");
            }

            // 通用字段
            if (StrUtil.isNotBlank(param.getName())) {
                tool.setName(param.getName());
            }
            if (param.getDescription() != null) {
                tool.setDescription(param.getDescription());
            }
            if (param.getTags() != null) {
                tool.setTags(param.getTags());
            }

            // 各形态专有字段（按工具实际 creationMode 落对应字段，其余忽略）
            applyShapeFields(tool, param);

            // 任何编辑都把状态切回 DRAFT（已发布编辑后回草稿，复用同一记录）
            tool.setStatus(ToolStatus.DRAFT);
            // 整聚合覆盖落库（聚合内校验基础不变量 + 发 TOOL_SAVED）
            tool.save(operatorId);
            return null;
        });
    }

    // ============================================================
    // publish
    // ============================================================

    /**
     * 发布工具：草稿 → 已发布（走全字段必填 + 形态校验；OpenAPI 形态解析端点元数据）。
     *
     * @param num        工具业务编号
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 工具不存在 / 非草稿态 / 形态校验不通过 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(String num, String operatorId) {
        Assert.notBlank(num, "工具业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.TOOL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Tool tool = requireByNum(num);
            // 领域动作：状态校验 + OpenAPI 解析 + 全字段校验 + 落库 + 发 TOOL_PUBLISHED
            tool.publish(operatorId);
            return null;
        });
    }

    // ============================================================
    // unpublish
    // ============================================================

    /**
     * 弃用工具：已发布 → 已废弃。
     * <p>
     * FunctionCall 工具弃用前做引用检查：被任何 MCP API 打包工具（EXISTING_API）引用则拒绝
     * （工具管理技术方案 §0 共识 #8 / PRD §附4）。
     *
     * @param num        工具业务编号
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 工具不存在 / 非已发布态 / 被引用 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(String num, String operatorId) {
        Assert.notBlank(num, "工具业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.TOOL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Tool tool = requireByNum(num);
            // FC 工具引用检查（CQRS：走 QueryService）
            if (tool.getType() == ToolType.FUNCTION_CALL
                    && toolQueryService.existsReferencedByMcp(num)) {
                throw new BusinessException(BizCode.TOOL_REFERENCED_BY_MCP.getCode(),
                        "该工具被 MCP API 打包工具引用，不能弃用；请先解除引用");
            }
            // 领域动作：状态校验 + 置 DEPRECATED + 发 TOOL_DEPRECATED
            tool.unpublish(operatorId);
            return null;
        });
    }

    // ============================================================
    // republish
    // ============================================================

    /**
     * 重新发布工具：已废弃 → 已发布。
     *
     * @param num        工具业务编号
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 工具不存在 / 非已废弃态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void republish(String num, String operatorId) {
        Assert.notBlank(num, "工具业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.TOOL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Tool tool = requireByNum(num);
            tool.republish(operatorId);
            return null;
        });
    }

    /**
     * 测试 MCP 远程连接。
     * <p>
     * 委托 {@link ToolRunnerFactory#testConnection(McpTestConnectionParamDTO)} 执行实际连接测试；
     * 本服务仅做参数透传，不持锁、不开事务（非持久化操作）。
     *
     * @param param 测试连接入参（mcpConfig / mcpConfigType / proxy 等）
     * @return 测试结果
     */
    public McpTestConnectionResultDTO testConnection(McpTestConnectionParamDTO param) {
        return toolRunnerFactory.testConnection(param);
    }

    // ============================================================
    // deleteDraft
    // ============================================================

    /**
     * 删除草稿工具（仅 DRAFT 可删）。
     *
     * @param num        工具业务编号
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 工具不存在 / 非草稿态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(String num, String operatorId) {
        Assert.notBlank(num, "工具业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.TOOL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Tool tool = requireByNum(num);
            // 领域动作：状态校验（仅 DRAFT）+ deleteByNum + 发 TOOL_DELETE_DRAFT
            tool.delete(operatorId);
            return null;
        });
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 经工厂按 num 加载聚合；不存在抛 {@link BizCode#TOOL_NOT_FOUND}。 */
    private Tool requireByNum(String num) {
        Tool tool = toolFactory.buildToolByNum(num);
        if (tool == null) {
            throw new BusinessException(BizCode.TOOL_NOT_FOUND.getCode(), "工具不存在 num=" + num);
        }
        return tool;
    }

    /** 按工具实际 creationMode 把编辑入参的形态字段 set 到聚合（其余形态字段忽略）。 */
    private void applyShapeFields(Tool tool, ToolUpdateParamDTO param) {
        switch (tool.getCreationMode()) {
            case REMOTE:
                if (StrUtil.isNotBlank(param.getMcpConfigType())) {
                    tool.setMcpConfigType(resolveMcpConfigType(param.getMcpConfigType()));
                }
                if (param.getMcpConfig() != null) {
                    tool.setMcpConfig(param.getMcpConfig());
                }
                applyProxyFields(tool, param);
                break;
            case API_PACKAGE:
                if (StrUtil.isNotBlank(param.getPackageMode())) {
                    tool.setPackageMode(resolvePackageMode(param.getPackageMode()));
                }
                if (param.getSourceFcToolNum() != null) {
                    tool.setSourceFcToolNum(param.getSourceFcToolNum());
                }
                if (param.getOpenApiSpec() != null) {
                    tool.setOpenApiSpec(param.getOpenApiSpec());
                }
                applyProxyFields(tool, param);
                break;
            case OPENAPI_SPEC:
                if (param.getOpenApiSpec() != null) {
                    tool.setOpenApiSpec(param.getOpenApiSpec());
                }
                break;
            case MANUAL:
                if (param.getBaseUrl() != null) {
                    tool.setBaseUrl(param.getBaseUrl());
                }
                if (param.getEndpoints() != null) {
                    tool.setEndpoints(toEndpoints(param.getEndpoints()));
                }
                break;
            default:
                // 不应发生：创建时已校验组合合法
        }
    }

    /** 代理字段（proxyEnabled / proxyHeaders）的编辑落入。 */
    private void applyProxyFields(Tool tool, ToolUpdateParamDTO param) {
        if (param.getProxyEnabled() != null) {
            tool.setProxyEnabled(param.getProxyEnabled());
        }
        if (param.getProxyHeaders() != null) {
            tool.setProxyHeaders(toProxyHeaders(param.getProxyHeaders()));
        }
    }

    /** 解析工具类型字符串，非法抛 {@link BizCode#TOOL_PARAM_INVALID}。 */
    private static ToolType resolveType(String type) {
        Assert.notBlank(type, "工具类型不能为空");
        try {
            return ToolType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.TOOL_PARAM_INVALID.getCode(), "不支持的工具类型：" + type);
        }
    }

    /** 解析创建方式字符串，非法抛 {@link BizCode#TOOL_PARAM_INVALID}。 */
    private static CreationMode resolveCreationMode(String mode) {
        Assert.notBlank(mode, "工具创建方式不能为空");
        try {
            return CreationMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.TOOL_PARAM_INVALID.getCode(), "不支持的创建方式：" + mode);
        }
    }

    /** 解析 MCP 配置子类型（可空），非法抛 {@link BizCode#TOOL_MCP_CONFIG_INVALID}。 */
    private static McpConfigType resolveMcpConfigType(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return McpConfigType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.TOOL_MCP_CONFIG_INVALID.getCode(), "不支持的 MCP 配置子类型：" + value);
        }
    }

    /** 解析打包方式（可空），非法抛 {@link BizCode#TOOL_PACKAGE_CONFIG_INVALID}。 */
    private static PackageMode resolvePackageMode(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return PackageMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.TOOL_PACKAGE_CONFIG_INVALID.getCode(), "不支持的打包方式：" + value);
        }
    }

    /** ProxyHeaderDTO 列表 → 值对象列表。 */
    private static List<ProxyHeader> toProxyHeaders(List<ProxyHeaderDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream()
                .map(d -> ProxyHeader.builder()
                        .name(d.getName())
                        .value(d.getValue())
                        .description(d.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** ApiEndpointDTO 列表 → 值对象列表（含 query / path / header 嵌套）。 */
    private static List<ApiEndpoint> toEndpoints(List<ApiEndpointDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream()
                .map(d -> ApiEndpoint.builder()
                        .method(resolveHttpMethod(d.getMethod()))
                        .path(d.getPath())
                        .description(d.getDescription())
                        .queryParams(toParams(d.getQueryParams()))
                        .pathParams(toParams(d.getPathParams()))
                        .headers(toHeaders(d.getHeaders()))
                        .build())
                .collect(Collectors.toList());
    }

    /** ApiParamDTO 列表 → 值对象列表。 */
    private static List<ApiParam> toParams(List<ApiParamDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream()
                .map(d -> ApiParam.builder()
                        .name(d.getName())
                        .type(resolveParamType(d.getType()))
                        .defaultValue(d.getDefaultValue())
                        .description(d.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** ApiHeaderDTO 列表 → 值对象列表。 */
    private static List<ApiHeader> toHeaders(List<ApiHeaderDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream()
                .map(d -> ApiHeader.builder()
                        .name(d.getName())
                        .defaultValue(d.getDefaultValue())
                        .description(d.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** 解析端点 HTTP 方法，非法抛 {@link BizCode#TOOL_ENDPOINT_INVALID}。 */
    private static HttpMethod resolveHttpMethod(String value) {
        Assert.notBlank(value, "端点请求方式不能为空");
        try {
            return HttpMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.TOOL_ENDPOINT_INVALID.getCode(), "不支持的请求方式：" + value);
        }
    }

    /** 解析端点参数类型，非法抛 {@link BizCode#TOOL_ENDPOINT_INVALID}。 */
    private static ApiParamType resolveParamType(String value) {
        Assert.notBlank(value, "端点参数类型不能为空");
        try {
            return ApiParamType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.TOOL_ENDPOINT_INVALID.getCode(), "不支持的参数类型：" + value);
        }
    }

    /**
     * 抢用例级分布式锁后执行编排；样板代码统一收口（参照 {@code SandboxCommandService}）。
     * 锁内再开事务（方法级 {@code @Transactional}），保证「先锁后事务」。
     *
     * @param lockKey 锁键（已含业务维度后缀）
     * @param action  临界区操作
     * @param <T>     返回类型
     * @return action 返回值
     * @throws BusinessException 抢锁失败（{@link BizCode#CONFLICT}）或线程中断
     */
    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "工具操作被中断");
        }
        if (!acquired) {
            log.warn("tool command lock busy key={}", lockKey);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "工具正在处理中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
