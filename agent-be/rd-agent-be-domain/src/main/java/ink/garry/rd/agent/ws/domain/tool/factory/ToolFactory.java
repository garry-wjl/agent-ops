package ink.garry.rd.agent.ws.domain.tool.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.tool.Tool;
import ink.garry.rd.agent.ws.domain.tool.gateway.ToolGateway;
import ink.garry.rd.agent.ws.domain.tool.repository.ToolRepository;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiEndpoint;
import ink.garry.rd.agent.ws.domain.tool.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.McpConfigType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.PackageMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ProxyHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool 领域工厂。
 * <p>
 * 提供 2 个 build 方法，覆盖 Tool 的两种构建场景（与 {@code SandboxFactory} /
 * {@code SkillFactory} 风格一致）：
 * <ul>
 *   <li>{@link #buildTool}：用必要 + 各形态字段构造一条新的 Tool（未落库）；status 由
 *       {@link Tool#save(String)} 在为空时兜底为 {@code DRAFT}，num 由 save 在为空时经网关生成。</li>
 *   <li>{@link #buildToolByNum}：按业务编号从仓储加载 Tool 并装配依赖。</li>
 * </ul>
 * <p>
 * <b>装配方式</b>：本类 {@code @Component} 受 Spring 管理；依赖 {@code @Resource} 字段注入。
 * 创建出的 Tool 由工厂手动 wire 所需的 Repository / Gateway / EventPublisher，
 * 使调用方可直接执行业务方法（save / publish / unpublish / republish / delete）。
 * <p>
 * <b>workspaceNum</b>：作为构建必填字段由应用层传入（应用层从 WorkspaceContextHolder 解析），
 * 不在工厂内访问 infra 上下文，保持 domain 仅依赖 facade。
 */
@Component
public class ToolFactory {

    @Resource
    private ToolRepository toolRepository;
    @Resource
    private ToolGateway toolGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用必要 + 各形态字段构造一条新的 Tool 聚合（未落库）。
     * <p>
     * 仅接收创建期用户可填业务字段（含各形态专有字段）；status / num / endpointMeta / 审计字段
     * 不在此处赋值，由 {@link Tool#save(String)} 统一处理。调用方拿到返回的 Tool 后通常立即
     * 调用 {@link Tool#save(String)} 完成首次落库（草稿态）。
     *
     * @param workspaceNum    归属工作空间业务编号（应用层从上下文解析后传入）
     * @param name            工具名称
     * @param description     工具描述
     * @param type            工具类型
     * @param creationMode    创建方式
     * @param tags            标签（可空）
     * @param ownerUserId     负责人 / 创建人用户 ID
     * @param mcpConfigType   MCP 配置子类型（仅 MCP-REMOTE，其余传 null）
     * @param mcpConfig       MCP 配置 JSON 原文（仅 MCP-REMOTE，其余传 null）
     * @param proxyEnabled    是否启用代理（MCP 两形态，其余传 null）
     * @param proxyHeaders    透传请求头（MCP 两形态，其余传 null）
     * @param packageMode     打包方式（仅 MCP-API_PACKAGE，其余传 null）
     * @param sourceFcToolNum 来源 FC 工具 num（仅 EXISTING_API，其余传 null）
     * @param openApiSpec     OpenAPI 原文（OPENAPI_SPEC / OPENAPI_PASTE，其余传 null）
     * @param baseUrl         Base URL（仅 FC-MANUAL，其余传 null）
     * @param endpoints       端点列表（仅 FC-MANUAL，其余传 null）
     * @return 已装配完依赖、可直接 save 的 Tool 聚合
     */
    public Tool buildTool(String workspaceNum,
                          String name,
                          String description,
                          ToolType type,
                          CreationMode creationMode,
                          List<String> tags,
                          String ownerUserId,
                          McpConfigType mcpConfigType,
                          String mcpConfig,
                          Boolean proxyEnabled,
                          List<ProxyHeader> proxyHeaders,
                          PackageMode packageMode,
                          String sourceFcToolNum,
                          String openApiSpec,
                          String baseUrl,
                          List<ApiEndpoint> endpoints) {
        Assert.notBlank(workspaceNum, "归属工作空间编号不能为空");
        Assert.notBlank(name, "工具名称不能为空");
        // description 在草稿态允许为空，发布时由 Tool.publish() 中的 validateByShape() 统一校验
        Assert.notNull(type, "工具类型不能为空");
        Assert.notNull(creationMode, "工具创建方式不能为空");
        Assert.notBlank(ownerUserId, "工具负责人不能为空");

        return new Tool(workspaceNum, name, description, type, creationMode, tags, ownerUserId,
                mcpConfigType, mcpConfig, proxyEnabled, proxyHeaders,
                packageMode, sourceFcToolNum, openApiSpec, baseUrl, endpoints,
                toolRepository, toolGateway, domainEventPublisher);
    }

    /**
     * 按业务编号加载 Tool 并装配依赖（等价于 {@code toolRepository.findByNum(num)} + wire）。
     *
     * @param num 工具业务编号
     * @return 装配完依赖的 Tool 聚合；不存在时返回 {@code null}
     */
    public Tool buildToolByNum(String num) {
        Assert.notBlank(num, "工具业务编号不能为空");
        Tool tool = toolRepository.findByNum(num);
        if (tool == null) {
            return null;
        }
        wireTool(tool);
        return tool;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入 Tool 聚合根。 */
    private void wireTool(Tool tool) {
        tool.setToolRepository(this.toolRepository);
        tool.setToolGateway(this.toolGateway);
        tool.setDomainEventPublisher(this.domainEventPublisher);
    }
}
