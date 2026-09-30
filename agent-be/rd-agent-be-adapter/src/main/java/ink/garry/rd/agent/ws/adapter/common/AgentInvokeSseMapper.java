package ink.garry.rd.agent.ws.adapter.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 将 {@link AgentInvokeFrame} 转为 SSE：Agent Event 默认 data 帧；沙箱状态带 {@code event: Sandbox.Status}。
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

    private static ServerSentEvent<String> toEvent(AgentInvokeFrame frame, ObjectMapper objectMapper) {
        try {
            if (frame.isSandboxStatus()) {
                SandboxStatusEvent status = frame.getSandboxStatus();
                return ServerSentEvent.<String>builder()
                        .event(SandboxStatusEvent.SSE_EVENT)
                        .data(objectMapper.writeValueAsString(status))
                        .build();
            }
            JsonNode root = objectMapper.valueToTree(frame.getAgentEvent());
            SseEventTransformer.transformFormatJsonResults(root);
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(root))
                    .build();
        } catch (Exception e) {
            log.error("SSE 事件变换失败", e);
            return ServerSentEvent.<String>builder().data("{}").build();
        }
    }
}
