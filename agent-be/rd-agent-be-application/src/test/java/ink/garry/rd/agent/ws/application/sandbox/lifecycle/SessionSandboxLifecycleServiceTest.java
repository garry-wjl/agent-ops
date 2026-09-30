package ink.garry.rd.agent.ws.application.sandbox.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Disposable;

@ExtendWith(MockitoExtension.class)
class SessionSandboxLifecycleServiceTest {

    @Mock
    private SandboxPoolService sandboxPoolService;

    @InjectMocks
    private SessionSandboxLifecycleService lifecycle;

    @BeforeEach
    void clear() {
        lifecycle.clear("SES-1");
    }

    @Test
    void ensureBoundSilent_doesNotEmitToSubscribers() {
        when(sandboxPoolService.ensureBound("SBX1", "SES-1", "op", "AGT1"))
                .thenReturn("inst-1");

        List<SandboxStatusEvent> received = new ArrayList<>();
        Disposable sub = lifecycle.subscribe("SES-1").subscribe(received::add);
        try {
            String id = lifecycle.ensureBoundSilent("SBX1", "SES-1", "op", "AGT1");

            assertThat(id).isEqualTo("inst-1");
            assertThat(lifecycle.phaseOf("SES-1")).isEqualTo(SessionSandboxPhase.READY);
            assertThat(lifecycle.readyInstanceId("SES-1")).isEqualTo("inst-1");
            assertThat(received).isEmpty();
        } finally {
            sub.dispose();
        }
    }

    @Test
    void ensureAliveOrRebindForUse_emitsPreparingThenReady() throws Exception {
        when(sandboxPoolService.ensureAliveOrRebind(
                eq("SES-1"), isNull(), eq("SBX1"), eq("AGT1"), eq("agent-exec")))
                .thenReturn("inst-2");

        List<SandboxStatusEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        Disposable sub = lifecycle.subscribe("SES-1").subscribe(e -> {
            received.add(e);
            latch.countDown();
        });
        try {
            String id = lifecycle.ensureAliveOrRebindForUse(
                    "SES-1", null, "SBX1", "AGT1", "agent-exec");
            assertThat(id).isEqualTo("inst-2");
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(2);
            assertThat(received.get(0).getPhase()).isEqualTo("PREPARING");
            assertThat(received.get(0).getSessionNum()).isEqualTo("SES-1");
            assertThat(received.get(1).getPhase()).isEqualTo("READY");
            assertThat(received.get(1).getInstanceId()).isEqualTo("inst-2");
        } finally {
            sub.dispose();
        }
    }

    @Test
    void ensureAliveOrRebindForUse_whenAlreadyReady_skipsPreparingEmit() {
        when(sandboxPoolService.ensureBound("SBX1", "SES-1", "op", "AGT1"))
                .thenReturn("inst-ready");
        lifecycle.ensureBoundSilent("SBX1", "SES-1", "op", "AGT1");

        when(sandboxPoolService.ensureAliveOrRebind(
                eq("SES-1"), eq("inst-ready"), eq("SBX1"), eq("AGT1"), eq("agent-exec")))
                .thenReturn("inst-ready");

        AtomicReference<SandboxStatusEvent> got = new AtomicReference<>();
        Disposable sub = lifecycle.subscribe("SES-1").subscribe(got::set);
        try {
            String id = lifecycle.ensureAliveOrRebindForUse(
                    "SES-1", "inst-ready", "SBX1", "AGT1", "agent-exec");

            assertThat(id).isEqualTo("inst-ready");
            assertThat(got.get()).isNull();
            verify(sandboxPoolService).ensureAliveOrRebind(
                    "SES-1", "inst-ready", "SBX1", "AGT1", "agent-exec");
        } finally {
            sub.dispose();
        }
    }

    @Test
    void ensureAliveOrRebindForUse_failure_emitsFailed() throws Exception {
        when(sandboxPoolService.ensureAliveOrRebind(
                anyString(), isNull(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("create timeout"));

        List<SandboxStatusEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        Disposable sub = lifecycle.subscribe("SES-1").subscribe(e -> {
            received.add(e);
            latch.countDown();
        });
        try {
            assertThatThrownBy(() ->
                    lifecycle.ensureAliveOrRebindForUse(
                            "SES-1", null, "SBX1", "AGT1", "agent-exec"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("create timeout");
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get(0).getPhase()).isEqualTo("PREPARING");
            assertThat(received.get(1).getPhase()).isEqualTo("FAILED");
            assertThat(received.get(1).getMessage()).contains("create timeout");
            assertThat(lifecycle.phaseOf("SES-1")).isEqualTo(SessionSandboxPhase.FAILED);
            verify(sandboxPoolService, never())
                    .ensureBound(anyString(), anyString(), anyString(), anyString());
        } finally {
            sub.dispose();
        }
    }

    @Test
    void sandboxStatusEvent_sseEventName() {
        assertThat(SandboxStatusEvent.SSE_EVENT).isEqualTo("Sandbox.Status");
    }
}
