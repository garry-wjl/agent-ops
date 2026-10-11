package ink.garry.rd.agent.ws.infra.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SkillSemver} 单元测试。
 */
class SkillSemverTest {

    @Test
    void nextPatch_blank_startsAt100() {
        assertThat(SkillSemver.nextPatch(null)).isEqualTo("1.0.0");
        assertThat(SkillSemver.nextPatch("")).isEqualTo("1.0.0");
        assertThat(SkillSemver.nextPatch("  ")).isEqualTo("1.0.0");
    }

    @Test
    void nextPatch_incrementsPatch() {
        assertThat(SkillSemver.nextPatch("1.0.0")).isEqualTo("1.0.1");
        assertThat(SkillSemver.nextPatch("2.3.9")).isEqualTo("2.3.10");
    }

    @Test
    void nextPatch_stripsPrereleaseSuffix() {
        assertThat(SkillSemver.nextPatch("1.0.0-beta")).isEqualTo("1.0.1");
    }

    @Test
    void nextPatch_unparseable_fallsBackTo100() {
        assertThat(SkillSemver.nextPatch("v1")).isEqualTo("1.0.0");
        assertThat(SkillSemver.nextPatch("abc")).isEqualTo("1.0.0");
    }
}
