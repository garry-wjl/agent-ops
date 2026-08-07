package ink.garry.rd.agent.ws.domain.session.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 思维链单节点值对象：表示 Agent 一次工具/技能调用的输入、输出与状态。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepNode {
    /** 节点 ID，用于在思维链中唯一标识该步骤。 */
    private String stepId;
    /** 被调用的技能名称。 */
    private String skillName;
    /** 步骤入参（结构由具体技能决定）。 */
    private Object input;
    /** 步骤出参（结构由具体技能决定）。 */
    private Object output;
    /** 步骤状态文本，如 SUCCESS / FAILED / RUNNING。 */
    private String status;
    /** 该步骤耗时，单位 ms。 */
    private Integer latencyMs;
    /** 步骤错误信息；成功时为空。 */
    private String error;
}
