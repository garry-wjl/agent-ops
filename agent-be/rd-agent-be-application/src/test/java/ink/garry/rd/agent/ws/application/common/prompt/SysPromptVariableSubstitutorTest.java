package ink.garry.rd.agent.ws.application.common.prompt;

import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SysPromptVariableSubstitutor} 单元测试。
 */
class SysPromptVariableSubstitutorTest {

    @Test
    void substitute_shouldReplaceKnownAndKeepMissing() {
        Map<String, String> vars = Map.of("orderId", "ORD-1", "SESSION_NUM", "SES-9");
        String out = SysPromptVariableSubstitutor.substitute(
                "订单 {{orderId}} 会话 {{SESSION_NUM}} 缺 {{missing}}", vars);
        assertEquals("订单 ORD-1 会话 SES-9 缺 {{missing}}", out);
    }

    @Test
    void substitute_shouldNotRecurse() {
        Map<String, String> vars = Map.of("a", "{{b}}", "b", "X");
        assertEquals("{{b}}", SysPromptVariableSubstitutor.substitute("{{a}}", vars));
    }

    @Test
    void merge_laterLayerWins() {
        Map<String, String> builtins = Map.of("SESSION_NUM", "S1", "orderId", "builtin");
        Map<String, Object> session = Map.of("orderId", "fromSession");
        Map<String, Object> request = Map.of("orderId", "fromRequest", "page", "detail");
        Map<String, String> merged = SysPromptVariableSubstitutor.merge(builtins, session, request);
        assertEquals("S1", merged.get("SESSION_NUM"));
        assertEquals("fromRequest", merged.get("orderId"));
        assertEquals("detail", merged.get("page"));
    }

    @Test
    void validateContext_rejectsIllegalKeyAndType() {
        assertThrows(BusinessException.class, () ->
                SysPromptVariableSubstitutor.validateContext(Map.of("1bad", "x")));
        Map<String, Object> nested = new HashMap<>();
        nested.put("obj", Map.of("a", 1));
        assertThrows(BusinessException.class, () ->
                SysPromptVariableSubstitutor.validateContext(nested));
    }

    @Test
    void validateContext_rejectsOversizedValue() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("big", "x".repeat(3000));
        assertThrows(BusinessException.class, () ->
                SysPromptVariableSubstitutor.validateContext(ctx));
    }

    @Test
    void builtinVars_keysAreUppercase() {
        Map<String, String> b = SysPromptVariableSubstitutor.builtinVars(
                "S", "A", "v1", "W", "u1");
        assertTrue(b.containsKey("SESSION_NUM"));
        assertTrue(b.containsKey("AGENT_NUM"));
        assertTrue(b.containsKey("AGENT_VERSION_NUM"));
        assertTrue(b.containsKey("WORKSPACE_NUM"));
        assertTrue(b.containsKey("OPERATOR_ID"));
        assertEquals("S", b.get("SESSION_NUM"));
    }
}
