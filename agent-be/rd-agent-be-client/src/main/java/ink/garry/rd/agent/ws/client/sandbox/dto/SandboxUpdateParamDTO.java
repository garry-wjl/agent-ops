package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 编辑沙箱入参 DTO（application 层边界）。
 * <p>
 * 应用层按当前状态决定写入哪些字段：草稿 / 失败态可改全部规格字段，其余态仅备注可改
 * （详见沙箱管理技术方案 §4.2.3）。
 */
@Data
public class SandboxUpdateParamDTO {

    /** 沙箱业务编号（必填）。 */
    private String num;

    /** 沙箱名称（草稿 / 失败态可改）。 */
    private String name;

    /** CPU 核数（草稿 / 失败态可改）。 */
    private BigDecimal cpu;

    /** 内存大小（MB，草稿 / 失败态可改）。 */
    private Integer memoryMb;

    /** 容器存活时间（分钟，草稿 / 失败态可改）。 */
    private Integer aliveMinutes;

    /** 备注（任意非删除态可改，≤100 字）。 */
    private String remark;
}
