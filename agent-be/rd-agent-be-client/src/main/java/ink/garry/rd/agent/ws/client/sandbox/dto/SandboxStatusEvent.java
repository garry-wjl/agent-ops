package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话沙箱状态 SSE 载荷（{@code event: Sandbox.Status}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxStatusEvent {

    /** SSE event 名，固定此前缀便于前端识别。 */
    public static final String SSE_EVENT = "Sandbox.Status";

    /** 会话编号。 */
    private String sessionNum;

    /** 阶段：IDLE / PREPARING / READY / FAILED。 */
    private String phase;

    /** Ready 时的容器实例 id。 */
    private String instanceId;

    /** 失败原因（可空）。 */
    private String message;

    /** 事件时间 epoch millis。 */
    private long ts;
}
