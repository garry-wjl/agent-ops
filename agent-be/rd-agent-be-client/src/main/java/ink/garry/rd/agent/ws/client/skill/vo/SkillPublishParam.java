package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布 Skill 新版本入参 Vo（adapter 层用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillPublishParam {

    /** Skill 业务编号 */
    private String skillNum;

    /** 新版本号字符串 */
    private String version;
}
