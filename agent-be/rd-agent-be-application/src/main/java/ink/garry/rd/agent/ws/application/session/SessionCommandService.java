package ink.garry.rd.agent.ws.application.session;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.session.dto.MessageDTO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import ink.garry.rd.agent.ws.domain.session.Message;
import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.factory.SessionFactory;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.domain.session.valueobject.StepChain;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Session 命令服务：负责会话创建 / 重命名 / 删除等写入用例编排。
 * <p>
 * <b>Agent 元数据访问约定</b>：本服务对 Agent 仅做只读校验（存在性 + 状态），通过
 * {@link AgentQueryService#findAgentByNum(String)} 拿到 {@link AgentDTO}；不再依赖
 * {@code AgentFactory} 与 {@code AgentVersionGateway}，避免越界装配重聚合根、
 * 也避免把 A2A / CONFIG 分支逻辑泄漏到会话用例。
 * <p>
 * <b>出参类型</b>：本 CommandService 返回 {@link SessionDTO}，Controller 出参的 VO 转换
 * 由 adapter 层 {@code SessionCommonAssembler} 负责。
 * <p>
 * <b>聚合装配</b>：Session / InvocationTrace 等聚合的 Repository / Gateway / Publisher
 * 装配统一由 {@link SessionFactory} 在 create / createByNum 时完成（v3.x 反转 §4.7），
 * 本服务不再持有 DomainEventPublisher 也不再写 wire helper。
 */
@Service
@RequiredArgsConstructor
public class SessionCommandService {

    /** A2A 模式下远端版本号缺失时的占位符（v2.6 PRD：A2A 不参与平台 Semver） */
    private static final String A2A_REMOTE_VERSION_PLACEHOLDER = "a2a-remote";

    /** 标题为空时的默认值 */
    private static final String DEFAULT_TITLE = "未命名会话";

    private final SessionFactory sessionFactory;
    private final AgentQueryService agentQueryService;

    /**
     * 创建会话：校验 Agent 状态后落库；A2A / CONFIG 分支由 {@link #resolveSessionVersionNum} 内化。
     * <p>
     * 若 {@code title} 为空，则兜底为 {@value #DEFAULT_TITLE}。
     *
     * @param agentNum   绑定的 Agent 业务编号
     * @param skillHint  Skill 提示，可空
     * @param title      会话标题，可空（空时自动设为 {@value #DEFAULT_TITLE}）
     * @param operatorId 操作人 userId
     * @param origin     会话来源：DEBUG_CONSOLE / API
     * @return 新建会话 DTO
     */
    @Transactional
    public SessionDTO createSession(String agentNum, String skillHint, String title, String operatorId, String origin) {
        AgentDTO agent = agentQueryService.findAgentByNum(agentNum);
        if (!AgentStatus.PUBLISHED.name().equals(agent.getStatus())) {
            throw new BusinessException(BizCode.AGENT_OFFLINED.getCode(), "Agent 未发布或已下线");
        }
        if (StrUtil.isBlank(title)) {
            title = DEFAULT_TITLE;
        }
        String sessionVersionNum = resolveSessionVersionNum(agent);
        Session session = sessionFactory.createSession(agentNum, sessionVersionNum, skillHint, operatorId, title, origin);
        session.save(operatorId);
        return toDTO(session);
    }

    /**
     * 重命名会话。
     *
     * @param sessionNum 会话业务编号
     * @param newTitle   新标题
     * @param operatorId 操作人 userId
     */
    @Transactional
    public void rename(String sessionNum, String newTitle, String operatorId) {
        Session session = requireSession(sessionNum);
        session.rename(newTitle, operatorId);
    }

    /**
     * 删除会话（级联删消息 / trace）。
     *
     * @param sessionNum 会话业务编号
     * @param operatorId 操作人 userId
     */
    @Transactional
    public void delete(String sessionNum, String operatorId) {
        Session session = requireSession(sessionNum);
        session.delete(operatorId);
    }

    /**
     * 追加一条用户消息，并刷新会话 lastMessageAt。
     * <p>
     * 仅会话归属人（operatorId == creatorUserId）可调用，权限由聚合根
     * {@link Session#appendUserMessage} 内置校验。
     *
     * @param sessionNum 会话业务编号
     * @param content    消息正文
     * @param inputType  输入类型（TEXT / IMAGE / VOICE 等）
     * @param traceId    本轮 invoke 的 traceId，可空
     * @param operatorId 操作人 userId，必须等于 creatorUserId
     * @return 新追加的消息 DTO
     */
    @Transactional
    public MessageDTO appendUserMessage(String sessionNum, String content, InputType inputType,
                                        String traceId, String operatorId) {
        Session session = requireSession(sessionNum);
        Message message = session.appendUserMessage(content, inputType, traceId, operatorId);
        return toMessageDTO(message);
    }

    /**
     * 追加一条助手（ASSISTANT）消息，可携带思维链；不做归属人校验（系统侧调用）。
     *
     * @param sessionNum 会话业务编号
     * @param content    助手回复内容
     * @param stepChain  Agent 内部思维链，可空
     * @param traceId    本轮 invoke 的 traceId
     * @param operatorId 操作人 userId（通常为系统侧或调用人）
     * @return 新追加的消息 DTO
     */
    @Transactional
    public MessageDTO appendAssistantMessage(String sessionNum, String content, StepChain stepChain,
                                             String traceId, String operatorId) {
        return appendAssistantMessage(sessionNum, content, stepChain, null, null, traceId, operatorId);
    }

    /**
     * 追加一条助手（ASSISTANT）消息,可携带段列表 + 原始 ContentBlock JSON,
     * 用于历史消息完整渲染。不做归属人校验（系统侧调用）。
     *
     * @param sessionNum         会话业务编号
     * @param content            助手回复内容(所有 text 段拼接,旧字段兼容)
     * @param stepChain          思维链节点序列,典型由 SegmentAccumulator.toStepChain() 派生
     * @param segments           按到达顺序的段列表(thinking/text/tool_use);FE 历史路径直接渲染
     * @param contentBlocksJson  AgentScope Msg.content 原始 ContentBlock 列表 JSON,trace 导出兜底
     * @param traceId            本轮 invoke 的 traceId
     * @param operatorId         操作人 userId
     * @return 新追加的消息 DTO
     */
    @Transactional
    public MessageDTO appendAssistantMessage(String sessionNum, String content, StepChain stepChain,
                                             List<AssistantSegment> segments,
                                             String contentBlocksJson,
                                             String traceId, String operatorId) {
        Session session = requireSession(sessionNum);
        Message message = session.appendAssistantMessage(content, stepChain, segments, contentBlocksJson, traceId, operatorId);
        return toMessageDTO(message);
    }

    private Session requireSession(String sessionNum) {
        Session session = sessionFactory.createByNum(sessionNum);
        if (session == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return session;
    }

    /**
     * 根据 Agent 元数据派生本次会话所绑定的版本号。
     * <p>
     * A2A 模式：取远端 Agent Card.version；缺失时使用占位符 {@value #A2A_REMOTE_VERSION_PLACEHOLDER}。<br>
     * CONFIG 模式：取 {@code agent.currentVersionNum}；按现行规则缺失不抛异常，原样透传。
     */
    private String resolveSessionVersionNum(AgentDTO agent) {
        if (CreationMode.A2A.name().equals(agent.getCreationMode())) {
            AgentDTO.A2aSource src = agent.getA2aSource();
            return src != null && StrUtil.isNotBlank(src.getRemoteVersion())
                    ? src.getRemoteVersion()
                    : A2A_REMOTE_VERSION_PLACEHOLDER;
        }
        return agent.getCurrentVersionNum();
    }

    /** Session 聚合 → SessionDTO 字段拷贝（仅 application 层内部使用）。 */
    private SessionDTO toDTO(Session session) {
        return SessionDTO.builder()
                .id(session.getId())
                .num(session.getNum())
                .agentNum(session.getAgentNum())
                .agentVersionNum(session.getAgentVersionNum())
                .skillHint(session.getSkillHint())
                .creatorUserId(session.getCreatorUserId())
                .title(session.getTitle())
                .lastMessageAt(session.getLastMessageAt())
                .origin(session.getOrigin())
                .createNo(session.getCreateNo())
                .updateNo(session.getUpdateNo())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    /**
     * Message 聚合 → MessageDTO 字段拷贝；枚举以 {@code name()} 落 String，
     * stepChain 用 fastjson 序列化为 JSON 串原样透传。
     */
    private MessageDTO toMessageDTO(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .num(message.getNum())
                .sessionNum(message.getSessionNum())
                .role(message.getRole() == null ? null : message.getRole().name())
                .inputType(message.getInputType() == null ? null : message.getInputType().name())
                .content(message.getContent())
                .stepChainJson(message.getStepChain() == null ? null : JSON.toJSONString(message.getStepChain()))
                .traceId(message.getTraceId())
                .createNo(message.getCreateNo())
                .updateNo(message.getUpdateNo())
                .createTime(message.getCreateTime())
                .updateTime(message.getUpdateTime())
                .build();
    }
}
