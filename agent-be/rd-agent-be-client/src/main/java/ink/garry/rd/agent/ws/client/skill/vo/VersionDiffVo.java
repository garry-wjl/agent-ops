package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 版本对比 Vo（v2.10：仅字段级 diff；SKILL.md 行级 diff 待 SkillFileStorage 上线后补 mdDiff）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VersionDiffVo {

    /** 基线版本号（左侧） */
    private String versionA;

    /** 比对版本号（右侧） */
    private String versionB;

    /** name 字段 diff；null 表示一致 */
    private String nameDiff;

    /** description 字段 diff；null 表示一致 */
    private String descriptionDiff;

    /** tags 集合差集 */
    private TagsDiff tagsDiff;

    /** 标签集合差集（静态内部类） */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TagsDiff {
        /** 仅出现在 versionA 的标签 */
        private List<String> onlyInA;
        /** 仅出现在 versionB 的标签 */
        private List<String> onlyInB;
        /** 两版本共有的标签 */
        private List<String> common;
    }
}
