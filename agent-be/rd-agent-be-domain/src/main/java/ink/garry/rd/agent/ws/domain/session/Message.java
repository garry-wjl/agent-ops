package ink.garry.rd.agent.ws.domain.session;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.session.repository.MessageRepository;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.domain.session.valueobject.MessageRole;
import ink.garry.rd.agent.ws.domain.session.valueobject.StepChain;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.domain.PublisherAware;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试台会话消息实体（隶属 Session 聚合）。
 * <p>
 * 表示会话中一条用户/助手消息，可携带思维链与本轮 invoke 的 traceId。
 * 由 {@link Session#appendUserMessage} / {@link Session#appendAssistantMessage} 构造并装配
 * {@link MessageRepository} 与 {@link DomainEventPublisher}。
 */
@Getter
@Setter
public class Message extends DomainEntity implements PublisherAware {
    /** 消息业务编号，全局唯一，由 SessionNumGateway 生成。 */
    private String num;
    /** 所属会话编号，外键关联 Session.num。 */
    private String sessionNum;
    /** 消息角色：USER / ASSISTANT / TOOL。 */
    private MessageRole role;
    /** 用户消息的输入类型（文本/图片/语音等）；助手消息可空。 */
    private InputType inputType;
    /** 消息正文内容。 */
    private String content;
    /** 助手消息的思维链节点序列；用户消息一般为空。 */
    private StepChain stepChain;
    /**
     * 助手消息按到达顺序的段列表(thinking / text / tool_use)。
     * <p>与 FE AssistantSegment 同构,落 message.segments_json (MySQL JSON 列)。
     * 历史消息或用户消息为 null。
     */
    private List<AssistantSegment> segments;
    /**
     * AgentScope Msg.content 原始 ContentBlock 列表序列化后的 JSON 字符串。
     * <p>仅作 trace 导出 / 评测数据复用兜底,不返回给 FE。
     * 落 message.content_blocks_json (MySQL JSON 列)。
     */
    private String contentBlocksJson;
    /** 关联本轮 invoke 的 traceId，用于跳转到 InvocationTrace 详情。 */
    private String traceId;

    /** 装配依赖：消息仓储，由父聚合 Session 注入。 */
    private transient MessageRepository messageRepository;
    /** 装配依赖：领域事件发布器，由 application 在拿到聚合后通过 {@link #setDomainEventPublisher} 装配。 */
    private transient DomainEventPublisher domainEventPublisher;

    /**
     * 校验消息不变量：编号、会话编号、角色必填；用户消息必须有 inputType。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(num, "消息编号不能为空");
        Assert.notBlank(sessionNum, "会话编号不能为空");
        Assert.notNull(role, "消息角色不能为空");
        if (role == MessageRole.USER) {
            Assert.notNull(inputType, "用户消息输入类型不能为空");
        }
    }

    /**
     * 保存消息：每次 save 都发布 MESSAGE_SAVED 事件（不区分新增 / 更新）。
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化对象（审计字段）
        initialize(operatorId);
        // 2. 领域规则校验（消息自身无额外规则，校验前置到 domainValidate）
        // 3. 赋值 / 状态流转（num 由父聚合 Session 在 buildMessage 阶段已分配，本步无操作）
        // 4. 领域完整性校验
        validate();
        // 5. 持久化
        messageRepository.save(this);
        // 6. 发布领域事件
        publish(DomainEventConstant.MESSAGE_SAVED, operatorId);
    }

    /**
     * 软删除消息：置 deleted=1 并按业务编号删除，发布 MESSAGE_DELETED 事件。
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化对象
        initialize(operatorId);
        // 2. 领域规则校验：必须已有业务编号
        Assert.isTrue(StrUtil.isNotBlank(num), "消息编号不能为空");
        // 3. 赋值 / 状态流转：标记软删除
        deleted = 1;
        // 4. 领域完整性校验
        validate();
        // 5. 持久化删除
        messageRepository.deleteByNum(num);
        // 6. 发布领域事件
        publish(DomainEventConstant.MESSAGE_DELETED, operatorId);
    }

    /** 统一发布器：Publisher 缺失时静默跳过，便于本地无 Spring 单测。 */
    private void publish(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageNum", num);
        payload.put("sessionNum", sessionNum);
        payload.put("role", role == null ? null : role.name());
        payload.put("traceId", traceId);
        payload.put("operatorId", operatorId);
        domainEventPublisher.send(DomainEventDTO.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .type(type)
                .data(payload)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build());
    }
}
