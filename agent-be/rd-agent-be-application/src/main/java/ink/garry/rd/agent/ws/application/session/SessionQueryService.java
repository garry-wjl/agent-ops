package ink.garry.rd.agent.ws.application.session;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.application.common.prompt.SysPromptVariableSubstitutor;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.session.AssistantSegmentVO;
import ink.garry.rd.agent.ws.client.session.MessageVO;
import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
import ink.garry.rd.agent.ws.client.session.SessionTurnVO;
import ink.garry.rd.agent.ws.client.session.StepChainVO;
import ink.garry.rd.agent.ws.client.session.StepNodeVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.session.entity.MessageEntity;
import ink.garry.rd.agent.ws.infra.session.entity.SessionEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.MessageMapper;
import ink.garry.rd.agent.ws.infra.session.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话查询服务：按 §5.1 / §5.3 新规直接走 infra Mapper，不再经 ReadGateway 跳转。
 * <p>
 * 复杂度边界：单表分页 / 单表列表 / 单会话归属校验；无跨表 join，故不需新建 XxxQueryMapper。
 */
@Service
@RequiredArgsConstructor
public class SessionQueryService {

    /** 单会话消息列表默认上限（详情页一次性返回所有消息时的护栏） */
    private static final int DEFAULT_MESSAGE_LIMIT = 200;

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;

