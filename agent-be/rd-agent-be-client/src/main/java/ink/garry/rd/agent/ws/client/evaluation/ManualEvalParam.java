package ink.garry.rd.agent.ws.client.evaluation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 人工调试评测入参：单条输入 + 期望输出，同步执行后立即返回结果。
 */
@Data
public class ManualEvalParam {
    /** 被评测 Agent 业务编号 */
    @NotBlank
    private String agentNum;
    /** 被评测 Agent 版本编号；为空表示用当前在线版本 */
    private String agentVersionNum;
    /** 被评测 Skill 业务编号；为空表示对整个 Agent 评测 */
    private String skillNum;
    /** 评测名称（人类可读） */
    private String name;
    /** 用例输入（必填） */
    @NotBlank
    private String input;
    /** 期望输出，可空（空时 Judge 自动通过） */
    private String expectedOutput;
}
