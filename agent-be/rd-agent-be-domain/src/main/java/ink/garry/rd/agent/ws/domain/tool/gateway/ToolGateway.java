package ink.garry.rd.agent.ws.domain.tool.gateway;

import ink.garry.rd.agent.ws.domain.tool.valueobject.EndpointMeta;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;

/**
 * 工具聚合业务网关。
 * <p>
 * 为<b>领域对象</b>提供工具服务（业务编码生成、领域规则执行所需的解析辅助）；
 * 不承担应用层读查询。实现位于 infra（{@code ToolGatewayImpl}）。
 */
public interface ToolGateway {

    /**
     * 生成工具业务编号。
     * <p>
     * 由 {@code Tool.save} 在 num 为空时调用；前缀按类型区分：MCP→{@code MCP}，
     * FUNCTION_CALL→{@code FC}（复用统一 BizNumGenerator，实现方保证全局唯一）。
     *
     * @param type 工具类型，决定编号前缀
     * @return 全局唯一的工具编号（如 {@code MCP202606091530001}）
     */
    String generateToolNum(ToolType type);

    /**
     * 解析 OpenAPI / Swagger 文档为端点元数据。
     * <p>
     * 服务 {@code Tool.publish} —— OpenAPI 形态工具发布时解析端点数与每端点 path/method/summary
     * 落库，供列表 / 详情展示。属"领域规则执行所需的解析辅助"，由 infra 实现。
     *
     * @param openApiSpec OpenAPI 3.x / Swagger 2.0 JSON 原文
     * @return 端点元数据；解析失败由实现方抛业务异常
     */
    EndpointMeta parseOpenApi(String openApiSpec);
}
