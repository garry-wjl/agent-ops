package ink.garry.rd.agent.ws.domain.prompt.gateway;

/**
 * Prompt 聚合业务网关。
 * <p>
 * 只为<b>领域对象</b>提供工具服务（业务编码生成）；不承担应用层读查询。
 * 实现位于 infra（{@code PromptGatewayImpl}，复用统一 {@code BizNumGenerator}）。
 */
public interface PromptGateway {

    /**
     * 生成 Prompt 业务编号（前缀 PRM）。
     * <p>
     * 由 {@code Prompt.save} 在 num 为空时调用；实现方需保证全局唯一
     * （如 {@code PRM + yyyyMMddHHmm + 4 位序号}）。
     *
     * @return 全局唯一的 Prompt 编号（如 {@code PRM2026061012340001}）
     */
    String generatePromptNum();
}
