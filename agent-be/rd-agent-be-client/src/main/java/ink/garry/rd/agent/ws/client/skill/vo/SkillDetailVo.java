package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 详情 Vo（含当前版本嵌套 + 复用次数）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillDetailVo {

    /** 基础 Skill 信息 */
    private SkillVo skill;

    /** 当前在线版本详细信息；status=DRAFT 且 currentVersionNum=null 时为 null */
    private SkillVersionVo currentVersion;

    /** 被多少个 Agent 复用引用；M3 接入后非 null，当前 0 占位 */
    private Integer reuseCount;

    /** 当前在线版本 SKILL.md 正文内容（v3.0 hotfix） */
    private String skillMdContent;
}
