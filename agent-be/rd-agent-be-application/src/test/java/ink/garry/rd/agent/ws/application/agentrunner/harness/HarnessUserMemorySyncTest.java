package ink.garry.rd.agent.ws.application.agentrunner.harness;

import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.domain.agent.AgentUserMemory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentUserMemoryGateway;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarnessUserMemorySyncTest {

    private static final String AGENT = "AGT-MEM-TEST";
    private static final String USER = "user-mem";

    private AgentQueryService agentQueryService;
    private AgentUserMemoryGateway gateway;
    private SandboxPoolService pool;
    private HarnessUserMemorySync sync;

    @BeforeEach
    void setUp() throws Exception {
        agentQueryService = mock(AgentQueryService.class);
        gateway = mock(AgentUserMemoryGateway.class);
        pool = mock(SandboxPoolService.class);
        sync = new HarnessUserMemorySync();
        inject(sync, "agentQueryService", agentQueryService);
        inject(sync, "agentUserMemoryGateway", gateway);
        inject(sync, "sandboxPoolService", pool);
        inject(sync, "openSandboxExecBridge", mock(OpenSandboxExecBridge.class));
        WorkspaceContextHolder.set(WorkspaceContext.builder().workspaceNum("WS1").build());
    }

    @AfterEach
    void tearDown() throws Exception {
        WorkspaceContextHolder.clear();
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "rd-agent-harness", AGENT, USER);
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // 测试清理失败不掩盖断言
                    }
                });
            }
        }
    }

    @Test
    void disabled_doesNotReadOrWriteMemory() {
        when(agentQueryService.loadAgentForDebug(AGENT, "v1")).thenReturn(agent(false));

        sync.hydrate(AGENT, "v1", "SES1", USER);
        sync.capture(AGENT, "v1", "SES1", USER);

        verify(gateway, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(gateway, never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabled_hydratesFromDbAndCapturesBack() throws Exception {
        when(agentQueryService.loadAgentForDebug(AGENT, "v1")).thenReturn(agent(true));
        when(pool.boundInstanceId("SES1")).thenReturn(null);
        when(gateway.find("WS1", AGENT, USER)).thenReturn(AgentUserMemory.builder()
                .workspaceNum("WS1")
                .agentNum(AGENT)
                .userId(USER)
                .memoryMd("记住偏好")
                .dailyLedgerJson("{\"2026-09-01\":\"旧账\"}")
                .build());

        sync.hydrate(AGENT, "v1", "SES1", USER);

        Path root = Path.of(System.getProperty("java.io.tmpdir"), "rd-agent-harness", AGENT, USER);
        assertEquals("记住偏好", Files.readString(root.resolve("MEMORY.md")));
        assertEquals("旧账", Files.readString(root.resolve("memory/2026-09-01.md")));

        LocalDate today = LocalDate.now();
        Files.writeString(root.resolve(UserMemoryFiles.dailyRelative(today)), "今天的事实");
        Files.writeString(root.resolve("MEMORY.md"), "整理后的记忆");
        when(gateway.find("WS1", AGENT, USER)).thenReturn(AgentUserMemory.builder()
                .workspaceNum("WS1")
                .agentNum(AGENT)
                .userId(USER)
                .memoryMd("记住偏好")
                .dailyLedgerJson("{\"2026-09-01\":\"旧账\"}")
                .build());

        sync.capture(AGENT, "v1", "SES1", USER);

        ArgumentCaptor<AgentUserMemory> captor = ArgumentCaptor.forClass(AgentUserMemory.class);
        verify(gateway).upsert(captor.capture());
        assertEquals("整理后的记忆", captor.getValue().getMemoryMd());
        assertTrue(captor.getValue().getDailyLedgerJson().contains(today.toString()));
        assertTrue(captor.getValue().getDailyLedgerJson().contains("今天的事实"));
        assertTrue(captor.getValue().getDailyLedgerJson().contains("旧账"));
        assertFalse(captor.getValue().getDailyLedgerJson().contains("记住偏好"));
    }

    private static AgentDTO agent(boolean memoryOn) {
        return AgentDTO.builder()
                .num(AGENT)
                .configSnapshot(AgentDTO.ConfigSnapshot.builder()
                        .enableLongTermMemory(memoryOn)
                        .build())
                .build();
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
