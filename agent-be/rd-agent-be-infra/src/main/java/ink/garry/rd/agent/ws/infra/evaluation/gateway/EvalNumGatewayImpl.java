package ink.garry.rd.agent.ws.infra.evaluation.gateway;

import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * EvalNumGateway 实现：BizNumGenerator + 类型前缀。
 */
@Component
public class EvalNumGatewayImpl implements EvalNumGateway {

    private static final String DATASET_PREFIX = "EDS";
    private static final String ROW_PREFIX = "EDR";
    private static final String GRADER_PREFIX = "EGR";
    private static final String TASK_PREFIX = "ETK";
    private static final String ITEM_PREFIX = "ETI";
    private static final String CASE_GEN_PREFIX = "ECG";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateDatasetNum() {
        return bizNumGenerator.generate(DATASET_PREFIX);
    }

    @Override
    public String generateDatasetRowNum() {
        return bizNumGenerator.generate(ROW_PREFIX);
    }

    @Override
    public String generateGraderNum() {
        return bizNumGenerator.generate(GRADER_PREFIX);
    }

    @Override
    public String generateTaskNum() {
        return bizNumGenerator.generate(TASK_PREFIX);
    }

    @Override
    public String generateTaskItemNum() {
        return bizNumGenerator.generate(ITEM_PREFIX);
    }

    @Override
    public String generateCaseGenJobNum() {
        return bizNumGenerator.generate(CASE_GEN_PREFIX);
    }
}
