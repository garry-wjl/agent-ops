package ink.garry.rd.agent.ws.infra.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import ink.garry.rd.agent.ws.domain.skill.dto.SkillCheckResultDTO;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItemResult;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SkillChecker} front-matter：version 可选。
 */
class SkillCheckerFrontMatterTest {

    @Test
    void formatPassWithoutVersion() {
        String md = """
                ---
                name: demo
                description: d
                ---

                # hi
                """;
        SkillCheckResultDTO result = SkillChecker.check(List.of(rootSkillMd(md)));
        assertThat(result.getFormatResult()).isEqualTo(SkillCheckItemResult.PASS);
        assertThat(result.getErrors()).noneMatch(e -> e.getMessage() != null && e.getMessage().contains("version"));
    }

    @Test
    void formatFailOnInvalidVersionWhenPresent() {
        String md = """
                ---
                name: demo
                description: d
                version: not-semver
                ---

                # hi
                """;
        SkillCheckResultDTO result = SkillChecker.check(List.of(rootSkillMd(md)));
        assertThat(result.getResult()).isEqualTo(SkillCheckResult.FAIL);
        assertThat(result.getFormatResult()).isEqualTo(SkillCheckItemResult.FAIL);
        assertThat(result.getErrors()).anyMatch(e -> e.getMessage() != null && e.getMessage().contains("Semver"));
    }

    private static SkillResourceFile rootSkillMd(String content) {
        return SkillResourceFile.builder()
                .path(SkillResourceCodec.SKILL_MD_FILENAME)
                .name(SkillResourceCodec.SKILL_MD_FILENAME)
                .parentPath(null)
                .type(SkillResourceFileType.FILE)
                .encoding("text")
                .content(content)
                .build();
    }
}
