package ink.garry.rd.agent.ws.domain.evaluation;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationCaseRepository;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvalCaseStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 评测执行用例聚合根。
 * 表示某次评测下的一个具体执行实例：保存输入、期望输出、实际输出、Judge 评分与状态。
 * 状态机：PENDING → RUNNING → PASSED / FAILED。
 */
@Getter
@Setter
public class EvaluationCase extends DomainEntity {
    /** 用例业务编号，全局唯一，由 EvalNumGateway 生成。 */
    private String num;
    /** 所属评测的业务编号，外键关联 Evaluation.num。 */
    private String evaluationNum;
    /** 用例输入文本。 */
    private String input;
    /** 用例期望输出（来自种子或人工编辑）。 */
    private String expectedOutput;
    /** 实际输出（执行后回填）。 */
    private String actualOutput;
    /** Judge 评分结果 JSON/文本（执行后回填）。 */
    private String judgeResult;
    /** 用例状态：PENDING / RUNNING / PASSED / FAILED。 */
    private EvalCaseStatus status;

    /** 装配依赖：用例仓储，用于持久化。 */
    private transient EvaluationCaseRepository evaluationCaseRepository;
    /** 装配依赖：评测域编号生成网关。 */
    private transient EvalNumGateway evalNumGateway;

    /**
     * 校验聚合不变量：evaluationNum、input、status 必填。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(evaluationNum, "evaluationNum 不能为空");
        Assert.notBlank(input, "评测输入不能为空");
        Assert.notNull(status, "用例状态不能为空");
    }

    /**
     * 保存用例：首次保存时生成 num，状态默认 PENDING；随后落库。
     */
    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateEvaluationCaseNum();
        }
        if (status == null) {
            status = EvalCaseStatus.PENDING;
        }
        validate();
        evaluationCaseRepository.save(this);
    }

    /**
     * 软删除用例：置 deleted=1 后按业务编号删除。
     */
    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "用例编号不能为空");
        deleted = 1;
        evaluationCaseRepository.deleteByNum(num);
    }

    /**
     * 标记用例通过：写入实际输出与 Judge 评分，状态置 PASSED 后落库。
     *
     * @param actualOutput 实际输出
     * @param judgeResult  Judge 评分结果
     * @param operatorId   操作人 ID
     */
    public void markPassed(String actualOutput, String judgeResult, String operatorId) {
        initialize(operatorId);
        this.actualOutput = actualOutput;
        this.judgeResult = judgeResult;
        this.status = EvalCaseStatus.PASSED;
        validate();
        evaluationCaseRepository.save(this);
    }

    /**
     * 标记用例失败：写入实际输出与 Judge 评分，状态置 FAILED 后落库。
     *
     * @param actualOutput 实际输出
     * @param judgeResult  Judge 评分结果
     * @param operatorId   操作人 ID
     */
    public void markFailed(String actualOutput, String judgeResult, String operatorId) {
        initialize(operatorId);
        this.actualOutput = actualOutput;
        this.judgeResult = judgeResult;
        this.status = EvalCaseStatus.FAILED;
        validate();
        evaluationCaseRepository.save(this);
    }
}
