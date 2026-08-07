package ink.garry.rd.agent.ws.domain.skillcheck;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.skillcheck.dto.SkillCheckRecordEventDTO;
import ink.garry.rd.agent.ws.domain.skillcheck.gateway.SkillCheckRecordGateway;
import ink.garry.rd.agent.ws.domain.skillcheck.repository.SkillCheckRecordRepository;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckError;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItemResult;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SkillCheckRecord 聚合根（v3.0 新增）。
 * <p>
 * 表示一次 Skill 发布检测的结果留痕。每次发布触发的大小 / 格式 / 可用性三检，无论
 * 通过（PASS）还是不通过（FAIL）都生成一条本记录入库，供 Skill 详情「检测记录」页查看与排查。
 * <p>
 * 由 application 层（{@code SkillCommandService.publish}）在检测完成后通过
 * {@code SkillCheckRecordFactory.buildCheckRecord} 创建并落库；记录一旦保存原则上不可变
 * （检测不通过的记录长期保留，不随重新发布删除）。
 * <p>
 * <b>领域方法</b>：{@link #save(String)} / {@link #delete(String)}。
 */
@Getter
@Setter
public class SkillCheckRecord extends DomainEntity {

    // ---- 业务字段 ----

    /** 检测记录业务编号（前缀 SCR，由 {@link SkillCheckRecordGateway#generateCheckRecordNum()} 生成）。 */
    private String num;

    /** 所属 Skill 业务编号。 */
    private String skillNum;

    /** 检测的目标版本号。 */
    private String version;

    /** 整体检测结果（PASS / FAIL）。 */
    private SkillCheckResult result;

    /** 大小检测子结果。 */
    private SkillCheckItemResult sizeResult;

    /** 格式检测子结果。 */
    private SkillCheckItemResult formatResult;

    /** 可用性检测子结果。 */
    private SkillCheckItemResult availabilityResult;

    /** 错误明细列表；result=FAIL 时非空。 */
    private List<SkillCheckError> errors;

    /** 检测总耗时（毫秒）。 */
    private Long costMs;

    /** 归属工作空间业务编号（前缀 WS-）。 */
    private String workspaceNum;

    // ---- 装配依赖 ----

    /** 装配依赖：检测记录仓储（save / findByNum / deleteByNum）。 */
    private transient SkillCheckRecordRepository skillCheckRecordRepository;
    /** 装配依赖：检测记录网关（业务编号生成）。 */
    private transient SkillCheckRecordGateway skillCheckRecordGateway;
    /** 装配依赖：领域事件发布器，由 {@code SkillCheckRecordFactory} 装配。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（Mapper / Factory 调用）。 */
    public SkillCheckRecord() {
    }

    // ---- 抽象方法实现 ----

    /**
     * 检测记录不变量：skillNum / version / result / 三项子结果必填；
     * result=FAIL 时 errors 必须非空。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(skillNum, "检测记录所属 skillNum 不能为空");
        Assert.notBlank(version, "检测记录目标版本号不能为空");
        Assert.notNull(result, "检测整体结果不能为空");
        Assert.notNull(sizeResult, "大小检测结果不能为空");
        Assert.notNull(formatResult, "格式检测结果不能为空");
        Assert.notNull(availabilityResult, "可用性检测结果不能为空");
        if (result == SkillCheckResult.FAIL) {
            Assert.notEmpty(errors, "检测不通过时错误明细不能为空");
        }
    }

    /**
     * 保存检测记录（一般为首次 INSERT）。
     * <p>
     * 六步顺序：(1) 初始化审计字段 → (2) 无前置规则 → (3) 赋值（值对象初始化 + num 生成）
     * → (4) 完整性校验 → (5) 持久化 → (6) 发布 {@code SKILL_CHECK_RECORDED} 事件。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：无前置状态约束

        // 3. 赋值：值对象初始化 + num 生成
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = skillCheckRecordGateway.generateCheckRecordNum();
        }

        // 4. 完整性校验
        this.validate();

        // 5. 持久化（仅 INSERT；errors 由 RepositoryImpl 序列化为 JSON 列）
        skillCheckRecordRepository.save(this);

        // 6. 发布事件（每次 save 必发）
        publishEvent(DomainEventConstant.SKILL_CHECK_RECORDED, operatorId);
    }

    /**
     * 软删除检测记录（标记 deleted=1）。
     * <p>
     * 仅在历史记录清理场景使用；常规业务流程不主动删除检测记录（失败记录需长期保留供排查）。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 num 非空 → (3) 赋值 deleted=1
     * → (4) 完整性校验 → (5) 持久化删除 → (6) 发布 {@code SKILL_CHECK_RECORD_DELETED} 事件。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则：num 必须已存在
        Assert.notBlank(this.num, "检测记录业务编号不能为空");

        // 3. 赋值
        this.deleted = 1;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化删除
        skillCheckRecordRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_CHECK_RECORD_DELETED, operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     *
     * @param type       事件类型常量
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 由调用链上游约束（本方法不主动抛出）
     */
    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(SkillCheckRecordEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
