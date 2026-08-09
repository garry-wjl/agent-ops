package ink.garry.rd.agent.ws.domain.session;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionCascadeGateway;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionNumGateway;
import ink.garry.rd.agent.ws.domain.session.repository.MessageRepository;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.domain.session.valueobject.MessageRole;
import ink.garry.rd.agent.ws.domain.session.valueobject.StepChain;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 调试台会话聚合根。
 * 表示用户与某个 Agent 版本的一次会话上下文，下挂 Message 与 InvocationTrace，
 * 负责创建会话、追加消息以及级联删除。
 */
@Getter
@Setter
public class Session extends DomainEntity implements ink.garry.rd.agent.ws.facade.domain.PublisherAware {
    /** 会话业务编号，全局唯一，由 SessionNumGateway 生成。 */
    private String num;
    /** 会话所绑定的 Agent 业务编号。 */
    private String agentNum;
    /** 会话所绑定的 Agent 版本编号，会话生命周期内不变。 */
    private String agentVersionNum;
    /** 调试台 Skill 提示，用于在会话中提示当前命中的技能（可空）。 */
    private String skillHint;
    /** 会话创建人用户 ID，作为会话归属与权限校验依据。 */
    private String creatorUserId;
    /** 会话标题，长度上限 128 字符；可由系统生成或用户重命名。 */
    private String title;
    /** 最近一条消息时间，用于会话列表排序与活跃度展示。 */
    private LocalDateTime lastMessageAt;

    /** 标题为空时的默认显示值，首次用户消息到达后会被替换为 input 前 20 字。 */
    private static final String DEFAULT_TITLE = "未命名会话";

    /** 会话来源：DEBUG_CONSOLE（调试台） / API（Open API）；创建时由入口自动写入。 */
    private String origin;

    /**
     * 会话默认调用上下文 JSON object 字符串（可空）。
     * 供系统提示词变量替换继承；格式与大小由应用层校验后再写入。
     */
    private String invokeContextJson;

    /** 装配依赖：会话仓储，用于持久化与查询会话本体。 */
    private transient SessionRepository sessionRepository;
    /** 装配依赖：消息仓储，注入到下游 Message 实体用于持久化。 */
    private transient MessageRepository messageRepository;
    /** 装配依赖：编号生成网关，提供 sessionNum 与 messageNum。 */
    private transient SessionNumGateway sessionNumGateway;
    /** 装配依赖：级联删除网关，删除会话时清理其消息与调用链。 */
    private transient SessionCascadeGateway sessionCascadeGateway;
    /** 装配依赖：领域事件发布器，发布会话创建事件（可空，缺失时跳过发布）。 */
    private transient DomainEventPublisher domainEventPublisher;

    /**
     * 校验聚合不变量：必填编号/Agent/版本/创建人，标题长度不超过 128。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(num, "会话编号不能为空");
        Assert.notBlank(agentNum, "Agent 编号不能为空");
        Assert.notBlank(agentVersionNum, "Agent 版本不能为空");
        Assert.notBlank(creatorUserId, "会话创建人不能为空");
        Assert.notBlank(origin, "会话来源不能为空");
        Assert.isTrue("DEBUG_CONSOLE".equals(origin) || "API".equals(origin),
                "会话来源必须为 DEBUG_CONSOLE 或 API");
        if (StrUtil.isNotBlank(title)) {
            Assert.isTrue(title.length() <= 128, "会话标题不能超过 128 字");
        }
    }

    /**
     * 保存会话：首次保存时自动生成 num，并在装配了发布器时发出 SESSION_CREATED 事件。
     * 事件载荷使用 Map 承载（按技术方案有意为之，避免在领域层耦合 DTO）。
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化对象（审计字段）
        initialize(operatorId);
        // 2. 领域规则校验（标题长度等 → domainValidate）
        // 3. 赋值 / 状态流转：num 为空则调网关生成
        if (StrUtil.isBlank(num)) {
            num = sessionNumGateway.generateSessionNum();
        }
        // 4. 领域完整性校验
        validate();
        // 5. 持久化
        sessionRepository.save(this);
        // 6. 发布领域事件
        if (domainEventPublisher != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionNum", num);
            payload.put("agentNum", agentNum);
            payload.put("agentVersionNum", agentVersionNum);
            payload.put("operatorId", operatorId);
            payload.put("occurredAt", LocalDateTime.now());
            domainEventPublisher.send(DomainEventDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .type(DomainEventConstant.SESSION_CREATED)
                    .data(payload)
                    .time(System.currentTimeMillis())
                    .sender(operatorId)
                    .build());
        }
    }

    /**
     * 删除会话：仅会话归属人可删除；置 deleted=1 后级联删除 Message 与 InvocationTrace，并发出 SESSION_DELETED 事件。
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化对象 + 归属人校验
        assertOwner(operatorId);
        initialize(operatorId);
        // 2. 领域规则校验（domainValidate）
        // 3. 赋值 / 状态流转：标记软删除
        deleted = 1;
        // 4. 领域完整性校验
        validate();
        // 5. 持久化：级联删除子表 + 删除本聚合
        if (sessionCascadeGateway != null) {
            sessionCascadeGateway.deleteMessagesBySessionNum(num);
            sessionCascadeGateway.deleteTracesBySessionNum(num);
        }
        sessionRepository.deleteByNum(num);
        // 6. 发布领域事件
        if (domainEventPublisher != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionNum", num);
            payload.put("agentNum", agentNum);
            payload.put("operatorId", operatorId);
            payload.put("occurredAt", LocalDateTime.now());
            domainEventPublisher.send(DomainEventDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .type(DomainEventConstant.SESSION_DELETED)
                    .data(payload)
                    .time(System.currentTimeMillis())
                    .sender(operatorId)
                    .build());
        }
    }

    /**
     * 重命名会话标题，仅会话归属人可执行。
     *
     * @param newTitle    新标题，非空且不超过 128 字
     * @param operatorId  操作人用户 ID，必须等于 creatorUserId
     */
    public void rename(String newTitle, String operatorId) {
        assertOwner(operatorId);
        Assert.notBlank(newTitle, "会话标题不能为空");
        Assert.isTrue(newTitle.length() <= 128, "会话标题不能超过 128 字");
        initialize(operatorId);
        title = newTitle;
        validate();
        sessionRepository.save(this);
    }

