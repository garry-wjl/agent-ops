package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * SSE 平台统一事件载荷（5 类共享）。
 * <p>
 * data 字段按 EventType 不同结构不同：
 *  - message.delta：{ content, index }
 *  - step.start：{ stepId, skillName, input, startedAt }
 *  - step.end：{ stepId, output, status, latencyMs, error? }
 *  - final：{ sessionNum, traceId, totalTokens, totalLatencyMs }
 *  - error：{ code, message, stepId? }
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlatformEvent {
    /** 事件类型，决定 data 字段结构 */
    private EventType type;
    /** 事件载荷，按 EventType 不同结构不同（见类级注释） */
    private Map<String, Object> data;
}
