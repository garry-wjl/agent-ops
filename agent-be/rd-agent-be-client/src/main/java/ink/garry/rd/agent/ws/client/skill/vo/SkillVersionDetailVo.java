package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 版本详情 Vo（v2.10：字段当前与 SkillVersionVo 一致；保留独立类型便于将来扩展）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillVersionDetailVo {

    /** 基础版本信息 */
    private SkillVersionVo version;
}
