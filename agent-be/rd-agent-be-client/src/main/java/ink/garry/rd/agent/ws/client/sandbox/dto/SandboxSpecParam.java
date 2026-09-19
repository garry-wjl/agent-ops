package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.Data;

import java.math.BigDecimal;

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

    /** 备注 */
    private String remark;
}
