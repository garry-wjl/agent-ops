package ink.garry.rd.agent.ws.adapter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AgentInvokeSseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsSandboxStatusWithNamedEvent() {
        SandboxStatusEvent status = SandboxStatusEvent.builder()
                .sessionNum("SES-1")
                .phase("PREPARING")
                .ts(1L)
                .build();

        StepVerifier.create(AgentInvokeSseMapper.toSse(
                        Flux.just(AgentInvokeFrame.sandboxStatus(status)), objectMapper))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("Sandbox.Status");
                    assertThat(sse.data()).contains("PREPARING").contains("SES-1");
                })
                .verifyComplete();
    }

    @Test
    void mapsAgentEventAsDefaultDataFrame() {
        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder().text("hi").build()))
                .build();
        Event event = new Event(EventType.REASONING, msg, false);

        List<ServerSentEvent<String>> list = AgentInvokeSseMapper.toSse(
                        Flux.just(AgentInvokeFrame.agent(event)), objectMapper)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(list).hasSize(1);
        assertThat(list.get(0).event()).isNull();
        assertThat(list.get(0).data()).contains("REASONING");
    }
}
