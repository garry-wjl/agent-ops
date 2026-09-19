package ink.garry.rd.agent.ws.domain.sandbox.gateway;

import java.math.BigDecimal;

/**
 * 远程沙箱容器生命周期网关（OpenSandbox 或 Mock）。
 * <p>
 * 领域/应用只依赖本接口，便于本地 Docker（{@code sandbox.mock=true}）与生产 OpenSandbox 切换。
 */
public interface SandboxContainerGateway {

    /**
     * 按规格创建远程容器。
     *
     * @param cpu          CPU 核数
     * @param memoryMb     内存 MB
     * @param aliveMinutes 容器 TTL 分钟
     * @return 远程 instanceId
     */
    String create(BigDecimal cpu, int memoryMb, int aliveMinutes);

    /**
     * 是否把工作空间按会话挂到独立 OSS 前缀。
     * <p>
     * 开启后热池不能复用：空闲容器没有会话 subPath，领走会串会话。
     *
     * @return 默认关闭
     */
    default boolean isolatesWorkspaceBySession() {
        return false;
    }

    /**
     * 创建并挂上会话工作空间。未开启隔离时忽略 subPath，行为与 {@link #create(BigDecimal, int, int)} 相同。
     *
     * @param workspaceSubPath {@code workspaceNum/agentNum/sessionNum}，可空
     * @return 远程 instanceId
     */
    default String create(BigDecimal cpu, int memoryMb, int aliveMinutes, String workspaceSubPath) {
        return create(cpu, memoryMb, aliveMinutes);
    }

    /**
     * 销毁远程容器。
     *
     * @param instanceId 远程 id
     */
    void kill(String instanceId);

    /**
     * 探活。
     *
     * @param instanceId 远程 id
     * @return 是否存活
     */
    boolean isAlive(String instanceId);

    /**
     * 滑动续期远程容器 TTL（会话活动时调用）。
     * <p>
     * 本地 Docker 等不支持续期的实现可为 no-op。
     *
     * @param instanceId   远程 id
     * @param aliveMinutes 续期后的存活窗口（分钟）
     */
    default void renew(String instanceId, int aliveMinutes) {
        // 默认不支持续期
    }
}
