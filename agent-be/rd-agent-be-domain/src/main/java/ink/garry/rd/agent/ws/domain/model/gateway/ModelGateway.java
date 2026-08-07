package ink.garry.rd.agent.ws.domain.model.gateway;

/**
 * 模型业务编号生成网关。
 * <p>
 * 业务编号与数据库自增主键解耦，跨聚合引用统一使用业务编号；实现位于 infra
 * （{@code ModelGatewayImpl}，复用统一 {@code BizNumGenerator}）。
 * <p>
 * <b>边界说明</b>：API Key 的加解密 / 掩码<b>不</b>放本网关 —— 领域内 {@code Model.apiKey}
 * 始终持有明文，密文 ↔ 明文转换由 {@code ModelRepositoryImpl} 在 Entity ↔ 领域对象映射时调用
 * {@code SecretCipher} 完成（持久化实现细节）。本网关仅承担 num 生成，保持 domain 仅依赖 facade。
 */
public interface ModelGateway {

    /**
     * 生成模型业务编号（前缀 MDL，复用统一 BizNumGenerator）。
     *
     * @return 形如 {@code MDL+yyyyMMddHHmm+4 位序号}（如 {@code MDL2026061015300001}）
     */
    String generateModelNum();
}
