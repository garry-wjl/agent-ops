package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

/**
 * 评测种子用例 VO。
 * <p>
 * 返回一条已沉淀的种子用例（对应 {@link EvalSeedParam} 的写入结果）。
 * 种子用例是后续自动评测抽样的样本来源。
 */
@Data
public class EvalSeedVO {
    /** 种子用例业务编号 */
    private String num;
    /** 归属 Skill 业务编号 */
    private String skillNum;
    /** 种子用例输入 */
    private String input;
    /** 期望输出；可空 */
    private String expectedOutput;
}
