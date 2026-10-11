package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布 Skill 新版本入参 Vo（adapter 层用）。
 * <p>版本号由后端自动生成；{@link #version} 仅兼容旧客户端，已忽略。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillPublishParam {

    /** Skill 业务编号 */
    private String skillNum;

    /**
     * @deprecated 版本号由系统自动分配，传入无效
     */
    @Deprecated
    private String version;
}
