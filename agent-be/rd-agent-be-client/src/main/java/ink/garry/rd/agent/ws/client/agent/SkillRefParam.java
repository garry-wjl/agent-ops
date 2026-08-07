package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * Agent 配置中绑定的 Skill 版本引用。
 */
@Data
public class SkillRefParam {

    /** Skill 业务编号。 */
    private String skillNum;

    /** 发布版本号。 */
    private String versionNum;
}
