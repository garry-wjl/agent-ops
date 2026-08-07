package ink.garry.rd.agent.ws.infra.common.client.sandbox;

import com.alibaba.opensandbox.sandbox.Sandbox;
import com.alibaba.opensandbox.sandbox.SandboxManager;
import com.alibaba.opensandbox.sandbox.config.ConnectionConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

/**
 * OpenSandbox SDK 薄封装：收口连接配置与沙箱生命周期原子操作。
 * <p>
 * 职责边界：<b>只做 SDK 调用</b>——创建 / 连接 / 续期 / 销毁单个远程容器，
 * 不持有任何会话语义、不碰 Redis。会话↔沙箱的复用映射与并发控制由应用层
 * {@code SandboxService} 负责。
 * <p>
 * 关键语义：
 * <ul>
 *   <li>{@link ConnectionConfig} 内含 OkHttp 连接池，构建一次后全局复用，{@link #init()}
 *       建、{@link #destroy()} 关；</li>
 *   <li>{@link Sandbox#close()} 仅释放该句柄的 HTTP 客户端，<b>不会销毁远程容器</b>——
 *       故 {@link #create()} 拿到 id 后即可安全关闭句柄，容器按 TTL 存活待复用；</li>
 *   <li>{@link #create()} 调用 {@code manualCleanup()} 关闭 SDK 自动清理，防止本进程
 *       退出 / pod 重启时误杀仍被其它副本复用的在用容器。</li>
 * </ul>
 */
@Slf4j
@Component
public class SandboxClient {

    @Resource
    private SandboxProperties properties;

    /** 全局复用的连接配置（含 OkHttp 连接池）。 */
    private ConnectionConfig connectionConfig;

    /** 全局复用的沙箱管控客户端（list / renew / kill）。 */
    private SandboxManager sandboxManager;

    @PostConstruct
    public void init() {
        this.connectionConfig = ConnectionConfig.builder()
                .apiKey(properties.getApiKey())
                .domain(properties.getDomain())
                .protocol(properties.getProtocol())
                .requestTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .useServerProxy(Boolean.TRUE)
                .build();
        this.sandboxManager = SandboxManager.builder()
                .connectionConfig(connectionConfig)
                .build();
        log.info("SandboxClient initialized, domain={}, image={}, ttlMinutes={}",
                properties.getDomain(), properties.getImage(), properties.getTtlMinutes());
    }

    @PreDestroy
    public void destroy() {
        if (sandboxManager != null) {
            try {
                sandboxManager.close();
            } catch (Exception e) {
                log.warn("close SandboxManager failed", e);
            }
        }
    }

    /**
     * 用固定镜像新建一个沙箱容器并返回其 id。
     * <p>
     * 句柄在 try-with-resources 内关闭（仅释放 HTTP 客户端，不影响远程容器存活）。
     *
     * @return 新建容器的 sandboxId
     */
    public String create(Map<String, String> env) {
        try (Sandbox sandbox = Sandbox.builder()
                .image(properties.getImage())
                .env(env)
                .timeout(ttl())
                .manualCleanup()
                .connectionConfig(connectionConfig)
                .build()) {
            String sandboxId = sandbox.getId();
            log.info("sandbox created, id={}", sandboxId);
            return sandboxId;
        }
    }

    /**
     * 按规格新建一个沙箱容器并返回其 id（沙箱资产管理用，区别于 Agent 运行时的 {@link #create(Map)}）。
     * <p>
     * 在固定镜像基础上，按入参设置 CPU / 内存资源限额与容器存活上限 timeout=aliveMinutes：
     * <ul>
     *   <li>CPU → millicores 字符串：核数 × 1000，如 1.5 核 → {@code "1500m"}；</li>
     *   <li>内存 → Mi 字符串：如 2048 MB → {@code "2048Mi"}；</li>
     *   <li>timeout → {@code Duration.ofMinutes(aliveMinutes)}，作为容器存活上限，到期由 OpenSandbox 回收。</li>
     * </ul>
     * 句柄在 try-with-resources 内关闭（仅释放 HTTP 客户端，不影响远程容器存活）。
     *
     * @param cpu          CPU 核数（0.5 步进，如 1.5）
     * @param memoryMb     内存大小（MB）
     * @param aliveMinutes 容器存活时间（分钟）
     * @return 新建容器的 sandboxId
     */
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes) {
        String cpuMillis = cpu.multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP).toPlainString() + "m";
        String memory = memoryMb + "Mi";
        try (Sandbox sandbox = Sandbox.builder()
                .image(properties.getImage())
                .resource(Map.of("cpu", cpuMillis, "memory", memory))
                .timeout(Duration.ofMinutes(aliveMinutes))
                .manualCleanup()
                .connectionConfig(connectionConfig)
                .build()) {
            String sandboxId = sandbox.getId();
            log.info("sandbox created by spec, id={}, cpu={}, memory={}, aliveMinutes={}",
                    sandboxId, cpuMillis, memory, aliveMinutes);
            return sandboxId;
        }
    }

    /**
     * 连接到一个已存在的沙箱容器，返回可执行命令 / 读写文件的句柄。
     * <p>
     * 跳过健康检查以省一次往返；句柄用完后由调用方 {@link Sandbox#close()} 释放。
     *
     * @param sandboxId 目标容器 id
     * @return 指向该容器的句柄
     */
    public Sandbox connect(String sandboxId) {
        return Sandbox.connector()
                .sandboxId(sandboxId)
                .connectionConfig(connectionConfig)
                .skipHealthCheck(true)
                .connect();
    }

    /**
     * 续期容器 TTL，重置为配置的存活窗口（会话活动时调用，实现滑动过期）。
     *
     * @param sandboxId 目标容器 id
     */
    public void renew(String sandboxId) {
        sandboxManager.renewSandbox(sandboxId, ttl());
    }

    /**
     * 显式销毁容器（会话结束 / 释放时调用）。
     *
     * @param sandboxId 目标容器 id
     */
    public void kill(String sandboxId) {
        sandboxManager.killSandbox(sandboxId);
        log.info("sandbox killed, id={}", sandboxId);
    }

    /**
     * 查询容器是否仍存活（沙箱资产脏态对账用）。
     * <p>
     * 经 {@link SandboxManager#getSandboxInfo(String)} 探测：能取到信息即视为存活；
     * 抛异常（容器已不存在 / 网络异常）一律视为不存活，由对账逻辑据此把平台状态校正为下线。
     *
     * @param sandboxId 目标容器 id
     * @return 存活返回 true；不存在 / 异常返回 false
     */
    public boolean isAlive(String sandboxId) {
        try {
            sandboxManager.getSandboxInfo(sandboxId);
            return true;
        } catch (Exception e) {
            log.warn("sandbox isAlive probe failed, treat as not alive, id={}, reason={}",
                    sandboxId, e.getMessage());
            return false;
        }
    }

    /** 配置的容器存活窗口。 */
    private Duration ttl() {
        return Duration.ofMinutes(properties.getTtlMinutes());
    }
}
