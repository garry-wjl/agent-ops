package ink.garry.rd.agent.ws.domain.evaluation;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationRepository;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvaluationStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.evaluation.EvalDomainEventDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 评测聚合根。
 * 表示对某个 Skill / Agent 版本的一次评测任务，聚合执行用例（EvaluationCase）的统计结果，
 * 状态机：PENDING → RUNNING → FINISHED / FAILED。
 */
@Getter
@Setter
public class Evaluation extends DomainEntity implements ink.garry.rd.agent.ws.facade.domain.PublisherAware {
    /** 评测业务编号，全局唯一，由 EvalNumGateway 生成。 */
    private String num;
    /** 评测名称（人类可读）。 */
    private String name;
    /** 被评测的 Agent 业务编号。 */
    private String agentNum;
    /** 被评测的 Agent 版本编号；可空表示评测最新版本。 */
    private String agentVersionNum;
    /** 被评测的 Skill 业务编号；可空表示对整个 Agent 评测。 */
    private String skillNum;
    /** 评测状态：PENDING / RUNNING / FINISHED / FAILED。 */
    private EvaluationStatus status;
    /** 评测发起人用户 ID。 */
    private String creatorUserId;
    /** 评测用例总数（finish 时回填）。 */
    private Integer totalCaseCount;
    /** 评测通过用例数（finish 时回填）。 */
    private Integer passedCaseCount;
    /** 评测失败用例数（finish 时回填）。 */
    private Integer failedCaseCount;

    /** 装配依赖：评测仓储，用于持久化。 */
    private transient EvaluationRepository evaluationRepository;
    /** 装配依赖：评测域编号生成网关。 */
    private transient EvalNumGateway evalNumGateway;
    /** 装配依赖：领域事件发布器，发布评测完成事件（可空时跳过）。 */
    private transient DomainEventPublisher domainEventPublisher;

    /**
     * 校验聚合不变量：agentNum、创建人、状态必填。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(agentNum, "agentNum 不能为空");
        Assert.notBlank(creatorUserId, "评测创建人不能为空");
        Assert.notNull(status, "评测状态不能为空");
    }

    /**
     * 保存评测：首次保存时初始化默认状态 PENDING、生成 num，并将统计计数置 0；随后落库。
     */
    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (status == null) {
            status = EvaluationStatus.PENDING;
        }
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateEvaluationNum();
        }
        if (totalCaseCount == null) {
            totalCaseCount = 0;
        }
        if (passedCaseCount == null) {
            passedCaseCount = 0;
        }
        if (failedCaseCount == null) {
            failedCaseCount = 0;
        }
        validate();
        evaluationRepository.save(this);
    }

    /**
     * 软删除评测：置 deleted=1 后按业务编号删除（不级联用例）。
     */
    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测编号不能为空");
        deleted = 1;
        evaluationRepository.deleteByNum(num);
    }

    /**
     * 标记评测成功完成：写入用例统计、置状态 FINISHED 并发布 EVALUATION_FINISHED 事件。
     *
     * @param total       总用例数
     * @param passed      通过用例数
     * @param failed      失败用例数
     * @param operatorId  操作人 ID
     */
    public void finish(int total, int passed, int failed, String operatorId) {
        initialize(operatorId);
        totalCaseCount = total;
        passedCaseCount = passed;
        failedCaseCount = failed;
        status = EvaluationStatus.FINISHED;
        validate();
        evaluationRepository.save(this);
        publishFinished(operatorId);
    }

    /**
     * 标记评测整体失败：置状态 FAILED 并发布 EVALUATION_FINISHED 事件。
     *
     * @param operatorId 操作人 ID
     */
    public void fail(String operatorId) {
        initialize(operatorId);
        status = EvaluationStatus.FAILED;
        validate();
        evaluationRepository.save(this);
        publishFinished(operatorId);
    }

    /** 发布评测完成事件；发布器未装配时静默跳过。 */
    private void publishFinished(String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        domainEventPublisher.send(DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(DomainEventConstant.EVALUATION_FINISHED)
                .data(EvalDomainEventDTO.builder()
                        .evaluationNum(num)
                        .agentNum(agentNum)
                        .agentVersionNum(agentVersionNum)
                        .status(status.name())
                        .operatorId(operatorId)
                        .occurredAt(LocalDateTime.now())
                        .build())
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build());
    }
}
