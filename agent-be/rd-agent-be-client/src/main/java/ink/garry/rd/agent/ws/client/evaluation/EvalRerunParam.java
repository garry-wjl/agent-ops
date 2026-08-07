package ink.garry.rd.agent.ws.client.evaluation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 评测重跑入参。
 * <p>
 * 基于已存在的评测，复用其用例集再次执行（如修复后回归）。重跑后产生新的评测记录，
 * 原评测结果保留，便于版本对比（见 {@link EvalCompareVO}）。
 */
@Data
public class EvalRerunParam {
    /** 待重跑的原评测业务编号（必填） */
    @NotBlank
    private String evaluationNum;
}
