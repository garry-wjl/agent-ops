package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 列表分页查询入参 Vo（adapter 层用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillPageQueryParam {

    /** 当前页码，从 1 起 */
    private Integer pageNo;

    /** 每页大小 */
    private Integer pageSize;

    /** 按来源筛选：SELF / COMPANY；null 表示不限 */
    private String source;

    /** 按状态筛选：DRAFT / PUBLISHED / DEPRECATED；null 表示不限 */
    private String status;

    /** 关键词（在 name / description 内 LIKE 匹配）；null/空表示不限 */
    private String keyword;

    /** 按负责人筛选；null 表示不限（admin 视角；普通用户场景由 controller 默认取当前用户） */
    private String ownerUserId;
}
