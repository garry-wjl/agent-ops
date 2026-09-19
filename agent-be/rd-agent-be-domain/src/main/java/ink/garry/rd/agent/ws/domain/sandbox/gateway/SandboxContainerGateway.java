package ink.garry.rd.agent.ws.domain.sandbox.gateway;

import java.math.BigDecimal;

/**
 * 远程沙箱容器生命周期网关（OpenSandbox 或 Mock）。
 * <p>
 * 领域/应用只依赖本接口，便于单测与本地 {@code sandbox.mock=true} 不连真实网关。
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
}
