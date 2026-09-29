package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SandboxEnvVars} 归一化与校验。
 */
class SandboxEnvVarsTest {

    @Test
    void normalize_nullOrEmpty_returnsEmpty() {
        assertTrue(SandboxEnvVars.normalize(null).isEmpty());
        assertTrue(SandboxEnvVars.normalize(Map.of()).isEmpty());
    }

    @Test
    void normalize_trimsKeyAndKeepsOrder() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("  FOO ", "bar");
        raw.put("BAZ", "qux");
        Map<String, String> out = SandboxEnvVars.normalize(raw);
        assertEquals("bar", out.get("FOO"));
        assertEquals("qux", out.get("BAZ"));
        assertEquals(2, out.size());
    }

    @Test
    void normalize_rejectsIllegalKey() {
        assertThrows(IllegalArgumentException.class,
                () -> SandboxEnvVars.normalize(Map.of("1BAD", "x")));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxEnvVars.normalize(Map.of("HAS-DASH", "x")));
    }

    @Test
    void normalize_rejectsDuplicateAfterTrim() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("FOO", "1");
        raw.put(" FOO", "2");
        assertThrows(IllegalArgumentException.class, () -> SandboxEnvVars.normalize(raw));
    }

    @Test
    void normalize_rejectsTooLongValue() {
        String longVal = "x".repeat(SandboxEnvVars.MAX_VALUE_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> SandboxEnvVars.normalize(Map.of("K", longVal)));
    }
}
