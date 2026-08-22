package ink.garry.rd.agent.ws.domain.evaluation.dataset;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.gateway.EvalDatasetGateway;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.repository.EvalDatasetRepository;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetStatus;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetType;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.domain.PublisherAware;
import ink.garry.rd.agent.ws.facade.evaluation.EvalDomainEventDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 评测集聚合根：草稿元数据、schema、发布版本协作。
 * <p>软删除标记不在领域业务字段中表达；删除经仓储软删 Entity。
 */
@Getter
@Setter
public class EvalDataset extends DomainEntity implements PublisherAware {

    /** 评测集业务编号（EDS）。 */
    private String num;
    /** 归属工作空间编号。 */
    private String workspaceNum;
    /** 名称。 */
    private String name;
    /** 描述。 */
    private String description;
    /** 类型（创建后不可改）。 */
    private DatasetType type;
    /** 关联 Agent 编号（AGENT 型必填）。 */
    private String agentNum;
    /** 表结构 JSON。 */
    private String schemaJson;
    /** 状态。 */
    private DatasetStatus status;
    /** 最新已发布版本号；0 表示尚未发布。 */
    private Integer latestVersion;

    /** 装配：仓储 */
    private transient EvalDatasetRepository evalDatasetRepository;
    /** 装配：编号网关 */
    private transient EvalNumGateway evalNumGateway;
    /** 装配：行/版本协作网关 */
    private transient EvalDatasetGateway evalDatasetGateway;
    /** 装配：事件发布器 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认构造。 */
    public EvalDataset() {
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "评测集名称不能为空");
        Assert.notNull(type, "评测集类型不能为空");
        Assert.notBlank(schemaJson, "schemaJson 不能为空");
        Assert.notNull(status, "评测集状态不能为空");
        if (type == DatasetType.AGENT) {
            Assert.notBlank(agentNum, "Agent 型评测集必须指定 agentNum");
        }
        if (latestVersion == null) {
            latestVersion = 0;
        }
    }

    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (status == null) {
            status = DatasetStatus.DRAFT;
        }
        if (latestVersion == null) {
            latestVersion = 0;
        }
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateDatasetNum();
        }
        domainValidate();
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
    }

    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        int running = evalDatasetGateway.countRunningTasksByDataset(num);
        Assert.isTrue(running == 0, "存在运行中的评测任务引用，无法删除");
        evalDatasetRepository.deleteByNum(num);
        publish(DomainEventConstant.DATASET_DELETED, operatorId, null);
    }

    /**
     * 更新草稿元数据 / schema（仅 DRAFT 或已发布后继续改草稿 schema 时允许；status 保持，已发布后仍可改草稿再发）。
     * <p>规则：仅当尚未绑定不可变 type 之外的字段；type 创建后不可改。
     */
    public void updateDraft(String name, String description, String schemaJson, String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        if (StrUtil.isNotBlank(name)) {
            this.name = name;
        }
        this.description = description;
        if (StrUtil.isNotBlank(schemaJson)) {
            this.schemaJson = schemaJson;
        }
        domainValidate();
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
    }

    /**
     * 向草稿追加一行。
     *
     * @param dataJson 行 JSON
     * @param operatorId 操作人
     */
    public void appendDraftRow(String dataJson, String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        Assert.notBlank(dataJson, "行数据不能为空");
        domainValidate();
        evalDatasetGateway.appendDraftRow(num, dataJson, operatorId);
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
    }

    /**
     * 向草稿追加一行，返回行编号与下标。
     *
     * @param dataJson 行 JSON
     * @param operatorId 操作人
     * @return [rowNum, rowIndexStr]
     */
    public String[] appendDraftRowWithNum(String dataJson, String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        Assert.notBlank(dataJson, "行数据不能为空");
        domainValidate();
        String[] meta = evalDatasetGateway.appendDraftRowWithNum(num, dataJson, operatorId);
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
        return meta;
    }

    /**
     * 删除一条草稿行（已发布版本行不可删）。
     *
     * @param rowNum 行业务编号
     * @param operatorId 操作人
     */
    public void deleteDraftRow(String rowNum, String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        Assert.notBlank(rowNum, "行编号不能为空");
        domainValidate();
        boolean ok = evalDatasetGateway.deleteDraftRow(num, rowNum, operatorId);
        Assert.isTrue(ok, "草稿行不存在或已发布版本行不可删除");
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
    }

    /**
     * 更新一条草稿行数据（已发布版本行不可改）。
     *
     * @param rowNum 行业务编号
     * @param dataJson 新行 JSON
     * @param operatorId 操作人
     */
    public void updateDraftRow(String rowNum, String dataJson, String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        Assert.notBlank(rowNum, "行编号不能为空");
        Assert.notBlank(dataJson, "行数据不能为空");
        domainValidate();
        boolean ok = evalDatasetGateway.updateDraftRow(num, rowNum, dataJson, operatorId);
        Assert.isTrue(ok, "草稿行不存在或已发布版本行不可修改");
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
    }

    /**
     * 替换草稿行。
     *
     * @param dataJsonList 行 JSON 列表
     * @param operatorId 操作人
     */
    public void replaceDraftRows(List<String> dataJsonList, String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        Assert.notNull(dataJsonList, "行数据不能为 null");
        domainValidate();
        evalDatasetGateway.replaceDraftRows(num, dataJsonList, operatorId);
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_SAVED, operatorId, null);
    }

    /**
     * 发布：固化当前草稿为新版本。
     *
     * @param operatorId 操作人
     * @return 新版本号
     */
    public int publish(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "评测集编号不能为空");
        int draftCount = evalDatasetGateway.countDraftRows(num);
        Assert.isTrue(draftCount >= 1, "发布前至少需要 1 行草稿数据");
        domainValidate();
        int next = (latestVersion == null ? 0 : latestVersion) + 1;
        int rowCount = evalDatasetGateway.publishVersion(num, next, schemaJson, operatorId);
        Assert.isTrue(rowCount >= 1, "发布固化行失败");
        this.latestVersion = next;
        this.status = DatasetStatus.PUBLISHED;
        evalDatasetRepository.save(this);
        publish(DomainEventConstant.DATASET_PUBLISHED, operatorId, next);
        return next;
    }

    private void publish(String type, String operatorId, Integer version) {
        if (domainEventPublisher == null) {
            return;
        }
        EvalDomainEventDTO data = EvalDomainEventDTO.builder()
                .workspaceNum(workspaceNum)
                .status(status == null ? null : status.name())
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
        // 复用 taskNum 字段承载 datasetNum（发布事件）；version 写入 totalCount 便于审计
        data.setTaskNum(num);
        if (version != null) {
            data.setTotalCount(version);
        }
        domainEventPublisher.send(DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(data)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build());
    }
}
