package ink.garry.rd.agent.ws.client.evaluation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自动评测入参。
 * <p>
 * 指定 Agent/Skill + 期望用例数，系统从种子用例（见 {@link EvalSeedParam}）抽样生成用例集
 * 并异步执行评测。与人工评测 {@link ManualEvalParam} 的区别：本接口批量、异步、用例由系统生成。
 */
@Data
public class AutoEvalParam {
    /** 被评测 Agent 业务编号（必填） */
    @NotBlank
    private String agentNum;
    /** 被评测 Agent 版本编号；为空表示当前在线版本 */
    private String agentVersionNum;
    /** 被评测 Skill 业务编号；为空表示对整个 Agent 评测 */
    private String skillNum;
    /** 期望生成的用例数；为空则按系统默认 */
    private Integer caseCount;
    /** 评测名称（人类可读） */
    private String name;
}
