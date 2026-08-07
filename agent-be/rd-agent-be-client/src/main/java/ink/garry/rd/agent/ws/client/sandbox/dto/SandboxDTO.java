package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 沙箱 DTO（列表项 / 命令返回，application 层边界）。
 * <p>承载沙箱全字段快照；adapter 由此转 {@code SandboxVO} 返回客户端。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SandboxDTO {

    /** 业务编号 SBX+yyyyMMddHHmm+4位序号 */
    private String num;

    /** 归属工作空间业务编号 */
    private String workspaceNum;

    /** 沙箱名称 */
    private String name;

    /** 沙箱类型：CODE */
    private String type;

    /** CPU 核数（0.5 步进） */
    private BigDecimal cpu;

    /** 内存大小（MB） */
    private Integer memoryMb;

    /** 容器存活时间（分钟） */
    private Integer aliveMinutes;

    /** 状态：DRAFT/INITIALIZED/ONLINE/OFFLINE/FAILED */
    private String status;

    /** 备注 */
    private String remark;

    /** OpenSandbox 容器实例 id；草稿 / 失败态为空 */
    private String sandboxInstanceId;

    /** 创建人工号 */
    private String createNo;

    /** 更新人工号 */
    private String updateNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
