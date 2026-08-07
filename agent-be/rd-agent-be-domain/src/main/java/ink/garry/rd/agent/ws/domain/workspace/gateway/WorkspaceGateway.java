package ink.garry.rd.agent.ws.domain.workspace.gateway;

/**
 * 工作空间业务编号生成网关。
 * <p>
 * 业务编号与数据库自增主键解耦，跨聚合引用统一使用业务编号；实现位于 infra。
 */
public interface WorkspaceGateway {

    /**
     * 生成工作空间业务编号（前缀 WS-）。
     *
     * @return 形如 WS-xxxxxxxx
     */
    String generateWorkspaceNum();
}
