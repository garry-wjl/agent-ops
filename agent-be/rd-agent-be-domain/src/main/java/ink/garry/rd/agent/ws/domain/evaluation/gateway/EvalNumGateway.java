package ink.garry.rd.agent.ws.domain.evaluation.gateway;

/**
 * 评测域编号生成网关：统一分配评测集/行/评估器/任务/用例业务编号。
 */
public interface EvalNumGateway {

    /** 生成评测集编号（前缀 EDS）。 */
    String generateDatasetNum();

    /** 生成评测集行编号（前缀 EDR）。 */
    String generateDatasetRowNum();

    /** 生成评估器编号（前缀 EGR）。 */
    String generateGraderNum();

    /** 生成评测任务编号（前缀 ETK）。 */
    String generateTaskNum();

    /** 生成评测任务用例编号（前缀 ETI）。 */
    String generateTaskItemNum();

    /** 生成评测集 Case 自动生成任务编号（前缀 ECG）。 */
    String generateCaseGenJobNum();
}
