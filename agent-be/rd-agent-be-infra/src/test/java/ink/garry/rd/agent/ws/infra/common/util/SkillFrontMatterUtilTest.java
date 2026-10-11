package ink.garry.rd.agent.ws.infra.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link SkillFrontMatterUtil} 单元测试。
 */
class SkillFrontMatterUtilTest {

    @Test
    void parseAndRewriteVersion() {
        String md = """
                ---
                name: demo
                description: d
                version: 1.0.0
                ---

                # hi
                """;
        List<SkillResourceFile> files = List.of(rootSkillMd(md));

        Map<String, String> fm = SkillFrontMatterUtil.parseRootFields(files);
        assertThat(fm.get("name")).isEqualTo("demo");
        assertThat(fm.get("version")).isEqualTo("1.0.0");

        List<SkillResourceFile> rewritten = SkillFrontMatterUtil.rewriteRootVersion(files, "1.0.1");
        assertThat(rewritten.get(0).getContent()).contains("version: 1.0.1");
        assertThat(rewritten.get(0).getContent()).contains("# hi");
        assertThat(SkillFrontMatterUtil.parseRootFields(rewritten).get("version")).isEqualTo("1.0.1");
    }

    @Test
    void parseMultilineDescriptionAndBom() {
        String md = "\uFEFF---\n"
                + "name: bom-skill\n"
                + "description: |\n"
                + "  line one\n"
                + "  line two\n"
                + "version: 1.0.0\n"
                + "---\n\n# body\n";
        Map<String, String> fm = SkillFrontMatterUtil.parseRootFields(List.of(rootSkillMd(md)));
        assertThat(fm.get("name")).isEqualTo("bom-skill");
        assertThat(fm.get("description")).isEqualTo("line one line two");
        assertThat(fm.get("version")).isEqualTo("1.0.0");
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
