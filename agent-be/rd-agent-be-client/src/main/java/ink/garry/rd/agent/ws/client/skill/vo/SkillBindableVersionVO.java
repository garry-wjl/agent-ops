package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Skill 可绑定版本 Vo（adapter 返回）。
 * <p>
 * 由 {@code SkillQueryService.bindableVersions(skillNum)} 输出，供 Agent 配置页
 * Skill 版本选择器使用。仅暴露状态为 {@code PUBLISHED} 的版本（DRAFT / DEPRECATED 不可绑定）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillBindableVersionVO {

    /** 版本号字符串（形如 v1.2.0；同 skillNum 下唯一）。 */
    private String versionNum;

    /** 发布时间（取 skill_version.create_time，即该版本发布落库时间）。 */
    private LocalDateTime publishedTime;

    /** 是否为当前最新发布版本（等于 {@code Skill.currentVersionNum} 者为 true）。 */
    private boolean latest;
}
