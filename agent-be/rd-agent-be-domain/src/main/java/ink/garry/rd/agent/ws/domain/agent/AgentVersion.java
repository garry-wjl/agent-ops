package ink.garry.rd.agent.ws.domain.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentVersionGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentVersionRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.Version;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.facade.agent.AgentDomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 版本实体（v3.0：版本模型重构后承载草稿 + 在线 + 历史三态）。
 * <p>
 * v3.0 状态机（{@link AgentVersionStatus}）：
 * <ul>
 *   <li>{@link AgentVersionStatus#DRAFT}：草稿态；{@code versionNum / changeLevel / remark / publishedBy /
 *       publishedAt / version} 全为 NULL；{@code current=false}；{@code editorUserId + lockUntil} 用于编辑锁。</li>
 *   <li>{@link AgentVersionStatus#PUBLISHED}：当前在线版本；{@code current=true}。</li>
 *   <li>{@link AgentVersionStatus#ARCHIVED}：历史已发布版本；{@code current=false}；版本号永久保留。</li>
 * </ul>
 * 已发布版本永久保留；切换在线版本通过 application 层在事务内翻转 {@code status / current}。
 * 回滚 = 复制历史 snapshot 生成新草稿走标准发布流程，版本号永远单调递增。
 */
@Getter
@Setter
public class AgentVersion extends DomainEntity implements ink.garry.rd.agent.ws.facade.domain.PublisherAware {

    /** 版本业务编号，前缀 AVN */
    private String num;
    /** 关联的 Agent 业务编号，跨聚合引用 ID */
    private String agentNum;
    /**
     * v3.0：版本状态（DRAFT / PUBLISHED / ARCHIVED）；同 agentNum 至多 1 个 DRAFT、至多 1 个 PUBLISHED。
     */
    private AgentVersionStatus status;
    /** 版本号字符串，形如 v1.0.0；DRAFT 时为 NULL，发布时按 patch+1 计算后赋值 */
    private String versionNum;
    /** 版本号值对象（major/minor/patch）；DRAFT 时为 NULL；持久化时折算为 versionNum + semver_* 列 */
    private Version version;
    /**
     * 配置快照。
     * <p>
     * DRAFT 态：可写；编辑动作直接 UPDATE。PUBLISHED / ARCHIVED 态：不可变。
     */
    private ConfigSnapshot configSnapshot;
    /** 发布备注，发布态长度 ≥ 10 字符；DRAFT 时为 NULL */
    private String remark;
    /** 发布人 userId；DRAFT 时为 NULL */
    private String publishedBy;
    /** 发布时间，仅在 DRAFT → PUBLISHED 翻转时赋值；DRAFT 时为 NULL */
    private LocalDateTime publishedAt;
    /** 是否为当前在线版本；切换在线版本只翻转该标记，不动 snapshot */
    private boolean current;
    /** v3.0：当前编辑者 userId（仅 DRAFT 行使用） */
    private String editorUserId;
    /** v3.0：草稿编辑锁过期时间（仅 DRAFT 行使用） */
    private LocalDateTime lockUntil;

    /** 装配依赖：版本仓储 */
    private transient AgentVersionRepository agentVersionRepository;
    /** 装配依赖：版本聚合网关（生成业务编号 + 读能力） */
    private transient AgentVersionGateway agentVersionGateway;
    /** 装配依赖：领域事件发布器 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（供框架反序列化使用） */
    public AgentVersion() {}

    /**
     * 版本规则校验：
     * <ul>
     *   <li>{@code agentNum} / {@code status} / {@code configSnapshot} 三字段任何状态都必填；</li>
     *   <li>非 DRAFT 态：{@code version / versionNum / remark(≥10 字符) / publishedBy} 必填；</li>
     *   <li>DRAFT 态：上述发布字段必为 NULL（由 service / factory 赋值时保证，不在此强校验，避免误伤兼容入口）。</li>
     * </ul>
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(agentNum, "agentNum 不能为空");
        Assert.notNull(status, "status 不能为空");
        Assert.notNull(configSnapshot, "configSnapshot 不能为空");
        if (status != AgentVersionStatus.DRAFT) {
            Assert.notNull(version, "已发布版本 version 不能为空");
            Assert.notBlank(versionNum, "已发布版本 versionNum 不能为空");
            Assert.notBlank(remark, "已发布版本 remark 不能为空");
            Assert.isTrue(remark.length() >= 10, "已发布版本 remark 长度需 ≥ 10 字符");
            Assert.notBlank(publishedBy, "已发布版本 publishedBy 不能为空");
        }
    }

    /**
     * 持久化版本（v3.0）：
     * <ul>
     *   <li>DRAFT 态：INSERT/UPDATE 都允许；不发 AGENT_VERSION_PUBLISHED 事件（草稿编辑事件由 application 层
     *       自行决定是否发 AGENT_DRAFT_SAVED）；</li>
     *   <li>PUBLISHED / ARCHIVED 态：仅 application 发布事务内翻转 status / current 时调用；
     *       发 AGENT_VERSION_PUBLISHED 事件（仅在 status=PUBLISHED 且 publishedAt 非空时）。</li>
     * </ul>
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化对象
        this.initialize(operatorId);

        // 2. 领域规则：仅发布态自动补 publishedAt
        if (this.status != AgentVersionStatus.DRAFT && this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }

        // 3. 赋值：num 生成、versionNum 自动同步（仅非 DRAFT）
        if (StrUtil.isBlank(this.num)) {
            this.num = agentVersionGateway.generateAgentVersionNum();
        }
        if (this.status != AgentVersionStatus.DRAFT && this.version != null && StrUtil.isBlank(this.versionNum)) {
            this.versionNum = this.version.toStr();
        }

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        agentVersionRepository.save(this);

        // 6. 发布事件（仅 PUBLISHED 态发 AGENT_VERSION_PUBLISHED）
        if (this.status == AgentVersionStatus.PUBLISHED) {
            domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_VERSION_PUBLISHED, operatorId));
        }
    }

    /**
     * 删除版本：
     * <ul>
     *   <li>DRAFT 态：物理删除（草稿无审计价值，与 v2.x AgentDraftRepositoryImpl 修复同思路；
     *       由 RepositoryImpl 内部 mapper.deleteById 旁路 logic-delete 实现）；</li>
     *   <li>PUBLISHED / ARCHIVED 态：仅在归档场景使用，常规流程不删历史版本。</li>
     * </ul>
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 校验
        Assert.notBlank(this.num, "num 不能为空");
        // 3. 赋值（DRAFT 物理删除由 RepositoryImpl 处理；非 DRAFT 走 logic-delete）
        this.deleted = 1;
        // 4. 完整性校验
        this.validate();
        // 5. 持久化删除
        agentVersionRepository.deleteByNum(this.num);
        // 6. 发布事件
        String eventType = this.status == AgentVersionStatus.DRAFT
                ? DomainEventConstant.AGENT_DRAFT_DISCARDED
                : "AGENT_VERSION_DELETED";
        domainEventPublisher.send(buildEvent(eventType, operatorId));
    }

    /** 构建版本领域事件载荷（携带 agentNum / versionNum） */
    private DomainEventDTO buildEvent(String type, String operatorId) {
        AgentDomainEventDTO payload = AgentDomainEventDTO.builder()
                .agentNum(this.agentNum)
                .versionNum(this.versionNum)
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
        return DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(payload)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
    }
}
