package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回滚到指定历史版本入参 DTO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillRollbackParamDTO {

    /** Skill 业务编号 */
    private String skillNum;

    /** 目标历史版本号 */
    private String targetVersion;
}
