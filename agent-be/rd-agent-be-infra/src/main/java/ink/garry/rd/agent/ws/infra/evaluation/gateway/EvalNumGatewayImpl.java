package ink.garry.rd.agent.ws.infra.evaluation.gateway;

import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EvalNumGateway 实现：基于 BizNumGenerator + 类型前缀。
 * <p>
 * 前缀按总体方案 §10.3：Evaluation=EVL，EvaluationCase=EVC，EvalSeed=ESD。
 */
@Component
@RequiredArgsConstructor
public class EvalNumGatewayImpl implements EvalNumGateway {

    /** 评测任务业务编号前缀 */
    private static final String EVAL_PREFIX = "EVL";
    /** 评测用例业务编号前缀 */
    private static final String CASE_PREFIX = "EVC";
    /** 评测种子业务编号前缀 */
    private static final String SEED_PREFIX = "ESD";

    private final BizNumGenerator bizNumGenerator;

    @Override
    public String generateEvaluationNum() {
        return bizNumGenerator.generate(EVAL_PREFIX);
    }

    @Override
    public String generateEvaluationCaseNum() {
        return bizNumGenerator.generate(CASE_PREFIX);
    }

    @Override
    public String generateEvalSeedNum() {
        return bizNumGenerator.generate(SEED_PREFIX);
    }
}
