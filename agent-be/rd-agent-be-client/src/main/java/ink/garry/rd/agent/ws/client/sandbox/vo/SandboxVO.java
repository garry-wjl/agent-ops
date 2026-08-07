package ink.garry.rd.agent.ws.client.sandbox.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 沙箱 Vo（列表项 / 命令返回，adapter 层出参）。
 * <p>
 * 承载沙箱全字段快照，由 application 的 {@code SandboxDTO} 经 {@code SandboxVoAssembler} 转换而来。
 * status 为枚举字符串：{@code DRAFT / INITIALIZED / ONLINE / OFFLINE / FAILED}，前端映射中文与色彩。
 */
@Data
public class SandboxVO {

    /** 业务编号 SBX+yyyyMMddHHmm+4位序号。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 沙箱名称。 */
    private String name;

    /** 沙箱类型：CODE。 */
    private String type;

    /** CPU 核数（0.5 步进）。 */
    private BigDecimal cpu;

    /** 内存大小（MB）。 */
    private Integer memoryMb;

    /** 容器存活时间（分钟）。 */
    private Integer aliveMinutes;

    /** 状态：DRAFT / INITIALIZED / ONLINE / OFFLINE / FAILED。 */
    private String status;

    /** 备注。 */
    private String remark;

    /** OpenSandbox 容器实例 id；草稿 / 失败态为空。 */
    private String sandboxInstanceId;

    /** 创建人工号。 */
    private String createNo;

    /** 更新人工号。 */
    private String updateNo;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
