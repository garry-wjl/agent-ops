package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

/**
 * Agent 步骤链中的单个节点 VO。
 * <p>
 * 表示 Agent 内部对单个 Skill 的一次调用：输入、输出、状态、延迟与错误信息。
 * input/output 类型为 Object，结构由 Skill 自身契约决定。
 */
@Data
public class StepNodeVO {
    /** 步骤 ID（链内唯一） */
    private String stepId;
    /** 被调用的 Skill 名称 */
    private String skillName;
    /** Skill 入参；结构由 Skill 自身契约决定 */
    private Object input;
    /** Skill 出参；结构由 Skill 自身契约决定 */
    private Object output;
    /** 步骤状态（如 PENDING/RUNNING/DONE/ERROR） */
    private String status;
    /** 步骤耗时（毫秒） */
    private Integer latencyMs;
    /** 错误信息；仅 status=ERROR 时有值 */
    private String error;
}
