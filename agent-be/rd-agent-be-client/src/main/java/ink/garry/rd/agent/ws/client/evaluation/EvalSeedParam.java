package ink.garry.rd.agent.ws.client.evaluation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 评测种子用例入参。
 * <p>
 * 新增/沉淀一条 Skill 维度的"种子用例"，作为后续自动评测的样本来源
 * （见 {@link AutoEvalParam} 的 caseCount 抽样）。
 */
@Data
public class EvalSeedParam {
    /** 归属 Skill 业务编号（必填） */
    @NotBlank
    private String skillNum;
    /** 种子用例输入（必填） */
    @NotBlank
    private String input;
    /** 期望输出；可空（空时由 Judge 自动判定为通过） */
    private String expectedOutput;
}
