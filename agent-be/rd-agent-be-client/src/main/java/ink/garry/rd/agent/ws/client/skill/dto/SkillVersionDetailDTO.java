package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Skill 版本详情 DTO（v2.10：当前与 {@link SkillVersionDTO} 字段一致；保留独立类型便于将来扩展，
 * 如附加 publishedBy / changeNote 等审计字段时不破坏列表接口）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SkillVersionDetailDTO {

    /** 基础版本信息（含 num/skillNum/version/name/description/tags/skillFileKey/status/createTime） */
    private SkillVersionDTO version;
}