    /**
     * 更新会话默认调用上下文，仅会话归属人可执行。
     *
     * @param json       JSON object 字符串；可空（清空）
     * @param operatorId 操作人用户 ID，必须等于 creatorUserId
     */
    public void updateInvokeContext(String json, String operatorId) {
        assertOwner(operatorId);
        initialize(operatorId);
        this.invokeContextJson = json;
        validate();
        sessionRepository.save(this);
    }

    /**
     * 追加用户消息（角色 USER），并刷新 lastMessageAt。
     * <p>
     * <b>自动标题回填</b>：若当前标题仅为 {@value #DEFAULT_TITLE}，则将消息内容的
     * 前 20 个字符设为新标题，确保首次输入后会话名称有意义。
     *
     * @param content     消息内容
     * @param inputType   输入类型（文本/图片/语音等），用户消息必填
     * @param traceId     可选，关联本轮 invoke 的 traceId
     * @param operatorId  操作人，必须为会话归属人
     * @return 已落库的 Message 实体
     */
    public Message appendUserMessage(String content, InputType inputType, String traceId, String operatorId) {
        assertOwner(operatorId);
        // 若标题为默认值且消息内容不为空，则用消息内容的前 20 字符自动替换标题
        if (strTitleIsDefault() && StrUtil.isNotBlank(content)) {
            rename(StrUtil.sub(content, 0, 20), operatorId);
        }
        Message message = buildMessage(MessageRole.USER, content, inputType, null, traceId);
        message.save(operatorId);
        touchLastMessageAt(operatorId);
        return message;
    }

    /** 判断当前标题是否为默认占位符（"未命名会话"）或为空。 */
    private boolean strTitleIsDefault() {
        return StrUtil.isBlank(title) || DEFAULT_TITLE.equals(title);
    }

    /**
     * 追加助手消息（角色 ASSISTANT），可携带思维链，并刷新 lastMessageAt。
     * 该方法由系统侧调用，不强制 owner 校验。
     *
     * @param content     助手回复内容
     * @param stepChain   思维链节点序列，可空
     * @param traceId     关联本轮 invoke 的 traceId
     * @param operatorId  操作人（通常为系统/调用人）
     * @return 已落库的 Message 实体
     */
    /**
     * 追加助手消息（角色 ASSISTANT），可携带思维链、按到达顺序的段列表与原始 ContentBlock JSON,
     * 并刷新 lastMessageAt。该方法由系统侧调用，不强制 owner 校验。
     *
     * @param content            助手回复内容(text 总和,用于旧字段兼容)
     * @param stepChain          思维链节点序列,可空(典型从 segments 中的 tool_use 段派生)
     * @param segments           按到达顺序的段列表(thinking / text / tool_use),可空
     * @param contentBlocksJson  AgentScope Msg.content 原始 ContentBlock 列表的 JSON 字符串,可空;
     *                           仅作 trace 导出 / 评测复用兜底,不返 FE
     * @param traceId            关联本轮 invoke 的 traceId
     * @param operatorId         操作人（通常为系统/调用人）
     * @return 已落库的 Message 实体
     */
    public Message appendAssistantMessage(String content, StepChain stepChain,
                                          List<AssistantSegment> segments,
                                          String contentBlocksJson,
                                          String traceId, String operatorId) {
        Message message = buildMessage(MessageRole.ASSISTANT, content, null, stepChain, traceId);
        message.setSegments(segments);
        message.setContentBlocksJson(contentBlocksJson);
        message.save(operatorId);
        touchLastMessageAt(operatorId);
        return message;
    }

    /**
     * 旧签名重载,保留以兼容未迁移到 segments 的调用方。新代码应优先使用带 segments 的重载。
     */
    public Message appendAssistantMessage(String content, StepChain stepChain, String traceId, String operatorId) {
        return appendAssistantMessage(content, stepChain, null, null, traceId, operatorId);
    }

    /** 构造一条新消息，分配 messageNum 并装配仓储与事件发布器（透传父聚合的 publisher 给子实体）。 */
    private Message buildMessage(MessageRole role, String content, InputType inputType, StepChain stepChain, String traceId) {
        Message message = new Message();
        message.setNum(sessionNumGateway.generateMessageNum());
        message.setSessionNum(num);
        message.setRole(role);
        message.setInputType(inputType);
        message.setContent(content);
        message.setStepChain(stepChain);
        message.setTraceId(traceId);
        message.setMessageRepository(messageRepository);
        message.setDomainEventPublisher(domainEventPublisher);
        return message;
    }

    /** 刷新 lastMessageAt 并落库，用于会话列表的活跃排序。 */
    private void touchLastMessageAt(String operatorId) {
        initialize(operatorId);
        lastMessageAt = LocalDateTime.now();
        sessionRepository.save(this);
    }

    /** 校验操作人是否为会话归属人，不通过则抛出权限异常。 */
    private void assertOwner(String operatorId) {
        Assert.isTrue(operatorId != null && operatorId.equals(creatorUserId), "无权限操作该会话");
    }
}
