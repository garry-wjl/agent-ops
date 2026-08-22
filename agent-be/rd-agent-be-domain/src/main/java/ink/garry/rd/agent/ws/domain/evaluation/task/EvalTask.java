package ink.garry.rd.agent.ws.domain.evaluation.task;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.repository.EvalTaskRepository;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.BindMode;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.domain.PublisherAware;
import ink.garry.rd.agent.ws.facade.evaluation.EvalDomainEventDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 评测任务聚合根：创建后冻结配置；状态机 PENDING→RUNNING→FINISHED/FAILED/CANCELLED。
 */
@Getter
@Setter
public class EvalTask extends DomainEntity implements PublisherAware {

    private String num;
    private String workspaceNum;
    private String name;
    private String description;
    private String datasetNum;
    private Integer datasetVersion;
    private BindMode bindMode;
    private String agentNum;
    private String agentVersionNum;
    /** 评估器绑定快照 JSON */
    private String graderBindingsJson;
    private String labelConfigJson;
    private TaskStatus status;
    private Integer totalCount;
    private Integer passedCount;
    private Integer failedCount;
    private String creatorUserId;

    private transient EvalTaskRepository evalTaskRepository;
    private transient EvalNumGateway evalNumGateway;
    private transient DomainEventPublisher domainEventPublisher;

    public EvalTask() {
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "任务名称不能为空");
        Assert.notBlank(datasetNum, "datasetNum 不能为空");
        Assert.notNull(datasetVersion, "datasetVersion 不能为空");
        Assert.notNull(bindMode, "bindMode 不能为空");
        Assert.notBlank(graderBindingsJson, "graderBindingsJson 不能为空");
        Assert.notBlank(creatorUserId, "creatorUserId 不能为空");
        Assert.notNull(status, "status 不能为空");
        if (bindMode == BindMode.AGENT) {
            Assert.notBlank(agentNum, "AGENT 绑定必须指定 agentNum");
            Assert.notBlank(agentVersionNum, "AGENT 绑定必须指定 agentVersionNum");
        }
    }

    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (status == null) {
            status = TaskStatus.PENDING;
        }
        if (totalCount == null) {
            totalCount = 0;
        }
        if (passedCount == null) {
            passedCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateTaskNum();
        }
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_SAVED, operatorId);
    }

    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "任务编号不能为空");
        Assert.isTrue(status != TaskStatus.RUNNING, "运行中的任务不可删除");
        evalTaskRepository.deleteByNum(num);
        publish(DomainEventConstant.EVAL_TASK_DELETED, operatorId);
    }

    /** PENDING → RUNNING。 */
    public void markRunning(String operatorId) {
        initialize(operatorId);
        Assert.isTrue(status == TaskStatus.PENDING, "仅 PENDING 可进入 RUNNING");
        status = TaskStatus.RUNNING;
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_RUNNING, operatorId);
    }

    /** 汇总完成。 */
    public void finish(int total, int passed, int failed, String operatorId) {
        initialize(operatorId);
        Assert.isTrue(status == TaskStatus.RUNNING || status == TaskStatus.PENDING,
                "仅运行中/待运行任务可 finish");
        status = TaskStatus.FINISHED;
        totalCount = total;
        passedCount = passed;
        failedCount = failed;
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_FINISHED, operatorId);
    }

    /** 失败终态。 */
    public void fail(String operatorId) {
        initialize(operatorId);
        status = TaskStatus.FAILED;
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_FAILED, operatorId);
    }

    /** 取消。 */
    public void cancel(String operatorId) {
        initialize(operatorId);
        Assert.isTrue(status == TaskStatus.RUNNING, "仅 RUNNING 可取消");
        status = TaskStatus.CANCELLED;
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_CANCELLED, operatorId);
    }

    /** 重跑失败项：FINISHED/FAILED → RUNNING。 */
    public void markRerunRunning(String operatorId) {
        initialize(operatorId);
        Assert.isTrue(status == TaskStatus.FINISHED || status == TaskStatus.FAILED,
                "仅 FINISHED/FAILED 可重跑失败项");
        status = TaskStatus.RUNNING;
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_RUNNING, operatorId);
    }

    /** 更新任务级标签配置 JSON。 */
    public void updateLabelConfig(String labelConfigJson, String operatorId) {
        initialize(operatorId);
        this.labelConfigJson = labelConfigJson;
        domainValidate();
        evalTaskRepository.save(this);
        publish(DomainEventConstant.EVAL_TASK_SAVED, operatorId);
    }

    private void publish(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        EvalDomainEventDTO data = EvalDomainEventDTO.builder()
                .taskNum(num)
                .workspaceNum(workspaceNum)
                .status(status == null ? null : status.name())
                .totalCount(totalCount)
                .passedCount(passedCount)
                .failedCount(failedCount)
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
        domainEventPublisher.send(DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(data)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build());
    }
}
