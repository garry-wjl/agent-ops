package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.core.skill.AgentSkill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BoundSkillRepository} 单元测试。
 */
class BoundSkillRepositoryTest {

    @Test
    void exposesOnlyBoundSkillsByName() {
        AgentSkill a = AgentSkill.builder()
                .name("alpha")
                .description("skill alpha")
                .skillContent("# A")
                .build();
        AgentSkill b = AgentSkill.builder()
                .name("beta")
                .description("skill beta")
                .skillContent("# B")
                .build();
        BoundSkillRepository repo = new BoundSkillRepository(List.of(a, b));

        assertEquals(List.of("alpha", "beta"), repo.getAllSkillNames());
        assertEquals(2, repo.getAllSkills().size());
        assertEquals("alpha", repo.getSkill("alpha").getName());
        assertNull(repo.getSkill("gamma"));
        assertTrue(repo.skillExists("beta"));
        assertFalse(repo.skillExists("gamma"));
    }

    @Test
    void emptyAndNullSafe() {
        BoundSkillRepository empty = new BoundSkillRepository(List.of());
        assertTrue(empty.getAllSkillNames().isEmpty());
        BoundSkillRepository nil = new BoundSkillRepository(null);
        assertTrue(nil.getAllSkills().isEmpty());
    }

    @Test
    void writeApisUnsupported() {
        BoundSkillRepository repo = new BoundSkillRepository(List.of());
        assertThrows(UnsupportedOperationException.class, () -> repo.save(List.of(), true));
        assertThrows(UnsupportedOperationException.class, () -> repo.delete("x"));
    }
}
