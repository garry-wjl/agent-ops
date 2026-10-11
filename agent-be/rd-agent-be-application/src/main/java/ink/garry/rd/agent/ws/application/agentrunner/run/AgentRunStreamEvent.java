package ink.garry.rd.agent.ws.application.agentrunner.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可回放的 SSE 业务帧（已序列化），供开放接口 / 调试台共用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunStreamEvent {

    /** SSE id：{@code {runId}:{seq}} */
    private String id;

    /** SSE event 名；空则走默认 data 帧 */
    private String event;

    /** SSE data（JSON 字符串） */
    private String data;

    /** 帧序号（同 run 内从 1 递增） */
    private long seq;
}
