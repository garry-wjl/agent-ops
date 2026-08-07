package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回滚到指定历史版本入参 Vo（adapter 层用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillRollbackParam {

    /** Skill 业务编号 */
    private String skillNum;

    /** 目标历史版本号 */
    private String targetVersion;
}
