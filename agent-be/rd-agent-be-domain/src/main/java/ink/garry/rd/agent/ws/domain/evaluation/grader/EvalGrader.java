package ink.garry.rd.agent.ws.domain.evaluation.grader;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.grader.gateway.EvalGraderGateway;
import ink.garry.rd.agent.ws.domain.evaluation.grader.repository.EvalGraderRepository;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.BuiltinGraderCode;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
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
 * 评估器聚合根。
 */
@Getter
@Setter
public class EvalGrader extends DomainEntity implements PublisherAware {

    /** 评估器编号（EGR）。 */
    private String num;
    /** 工作空间编号。 */
    private String workspaceNum;
    /** 名称。 */
    private String name;
    /** 描述。 */
    private String description;
    /** 类型。 */
    private GraderKind kind;
    /** 内置编码（BUILTIN）。 */
    private String builtinCode;
    /** 配置 JSON。 */
    private String configJson;
    /** 配置版本号。 */
    private Integer version;

    private transient EvalGraderRepository evalGraderRepository;
    private transient EvalNumGateway evalNumGateway;
    private transient EvalGraderGateway evalGraderGateway;
    private transient DomainEventPublisher domainEventPublisher;

    public EvalGrader() {
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "评估器名称不能为空");
        Assert.notNull(kind, "评估器类型不能为空");
        Assert.notBlank(configJson, "configJson 不能为空");
        if (kind == GraderKind.BUILTIN) {
            Assert.notBlank(builtinCode, "BUILTIN 评估器必须指定 builtinCode");
            Assert.isTrue(BuiltinGraderCode.isValid(builtinCode), "非法内置评估器编码");
        }
        if (version == null || version < 1) {
            version = 1;
        }
    }

    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (version == null) {
            version = 1;
        }
        if (StrUtil.isBlank(configJson)) {
            configJson = "{}";
        }
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateGraderNum();
        }
        domainValidate();
        evalGraderRepository.save(this);
        publish(DomainEventConstant.GRADER_SAVED, operatorId);
    }

    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评估器编号不能为空");
        int running = evalGraderGateway.countRunningTasksByGrader(num);
        Assert.isTrue(running == 0, "存在运行中的评测任务绑定该评估器，无法删除");
        evalGraderRepository.deleteByNum(num);
        publish(DomainEventConstant.GRADER_DELETED, operatorId);
    }

    /**
     * 更新配置并升版本。
     *
     * @param name 名称
     * @param description 描述
     * @param configJson 配置
     * @param operatorId 操作人
     */
    public void updateConfig(String name, String description, String configJson, String operatorId) {
        initialize(operatorId);
        if (StrUtil.isNotBlank(name)) {
            this.name = name;
        }
        this.description = description;
        if (StrUtil.isNotBlank(configJson)) {
            this.configJson = configJson;
        }
        bumpVersion(operatorId);
    }

    /**
     * 配置变更升版本并落库。
     *
     * @param operatorId 操作人
     */
    public void bumpVersion(String operatorId) {
        initialize(operatorId);
        this.version = (version == null ? 1 : version) + 1;
        domainValidate();
        evalGraderRepository.save(this);
        publish(DomainEventConstant.GRADER_SAVED, operatorId);
    }

    private void publish(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        EvalDomainEventDTO data = EvalDomainEventDTO.builder()
                .taskNum(num)
                .workspaceNum(workspaceNum)
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
