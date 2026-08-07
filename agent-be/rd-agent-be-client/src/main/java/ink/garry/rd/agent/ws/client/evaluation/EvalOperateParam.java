package ink.garry.rd.agent.ws.client.evaluation;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评测状态机操作通用入参：仅需评测业务编号。
 * <p>
 * 用于 /submit /pause /resume /cancel /restart 接口。
 */
@Data
public class EvalOperateParam {
    /** 评测业务编号（必填） */
    @NotNull
    private String evaluationNum;
}
