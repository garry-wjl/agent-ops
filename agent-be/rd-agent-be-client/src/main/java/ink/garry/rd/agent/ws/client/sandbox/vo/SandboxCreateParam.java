package ink.garry.rd.agent.ws.client.sandbox.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建沙箱入参 Vo（adapter 层用，来自 HTTP 请求体）。
 * <p>
 * workspaceNum 由前端当前空间上下文（{@code X-Workspace-Num} 头）传入，亦可显式置于请求体；
 * adapter 经 {@code SandboxVoAssembler} 转 {@code SandboxCreateParamDTO} 后调用 application。
 * type 本期固定 CODE。
 */
@Data
public class SandboxCreateParam {

    /** 归属工作空间业务编号（必填）。 */
    private String workspaceNum;

    /** 沙箱名称（必填，1~64 字符）。 */
    private String name;

    /** 沙箱类型（本期固定 CODE，可空兜底 CODE）。 */
    private String type;

    /** CPU 核数（必填，0.5 步进，区间 0.5~16）。 */
    private BigDecimal cpu;

    /** 内存大小（MB，必填，区间 128~65536）。 */
    private Integer memoryMb;

    /** 容器存活时间（分钟，必填，区间 1~1440）。 */
    private Integer aliveMinutes;

    /** 备注（可空，≤100 字）。 */
    private String remark;
}
