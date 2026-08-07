package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 检测错误明细 DTO（v3.0 新增）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCheckErrorDTO {

    /** 检测项类别：SIZE / FORMAT / AVAILABILITY */
    private String checkItem;

    /** 错误位置（资源文件相对路径；无具体位置时为 null） */
    private String location;

    /** 错误原因（面向用户的可读描述） */
    private String message;
}
