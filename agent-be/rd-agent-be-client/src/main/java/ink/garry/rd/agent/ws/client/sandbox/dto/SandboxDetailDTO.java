package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙箱详情 DTO（application 层边界）。
 * <p>
 * 本期详情即沙箱全字段 + 当前状态（无独立的状态时间线表，前端时间线由审计日志侧承载）；
 * 以嵌套 {@link SandboxDTO} 承载，预留后续扩展（如运行指标）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SandboxDetailDTO {

    /** 沙箱全字段快照 */
    private SandboxDTO sandbox;
}
