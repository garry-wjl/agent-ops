package ink.garry.rd.agent.ws.client.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件载荷（client 层 adapter ↔ application 之间的传输契约）。
 * <p>
 * {@code name} 对应 SSE {@code event:} 字段（事件类型 code）；
 * {@code data} 由调用方序列化为 JSON 后通过 {@code data:} 字段输出。
 * <p>
 * 该类用于把 application 层的领域事件流（如 {@code PlatformEvent}）拍平成
 * adapter 可直接消费的纯数据载体，保持 adapter 不引用 domain 类型（§6.1）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsePayload {

    /** SSE 事件类型 name（对应前端 EventSource 的 event 字段） */
    private String name;

    /** SSE 事件数据载荷，由 adapter 序列化为 JSON */
    private Object data;
}
