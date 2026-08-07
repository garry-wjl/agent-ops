package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建沙箱入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>workspaceNum 由 adapter 从当前空间上下文取得后传入；type 本期固定 CODE。
 */
@Data
public class SandboxCreateParamDTO {

    /** 归属工作空间业务编号（必填）。 */
    private String workspaceNum;

    /** 沙箱名称（必填，1~64 字符）。 */
    private String name;

    /** 沙箱类型（本期固定 CODE）。 */
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
