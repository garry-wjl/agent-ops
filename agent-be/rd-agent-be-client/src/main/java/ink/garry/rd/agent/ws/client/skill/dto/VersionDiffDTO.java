package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 版本对比 DTO（v2.10：仅做字段级 diff；SKILL.md 行级 diff 待 SkillFileStorage 上线后再补 mdDiff 字段）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VersionDiffDTO {

    /** 基线版本号（左侧） */
    private String versionA;

    /** 比对版本号（右侧） */
    private String versionB;

    /** name 字段 diff；null 表示一致；非空时格式 "{vA.name} → {vB.name}" */
    private String nameDiff;

    /** description 字段 diff；同上 */
    private String descriptionDiff;

    /** tags 字段 diff：新增 / 移除 / 无变化（按集合差集计算） */
    private TagsDiff tagsDiff;

    /**
     * 标签集合差集。
     */
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
