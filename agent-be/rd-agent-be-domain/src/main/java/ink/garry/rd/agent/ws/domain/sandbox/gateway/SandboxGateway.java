package ink.garry.rd.agent.ws.domain.sandbox.gateway;

/**
 * 沙箱业务编号生成网关。
 * <p>
 * 业务编号与数据库自增主键解耦，跨聚合引用统一使用业务编号；实现位于 infra。
 * <p>
 * <b>边界说明</b>：sandbox 领域仅有本网关。OpenSandbox 容器的创建 / 健康检查 / 销毁 / 存活查询
 * 不属于领域出站协作能力，而是应用层职责 —— 由 {@code application.sandbox.SandboxRunner}
 * 直接使用 infra 工具类 {@code SandboxClient} 完成，不在 domain 定义运行时网关。
 */
public interface SandboxGateway {

    /**
     * 生成沙箱业务编号（前缀 SBX，复用统一 BizNumGenerator）。
     *
     * @return 形如 SBX+yyyyMMddHHmm+4 位序号
     */
    String generateSandboxNum();
}
