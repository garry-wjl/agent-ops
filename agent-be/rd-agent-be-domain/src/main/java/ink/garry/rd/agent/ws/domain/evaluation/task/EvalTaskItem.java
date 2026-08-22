package ink.garry.rd.agent.ws.domain.evaluation.task;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.gateway.EvalTaskGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.repository.EvalTaskItemRepository;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.EvalItemScore;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.ItemStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.domain.PublisherAware;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 评测任务用例实体。
 */
@Getter
@Setter
public class EvalTaskItem extends DomainEntity implements PublisherAware {

    private String num;
    private String taskNum;
    private Integer rowIndex;
    private String inputJson;
    private String actualOutput;
    private String traceSummaryJson;
    private Boolean overallPass;
    private ItemStatus status;
    private Long latencyMs;
    private String errorMessage;
    private String labelJson;

    private transient EvalTaskItemRepository evalTaskItemRepository;
    private transient EvalNumGateway evalNumGateway;
    private transient EvalTaskGateway evalTaskGateway;
    private transient DomainEventPublisher domainEventPublisher;

    public EvalTaskItem() {
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(taskNum, "taskNum 不能为空");
        Assert.notNull(rowIndex, "rowIndex 不能为空");
        Assert.notBlank(inputJson, "inputJson 不能为空");
        Assert.notNull(status, "status 不能为空");
    }

    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (status == null) {
            status = ItemStatus.PENDING;
        }
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateTaskItemNum();
        }
        domainValidate();
        evalTaskItemRepository.save(this);
        publish(operatorId);
    }

    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "用例编号不能为空");
        evalTaskItemRepository.deleteByNum(num);
        publish(operatorId);
    }

    /**
     * 标记通过并写入得分。
     */
    public void markPassed(String actualOutput, String traceSummaryJson, Long latencyMs,
                           List<EvalItemScore> scores, String operatorId) {
        initialize(operatorId);
        this.actualOutput = actualOutput;
        this.traceSummaryJson = traceSummaryJson;
        this.latencyMs = latencyMs;
        this.overallPass = true;
        this.status = ItemStatus.PASSED;
        this.errorMessage = null;
        domainValidate();
        evalTaskItemRepository.save(this);
        if (scores != null) {
            evalTaskGateway.replaceItemScores(num, scores);
        }
        publish(operatorId);
    }

    /**
     * 标记失败（评分未过或业务失败）。
     */
    public void markFailed(String actualOutput, String traceSummaryJson, Long latencyMs,
                           String errorMessage, List<EvalItemScore> scores, String operatorId) {
        initialize(operatorId);
        this.actualOutput = actualOutput;
        this.traceSummaryJson = traceSummaryJson;
        this.latencyMs = latencyMs;
        this.overallPass = false;
        this.status = ItemStatus.FAILED;
        this.errorMessage = errorMessage;
        domainValidate();
        evalTaskItemRepository.save(this);
        if (scores != null) {
            evalTaskGateway.replaceItemScores(num, scores);
        }
        publish(operatorId);
    }

    /**
     * 标记调用异常。
     */
    public void markError(String errorMessage, Long latencyMs, String operatorId) {
        initialize(operatorId);
        this.errorMessage = errorMessage;
        this.latencyMs = latencyMs;
        this.overallPass = false;
        this.status = ItemStatus.ERROR;
        domainValidate();
        evalTaskItemRepository.save(this);
        publish(operatorId);
    }

    /**
     * 更新人工标签 JSON。
     */
    public void updateLabel(String labelJson, String operatorId) {
        initialize(operatorId);
        this.labelJson = labelJson;
        domainValidate();
        evalTaskItemRepository.save(this);
        publish(operatorId);
    }

    private void publish(String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        domainEventPublisher.send(DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(DomainEventConstant.EVAL_TASK_ITEM_SAVED)
                .data(this)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build());
    }
}
