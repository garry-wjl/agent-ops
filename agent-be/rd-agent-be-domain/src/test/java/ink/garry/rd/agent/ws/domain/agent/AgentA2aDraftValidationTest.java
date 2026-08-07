package ink.garry.rd.agent.ws.domain.agent;

import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentType;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Agent 聚合根 domainValidate 对 A2A 草稿态的宽松校验测试（hotfix_20260625_a2a-create-endpoints）。
 * <p>
 * v2.6 允许 A2A + DRAFT_ONLY 时 a2aSource / nacosServiceKey 均为空（远端 AgentCard 尚未拉取），
 * 但非草稿状态（PENDING_SYNC / PUBLISHED / OFFLINE）必须严格。
 */
class AgentA2aDraftValidationTest {

    /**
     * 直接 new Agent()，用反射塞字段；transient 仓储/网关/publisher 留 null（不影响 validate）。
     */
    private Agent newA2aAgent(AgentStatus status, A2aSourceInfo source, String nacosServiceKey) {
        Agent agent = new Agent();
        setField(agent, "name", "A2A 草稿");
        setField(agent, "creationMode", CreationMode.A2A);
        setField(agent, "agentType", AgentType.NORMAL);
        setField(agent, "ownerUserId", "system");
        setField(agent, "status", status);
        setField(agent, "a2aSource", source);
        setField(agent, "nacosServiceKey", nacosServiceKey);
        return agent;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = Agent.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** A2A + DRAFT_ONLY 允许 a2aSource / nacosServiceKey 为空。 */
    @Test
    void a2aDraftOnly_nullSourceAndKey_shouldPass() {
        Agent draft = newA2aAgent(AgentStatus.DRAFT_ONLY, null, null);
        assertDoesNotThrow(draft::domainValidate);
    }

    /** A2A + DRAFT_ONLY 允许只填 a2aSource 但 nacosServiceKey 留空（草稿态不强求 serviceKey 解析）。 */
    @Test
    void a2aDraftOnly_sourcePresent_keyMissing_shouldPass() {
        A2aSourceInfo source = A2aSourceInfo.builder()
                .nacosGroup("DEFAULT_GROUP")
                .nacosService("test-agent")
                .build();
        Agent draft = newA2aAgent(AgentStatus.DRAFT_ONLY, source, null);
        assertDoesNotThrow(draft::domainValidate);
    }

    /** A2A + PENDING_SYNC 缺少 a2aSource 必须被拒。 */
    @Test
    void a2aPending_nullSource_shouldReject() {
        Agent agent = newA2aAgent(AgentStatus.PENDING_SYNC, null, null);
        assertThrows(RuntimeException.class, agent::domainValidate);
    }

    /** A2A + PENDING_SYNC 缺少 nacosServiceKey 必须被拒。 */
    @Test
    void a2aPending_nullKey_shouldReject() {
        A2aSourceInfo source = A2aSourceInfo.builder()
                .nacosGroup("DEFAULT_GROUP")
                .nacosService("test-agent")
                .build();
        Agent agent = newA2aAgent(AgentStatus.PENDING_SYNC, source, null);
        assertThrows(RuntimeException.class, agent::domainValidate);
    }

    /** A2A + PUBLISHED 字段齐全时通过。 */
    @Test
    void a2aPublished_fullSourceAndKey_shouldPass() {
        A2aSourceInfo source = A2aSourceInfo.builder()
                .nacosGroup("DEFAULT_GROUP")
                .nacosService("test-agent")
                .build();
        Agent agent = newA2aAgent(AgentStatus.PUBLISHED, source, "DEFAULT_GROUP@@test-agent");
        assertDoesNotThrow(agent::domainValidate);
    }

    /** A2A 强制 agentType=NORMAL，SUPERVISOR 必拒（与原行为一致）。 */
    @Test
    void a2a_supervisorType_shouldReject() {
        Agent agent = newA2aAgent(AgentStatus.DRAFT_ONLY, null, null);
        setField(agent, "agentType", AgentType.SUPERVISOR);
        assertThrows(RuntimeException.class, agent::domainValidate);
    }
}