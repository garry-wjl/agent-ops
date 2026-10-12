package ink.garry.rd.agent.ws.application.agentrunner.sandbox;

import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HeaderEnvInjector} 单元测试：开关、映射、大小写、非法 env 名、export 前缀。
 */
class HeaderEnvInjectorTest {

    @Test
    void resolve_disabled_returnsEmpty() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder()
                .headerEnvInjectEnabled(false)
                .headerEnvMappings(List.of(mapping("X-Token", "TOKEN")))
                .build();
        assertTrue(HeaderEnvInjector.resolve(snap, Map.of("X-Token", "abc")).isEmpty());
    }

    @Test
    void resolve_enabled_mapsCaseInsensitiveHeader() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder()
                .headerEnvInjectEnabled(true)
                .headerEnvMappings(List.of(
                        mapping("X-User-Id", "USER_ID"),
                        mapping("x-trace", "TRACE_ID")))
                .build();
        Map<String, String> env = HeaderEnvInjector.resolve(
                snap, Map.of("x-user-id", "u1", "X-Trace", "t9"));
        assertEquals(Map.of("USER_ID", "u1", "TRACE_ID", "t9"), env);
    }

    @Test
    void resolve_skipsMissingHeaderAndInvalidEnvName() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder()
                .headerEnvInjectEnabled(true)
                .headerEnvMappings(List.of(
                        mapping("X-A", "BAD-NAME"),
                        mapping("X-B", "GOOD"),
                        mapping("X-Missing", "MISS")))
                .build();
        Map<String, String> env = HeaderEnvInjector.resolve(snap, Map.of("X-A", "a", "X-B", "b"));
        assertEquals(Map.of("GOOD", "b"), env);
    }

    @Test
    void prefixExports_wrapsCommand() {
        Map<String, String> env = new java.util.LinkedHashMap<>();
        env.put("FOO", "bar");
        env.put("Q", "a'b");
        String wrapped = HeaderEnvInjector.prefixExports(env, "echo hi");
        assertEquals("export FOO='bar'; export Q='a'\\''b'; echo hi", wrapped);
    }

    @Test
    void prefixExports_emptyEnv_unchanged() {
        assertEquals("ls", HeaderEnvInjector.prefixExports(Map.of(), "ls"));
        assertEquals("ls", HeaderEnvInjector.prefixExports(null, "ls"));
    }

    private static AgentDTO.ConfigSnapshot.HeaderEnvMapping mapping(String header, String env) {
        return AgentDTO.ConfigSnapshot.HeaderEnvMapping.builder()
                .headerName(header)
                .envName(env)
                .build();
    }
}
