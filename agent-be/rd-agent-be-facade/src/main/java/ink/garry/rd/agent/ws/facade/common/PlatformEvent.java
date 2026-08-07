package ink.garry.rd.agent.ws.facade.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台事件载荷。
 * 用于流式响应（SSE / WebSocket）中下行给前端的事件结构，
 * 与 {@link EventType} 配合表达消息增量、步骤起止、最终结果与异常等场景。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlatformEvent {
    /** 事件类型，详见 {@link EventType} */
    private EventType type;
    /** 事件载荷数据，结构随 type 而定 */
    private Object data;
}
