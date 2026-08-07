package ink.garry.rd.agent.ws.domain.skillcheck.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Skill 发布检测错误（贫血模型值对象）。
 * <p>
 * 描述一条具体的检测失败项：属于哪类检测（{@link #checkItem}）、位置（{@link #location}，
 * 通常为资源文件相对路径）、原因（{@link #message}）。多条错误内聚在
 * {@link ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord} 聚合中。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCheckError {

    /** 检测项类别（SIZE / FORMAT / AVAILABILITY）。 */
    private SkillCheckItem checkItem;

    /** 错误位置（资源文件相对路径，如 {@code SKILL.md}、{@code references/api.md}；无具体位置时可为空）。 */
    private String location;

    /** 错误原因（面向用户的可读描述）。 */
    private String message;
}
