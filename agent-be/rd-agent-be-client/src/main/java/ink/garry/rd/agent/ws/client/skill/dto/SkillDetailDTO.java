package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Skill 详情 DTO（含当前在线版本嵌套 + 复用次数）。
 * <p>继承 {@link SkillDTO} 全部字段。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SkillDetailDTO {

    /** 基础 Skill 信息（含 num/name/description/tags/skillFileKey/source/status/currentVersionNum/审计字段） */
    private SkillDTO skill;

    /**
     * 当前在线版本的详细信息；
     * status=DRAFT 且 currentVersionNum=null 时为 null。
     */
    private SkillVersionDTO currentVersion;

    /**
     * 被多少个 Agent 复用引用；M3 接入后非 null。
     * 当前阶段返回 0 或 null 占位。
     */
    private Integer reuseCount;

    /**
     * 当前在线版本 SKILL.md 正文内容（v3.0 hotfix：从版本快照资源树提取）。
     * <p>用于详情页「基本信息」Tab 展示；无在线版本或资源树中无 SKILL.md 时为 null。
     */
    private String skillMdContent;
}
