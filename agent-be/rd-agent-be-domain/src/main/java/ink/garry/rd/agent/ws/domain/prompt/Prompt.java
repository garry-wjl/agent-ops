package ink.garry.rd.agent.ws.domain.prompt;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.prompt.dto.PromptDomainEventDTO;
import ink.garry.rd.agent.ws.domain.prompt.gateway.PromptGateway;
import ink.garry.rd.agent.ws.domain.prompt.repository.PromptRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prompt 聚合根（Prompt 中心 v1.0）。
 * <p>
 * 表示一条可被检索、维护、按 Key 稳定引用的提示词资产。承载业务编码、引用键、描述、
 * 模板原文与标签；模板内容中的 {@code {{变量名}}} 占位符<b>原样存储不解析</b>，由调用方在
 * 运行时自行替换（Prompt 中心技术方案 §0 共识 #5）。
 * <p>
 * <b>无状态机</b>：Prompt 不存在草稿 / 发布 / 弃用态，新增即生效、编辑即生效
 * （技术方案 §0 共识 #6）。因此仅两个领域动作：
 * <ul>
 *   <li>{@link #save(String)}：新增 / 编辑 Prompt 元信息（upsert 语义）</li>
 *   <li>{@link #delete(String)}：软删除 Prompt</li>
 * </ul>
 * <p>
 * <b>唯一性</b>：{@code (workspaceNum, num)} 与 {@code (workspaceNum, promptKey)} 两个
 * 工作空间内唯一键属跨行约束，由应用层 QueryService 预检 + DB 唯一索引兜底，不在聚合内校验。
 * <p>
 * <b>事件常量</b>：见 {@link DomainEventConstant#PROMPT_SAVED}、{@link DomainEventConstant#PROMPT_DELETED}。
 */
@Getter
@Setter
public class Prompt extends DomainEntity {

    // ---- 约束常量 ----

    /** Prompt Key 长度上限。 */
    private static final int PROMPT_KEY_MAX_LENGTH = 128;
    /** 描述长度上限。 */
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    /** 模板内容长度上限。 */
    private static final int TEMPLATE_CONTENT_MAX_LENGTH = 20000;
    /** 标签数量上限。 */
    private static final int TAGS_MAX_COUNT = 20;
    /** 单标签长度上限。 */
    private static final int TAG_MAX_LENGTH = 32;

    // ---- 业务字段 ----

    /** Prompt 业务编号（前缀 PRM，由 {@link PromptGateway#generatePromptNum()} 生成）。 */
    private String num;

    /** 归属工作空间业务编号（前缀 WS-）；由 {@link ink.garry.rd.agent.ws.domain.prompt.factory.PromptFactory} 在 build 时注入。 */
    private String workspaceNum;

    /** Prompt 引用键；用户填写，工作空间内唯一（应用层预检 + DB 唯一索引兜底）。 */
    private String promptKey;

    /** Prompt 描述信息；选填，≤500 字符。 */
    private String description;

    /** 模板原文（含 {@code {{变量}}}，原样存储不解析）；必填，≤20000 字符。 */
    private String templateContent;

    /** 自由标签数组；列表 facet 筛选用。 */
    private List<String> tags;

    /** 负责人 / 创建人用户 ID。 */
    private String ownerUserId;

    // ---- 装配依赖（由 PromptFactory 装配） ----

    /** 装配依赖：Prompt 仓储，承担 save / findByNum / deleteByNum 三方法。 */
    private transient PromptRepository promptRepository;
    /** 装配依赖：Prompt 网关（业务编号生成）。 */
    private transient PromptGateway promptGateway;
    /** 装配依赖：领域事件发布器，由 {@code PromptFactory} 装配。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（Lombok 不自动生成；显式声明便于 Mapper / Factory 调用）。 */
    public Prompt() {
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验：promptKey / templateContent 必填；长度与标签约束。
     * <p>
     * num / workspaceNum 由工厂与 save 流程保障，不在此重复强校验。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(promptKey, "Prompt Key 不能为空");
        Assert.isTrue(promptKey.length() <= PROMPT_KEY_MAX_LENGTH,
                "Prompt Key 长度不能超过 {} 字符", PROMPT_KEY_MAX_LENGTH);
        Assert.notBlank(templateContent, "Prompt 模板内容不能为空");
        Assert.isTrue(templateContent.length() <= TEMPLATE_CONTENT_MAX_LENGTH,
                "Prompt 模板内容长度不能超过 {} 字符", TEMPLATE_CONTENT_MAX_LENGTH);
        if (StrUtil.isNotBlank(description)) {
            Assert.isTrue(description.length() <= DESCRIPTION_MAX_LENGTH,
                    "Prompt 描述长度不能超过 {} 字符", DESCRIPTION_MAX_LENGTH);
        }
        Assert.notBlank(ownerUserId, "Prompt 负责人不能为空");
        if (tags != null) {
            Assert.isTrue(tags.size() <= TAGS_MAX_COUNT, "Prompt 标签数量不能超过 {} 个", TAGS_MAX_COUNT);
            for (String tag : tags) {
                Assert.notBlank(tag, "Prompt 标签不能含空白项");
                Assert.isTrue(tag.length() <= TAG_MAX_LENGTH, "Prompt 单个标签长度不能超过 {} 字符", TAG_MAX_LENGTH);
            }
        }
    }

    /**
     * 保存 / 编辑 Prompt 元信息（upsert，不区分新增 / 更新）。
     * <p>
     * 六步顺序：(1) 初始化审计字段 → (2) 无前置状态规则（无状态机）→ (3) 赋值（值对象初始化 +
     * num 生成）→ (4) 领域完整性校验 → (5) 持久化 → (6) 发布事件。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：Prompt 无状态机，save 无前置状态约束

        // 3. 赋值：值对象初始化 + num 生成
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = promptGateway.generatePromptNum();
        }

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化（upsert 语义，不区分新增 / 更新）
        promptRepository.save(this);

        // 6. 发布事件（每次 save 必发，禁止 wasNew 式判断）
        publishEvent(DomainEventConstant.PROMPT_SAVED, operatorId);
    }

    /**
     * 软删除 Prompt。
     * <p>
     * Prompt 无状态机，删除无前置状态约束（与 Skill / Tool 的"发布态不可删"不同）。
     * 六步顺序：(1) 初始化 → (2) 无前置规则 → (3) 置 deleted=1 → (4) 完整性校验 →
     * (5) 软删除 → (6) 发布事件。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则校验：无状态约束，直接放行

        // 3. 赋值：置逻辑删除标记
        this.deleted = 1;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化删除（按 num 软删）
        promptRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.PROMPT_DELETED, operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     *
     * @param type       事件类型常量（见 {@link DomainEventConstant}）
     * @param operatorId 操作人用户 ID
     */
    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(PromptDomainEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
