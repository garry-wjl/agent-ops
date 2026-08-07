package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

/**
 * 评测用例 VO。
 * <p>
 * 一条评测中的单个用例：包含输入、期望输出、实际输出与 Judge 判定结果。
 * 评测尚未跑完时 actualOutput/judgeResult/status 可能为空。
 */
@Data
public class EvalCaseVO {
    /** 用例业务编号 */
    private String num;
    /** 所属评测业务编号 */
    private String evaluationNum;
    /** 用例输入 */
    private String input;
    /** 期望输出；可空（空时由 Judge 自动判定为通过） */
    private String expectedOutput;
    /** 本次实际输出；用例未跑完时为空 */
    private String actualOutput;
    /** Judge 判定结果（如 PASS/FAIL/PARTIAL） */
    private String judgeResult;
    /** 用例执行状态（如 PENDING/RUNNING/DONE/ERROR） */
    private String status;
}