    /**
     * 分页查询某个 Agent 的会话历史。
     * <p>
     * 会话归属维度为 <b>Agent</b>（而非创建人）：同一 Agent 下不同来源（web 调试台 / API 秘钥调用）
     * 的会话都应出现在该 Agent 的"会话历史"里，故不再按 {@code creatorUserId} 私有过滤——否则
     * API 调用产生的会话（creatorUserId 为兜底的 {@code "system"}）会被永久排除。
     * <p>
     * 因去掉了归属人过滤，{@code agentNum} 变为<b>必填</b>：缺省将导致返回全量会话（跨 Agent 泄露），
     * 故此处强校验非空。
     *
     * @param query 列表查询（agentNum 必填 + 分页 + 可选 origin/keyword/mineOnly）
     * @param operatorId 操作人 userId；仅当 mineOnly=true 时用于过滤创建人，否则可传 null
     * @return 该 Agent 的会话分页结果
     */
    public PageVO<SessionListVO> pageList(SessionListQuery query, String operatorId) {
        if (StrUtil.isBlank(query.getAgentNum())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "agentNum 不能为空");
        }
        int safePageNo = query.getPageNo() == null ? 1 : query.getPageNo();
        int safePageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        Page<SessionEntity> page = Page.of(safePageNo, safePageSize);
        LambdaQueryWrapper<SessionEntity> wrapper = new LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getAgentNum, query.getAgentNum())
                .eq(SessionEntity::getDeleted, 0)
                .eq(Boolean.TRUE.equals(query.getMineOnly()) && operatorId != null,
                        SessionEntity::getCreatorUserId, operatorId)
                .eq(StrUtil.isNotBlank(query.getOrigin()), SessionEntity::getOrigin, query.getOrigin())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(SessionEntity::getNum, query.getKeyword())
                        .or()
                        .like(SessionEntity::getTitle, query.getKeyword()))
                .orderByDesc(SessionEntity::getLastMessageAt)
                .orderByDesc(SessionEntity::getCreateTime);
        Page<SessionEntity> result = sessionMapper.selectPage(page, wrapper);
        List<SessionListVO> list = result.getRecords().stream().map(this::toListVO).toList();
        return PageVO.of(list, result.getTotal(), safePageNo, safePageSize);
    }

    public SessionDetailVO detail(String sessionNum) {
        SessionEntity entity = requireExistingSession(sessionNum);
        SessionDetailVO vo = new SessionDetailVO();
        vo.setNum(entity.getNum());
        vo.setAgentNum(entity.getAgentNum());
        vo.setAgentVersionNum(entity.getAgentVersionNum());
        vo.setSkillHint(entity.getSkillHint());
        vo.setTitle(entity.getTitle());
        vo.setCreateTime(entity.getCreateTime());
        vo.setOrigin(entity.getOrigin());
        Map<String, Object> invokeContext = SysPromptVariableSubstitutor.parseJson(entity.getInvokeContext());
        vo.setInvokeContext(invokeContext.isEmpty() ? null : new LinkedHashMap<>(invokeContext));
        List<MessageVO> messages = listMessagesInternal(sessionNum, DEFAULT_MESSAGE_LIMIT);
        vo.setMessages(messages);
        vo.setTurns(toTurns(messages));
        return vo;
    }

    /**
     * 按 Run ID 查会话详情。
     * <p>
     * 一轮 Run 的用户消息和助手消息都记在同一会话上，取其中一条即可定位会话，
     * 再复用 {@link #detail(String)}。返回的 messages、turns 以及助手消息上的 Token
     * 与按会话编号查询完全一致。进行中的 Run 只有用户消息，Token 要等助手消息落库后才有。
     * 功能上线前的历史消息没有 Run ID，按此查询视为会话不存在。
     *
     * @param runId 本轮 Agent run 编号
     * @return 会话详情
     */
    public SessionDetailVO detailByRunId(String runId) {
        if (StrUtil.isBlank(runId)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "runId 不能为空");
        }
        MessageEntity hit = messageMapper.selectOne(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getRunId, runId.trim())
                .eq(MessageEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (hit == null || StrUtil.isBlank(hit.getSessionNum())) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return detail(hit.getSessionNum());
    }

    public List<MessageVO> listMessages(String sessionNum) {
        requireExistingSession(sessionNum);
        return listMessagesInternal(sessionNum, DEFAULT_MESSAGE_LIMIT);
    }

    private List<MessageVO> listMessagesInternal(String sessionNum, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, DEFAULT_MESSAGE_LIMIT);
        return messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getSessionNum, sessionNum)
                        .eq(MessageEntity::getDeleted, 0)
                        .orderByAsc(MessageEntity::getCreateTime)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toMessageVO)
                .toList();
    }

    /**
     * 校验会话存在且未删除，返回实体。
     * <p>
     * 会话归属维度为 Agent（详见 {@link #pageList}），不再做创建人级私有校验——否则从"会话历史"
     * 点开一条 API 来源会话（creatorUserId="system"）会误判 403。是否有权访问由上层"能否访问该
     * Agent"决定；本方法只负责存在性。
     *
     * @param sessionNum 会话业务编号
     * @return 会话实体
     */
    private SessionEntity requireExistingSession(String sessionNum) {
        SessionEntity entity = sessionMapper.selectOne(new LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getNum, sessionNum)
                .eq(SessionEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return entity;
    }

    private SessionListVO toListVO(SessionEntity entity) {
        SessionListVO vo = new SessionListVO();
        vo.setNum(entity.getNum());
        vo.setAgentNum(entity.getAgentNum());
        vo.setAgentVersionNum(entity.getAgentVersionNum());
        vo.setTitle(entity.getTitle());
        vo.setLastMessageAt(entity.getLastMessageAt());
        vo.setOrigin(entity.getOrigin());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private MessageVO toMessageVO(MessageEntity entity) {
        MessageVO vo = new MessageVO();
        vo.setNum(entity.getNum());
        vo.setRole(entity.getRole());
        vo.setInputType(entity.getInputType());
        vo.setContent(parseContent(entity.getContent()));
        vo.setStepChain(toStepChainVO(entity.getStepChain()));
        vo.setSegments(toSegmentsVO(entity.getSegmentsJson()));
        vo.setTraceId(entity.getTraceId());
        vo.setRunId(entity.getRunId());
        vo.setInputTokens(entity.getInputTokens());
        vo.setOutputTokens(entity.getOutputTokens());
        vo.setCachedTokens(entity.getCachedTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 按 Run ID 把消息收成一轮。没有 Run ID 的历史消息跳过。
     * 同一 Run 下取第一条用户消息和第一条助手消息；Token 以助手消息上的值为准。
     */
    private static List<SessionTurnVO> toTurns(List<MessageVO> messages) {
        Map<String, SessionTurnVO> byRun = new LinkedHashMap<>();
        if (messages == null) {
            return List.of();
        }
        for (MessageVO message : messages) {
            if (message == null || StrUtil.isBlank(message.getRunId())) {
                continue;
            }
            SessionTurnVO turn = byRun.computeIfAbsent(message.getRunId(), runId -> {
                SessionTurnVO created = new SessionTurnVO();
                created.setRunId(runId);
                return created;
            });
            if ("USER".equals(message.getRole()) && StrUtil.isBlank(turn.getUserMessageNum())) {
                turn.setUserMessageNum(message.getNum());
            } else if ("ASSISTANT".equals(message.getRole()) && StrUtil.isBlank(turn.getAssistantMessageNum())) {
                turn.setAssistantMessageNum(message.getNum());
                turn.setInputTokens(message.getInputTokens());
                turn.setOutputTokens(message.getOutputTokens());
                turn.setCachedTokens(message.getCachedTokens());
                turn.setTotalTokens(message.getTotalTokens());
            }
        }
        return new ArrayList<>(byRun.values());
    }

    private Object parseContent(String content) {
        if (content == null) {
            return null;
        }
        try {
            return JSON.parse(content);
        } catch (Exception ignored) {
            return content;
        }
    }

    private StepChainVO toStepChainVO(String stepChainJson) {
        if (StrUtil.isBlank(stepChainJson)) {
            return null;
        }
        try {
            List<StepNodeVO> nodes = JSON.parseArray(stepChainJson, StepNodeVO.class);
            StepChainVO vo = new StepChainVO();
            vo.setSteps(nodes);
            return vo;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 把 message.segments_json 反序列化为 AssistantSegmentVO 列表。
     * 空或解析失败时返回 null,FE 自动降级到 content + stepChain 路径。
     */
    private List<AssistantSegmentVO> toSegmentsVO(String segmentsJson) {
        if (StrUtil.isBlank(segmentsJson)) {
            return null;
        }
        try {
            return JSON.parseArray(segmentsJson, AssistantSegmentVO.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
