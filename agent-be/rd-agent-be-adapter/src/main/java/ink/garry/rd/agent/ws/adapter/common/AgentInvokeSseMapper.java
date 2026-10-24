package ink.garry.rd.agent.ws.adapter.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunStreamEvent;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 将 {@link AgentInvokeFrame} / {@link AgentRunStreamEvent} 转为 SSE。
 */
@Slf4j
public final class AgentInvokeSseMapper {

    private AgentInvokeSseMapper() {
    }

    /**
     * @param frames Flux 统一帧
     * @param objectMapper JSON
     * @return SSE 字符串流
     */
    public static Flux<ServerSentEvent<String>> toSse(Flux<AgentInvokeFrame> frames,
                                                      ObjectMapper objectMapper) {
        if (frames == null) {
            return Flux.empty();
        }
        return frames.map(frame -> toEvent(frame, objectMapper));
    }

    /**
     * 可回放 run 事件 → SSE（带 id，供 Last-Event-ID 续传）。
     *
     * @param events run 事件流
     * @return SSE 字符串流
     */
    public static Flux<ServerSentEvent<String>> toSseFromRun(Flux<AgentRunStreamEvent> events) {
        if (events == null) {
            return Flux.empty();
        }
        return events.map(AgentInvokeSseMapper::toRunEvent);
    }

    private static ServerSentEvent<String> toRunEvent(AgentRunStreamEvent ev) {
        ServerSentEvent.Builder<String> b = ServerSentEvent.<String>builder()
                .id(ev.getId())
                .data(ev.getData() == null ? "{}" : ev.getData());
        if (ev.getEvent() != null && !ev.getEvent().isBlank()) {
            b.event(ev.getEvent());
        }
        return b.build();
    }

    private static ServerSentEvent<String> toEvent(AgentInvokeFrame frame, ObjectMapper objectMapper) {
        try {
            if (frame.isSandboxStatus()) {
                SandboxStatusEvent status = frame.getSandboxStatus();
                return ServerSentEvent.<String>builder()
                        .event(SandboxStatusEvent.SSE_EVENT)
                        .data(objectMapper.writeValueAsString(status))
                        .build();
            }
            if (frame.isUserInputRequired()) {
                return ServerSentEvent.<String>builder()
                        .event(frame.getSseEventName())
                        .data(objectMapper.writeValueAsString(frame.getUserInputRequired()))
                        .build();
            }
            if (frame.isUserInputStatus()) {
                return ServerSentEvent.<String>builder()
                        .event(frame.getSseEventName())
                        .data(objectMapper.writeValueAsString(frame.getUserInputStatusPayload()))
                        .build();
            }
            Object payload = frame.isScopeAgentEvent()
                    ? frame.getScopeAgentEvent()
                    : frame.getAgentEvent();
            JsonNode root = objectMapper.valueToTree(payload);
            if (frame.isAgentEvent()) {
                SseEventTransformer.transformFormatJsonResults(root);
            }
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(root))
                    .build();
        } catch (Exception e) {
            log.error("SSE 事件变换失败", e);
            return ServerSentEvent.<String>builder().data("{}").build();
        }
    }
}
