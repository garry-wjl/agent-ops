package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 快照中的 Skill 版本引用。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillRef {

    /** Skill 业务编号。 */
    private String skillNum;

    /** 发布版本号。 */
    private String versionNum;
}
