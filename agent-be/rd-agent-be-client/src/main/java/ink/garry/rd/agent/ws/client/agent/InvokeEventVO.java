package ink.garry.rd.agent.ws.client.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SSE 事件载荷 VO（前端 EventSource data 字段对应）
 * <p>
 * 5 类事件：message.delta / step.start / step.end / final / error。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvokeEventVO {
    private String event;
    private Map<String, Object> data;
}
