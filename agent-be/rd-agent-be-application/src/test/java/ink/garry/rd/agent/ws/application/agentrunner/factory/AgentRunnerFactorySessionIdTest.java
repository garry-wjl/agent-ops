package ink.garry.rd.agent.ws.application.agentrunner.factory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AgentRunnerFactory#resolveSessionId(String, String)} 单元测试。
 * <p>覆盖记忆按会话隔离修复：会话号非空时用会话号作 defaultSessionId（会话级隔离），
 * 会话号为空时回退到 Agent 名（保持旧行为、不抛异常）。
 */
class AgentRunnerFactorySessionIdTest {

    @Test
    void nonBlankSessionNum_shouldUseSessionNumForIsolation() {
        assertEquals("SES-123",
                AgentRunnerFactory.resolveSessionId("SES-123", "我的助手"));
    }

    @Test
    void blankSessionNum_shouldFallbackToAgentName() {
        assertEquals("我的助手", AgentRunnerFactory.resolveSessionId(null, "我的助手"));
        assertEquals("我的助手", AgentRunnerFactory.resolveSessionId("", "我的助手"));
        assertEquals("我的助手", AgentRunnerFactory.resolveSessionId("   ", "我的助手"));
    }

    @Test
    void differentSessions_shouldResolveToDifferentKeys() {
        // 同一 Agent 的两个会话解析出不同的隔离键 —— 记忆不再串扰
        String a = AgentRunnerFactory.resolveSessionId("SES-A", "助手");
        String b = AgentRunnerFactory.resolveSessionId("SES-B", "助手");
        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }
}
