package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 检测记录分页查询入参 DTO（v3.0 新增）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCheckRecordPageQueryParamDTO {

    /** 所属 Skill 业务编号（必填） */
    private String skillNum;

    /** 当前页码，从 1 起 */
    private Integer pageNo;

    /** 每页大小 */
    private Integer pageSize;
}
