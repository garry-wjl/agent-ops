package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Agent 内嵌维护的沙箱规格（只存元数据，不起真实容器）。
 */
@Data
public class SandboxSpecParam {

    /**
     * 是否启用沙箱。
     * <p>{@code false}：当前快照解绑 {@code sandboxRef}；{@code true}/null：按规格 upsert。
     */
    private Boolean enabled;

    /** 展示名；空则自动生成 */
    private String name;

    /** CPU 核数（0.5 步进） */
    private BigDecimal cpu;

    /** 内存 MB */
    private Integer memoryMb;

    /** 容器存活分钟 */
    private Integer aliveMinutes;

    /** 最大并发会话实例 */
    private Integer maxConcurrent;

    /** 会话空闲回收等待分钟 */
    private Integer sessionIdleTtlMinutes;

    /**
     * 创建容器时注入的环境变量。
     * <p>key 须匹配 {@code [A-Za-z_][A-Za-z0-9_]*}；最多 50 条。
     */
    private Map<String, String> env;

    /** 备注 */
    private String remark;
}
