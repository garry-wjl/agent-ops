package ink.garry.rd.agent.ws.adapter.common;

import static org.assertj.core.api.Assertions.assertThat;

import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunStreamEvent;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 续传 SSE 映射：{@code id=runId:seq} 与 event 名必须透传给客户端。
 */
class AgentInvokeSseMapperRunTest {

    @Test
    @DisplayName("toSseFromRun 保留 id / event / data，供 Last-Event-ID 续传")
    void toSseFromRun_preservesIdEventData() {
        List<ServerSentEvent<String>> sse = AgentInvokeSseMapper.toSseFromRun(Flux.just(
                        AgentRunStreamEvent.builder()
                                .id("RUNx:1")
                                .seq(1)
                                .event("run_started")
                                .data("{\"runId\":\"RUNx\"}")
                                .build(),
                        AgentRunStreamEvent.builder()
                                .id("RUNx:2")
                                .seq(2)
                                .event(SandboxStatusEvent.SSE_EVENT)
                                .data("{\"phase\":\"READY\"}")
                                .build(),
                        AgentRunStreamEvent.builder()
                                .id("RUNx:3")
                                .seq(3)
                                .event(null)
                                .data("{\"type\":\"REASONING\"}")
                                .build()))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(sse).hasSize(3);
        assertThat(sse.get(0).id()).isEqualTo("RUNx:1");
        assertThat(sse.get(0).event()).isEqualTo("run_started");
        assertThat(sse.get(0).data()).contains("RUNx");

        assertThat(sse.get(1).id()).isEqualTo("RUNx:2");
        assertThat(sse.get(1).event()).isEqualTo(SandboxStatusEvent.SSE_EVENT);

        assertThat(sse.get(2).id()).isEqualTo("RUNx:3");
        assertThat(sse.get(2).event()).isNull();
        assertThat(sse.get(2).data()).contains("REASONING");
    }
}
