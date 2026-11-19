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

    /** 会话消息每页条数：缺省 200，单次最多也是 200。 */
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 200;

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

    /**
     * 会话详情，消息取第 1 页、每页 {@value #DEFAULT_MESSAGE_PAGE_SIZE} 条。
     *
     * @param sessionNum 会话业务编号
     * @return 会话详情
     */
    public SessionDetailVO detail(String sessionNum) {
        return detail(sessionNum, null, null);
    }

    /**
     * 会话详情，消息按页返回。
     * <p>
     * 不传页码时返回第 1 页，不传每页条数时为 200，单页最多 200。
     * messages 和 turns 只含当前页；total 是该会话的消息总数。
     *
     * @param sessionNum 会话业务编号
     * @param pageNo     页码，从 1 开始；空则第 1 页
     * @param pageSize   每页条数；空则 200
     * @return 会话详情
     */
    public SessionDetailVO detail(String sessionNum, Integer pageNo, Integer pageSize) {
        SessionEntity entity = requireExistingSession(sessionNum);
        MessagePage page = loadMessagePage(sessionNum, pageNo, pageSize);
        SessionDetailVO vo = assembleDetail(entity, page.messages());
        vo.setPageNo(page.pageNo());
        vo.setPageSize(page.pageSize());
        vo.setTotal(page.total());
        return vo;
    }

    /**
     * 按 Run ID 查这一轮的消息。
     * <p>
     * 只返回该 Run 自己的用户消息和助手消息，以及这一轮的 Token。
     * 会话头（编号、Agent、标题、来源）仍带上，用来说明这一轮属于哪个会话；
     * 同一会话里其他 Run 的消息不会出现在 messages 或 turns 里。
     * 进行中的 Run 只有用户消息，Token 要等助手消息落库后才有。
     * 功能上线前的历史消息没有 Run ID，按此查询视为会话不存在。
     *
     * @param runId 本轮 Agent run 编号
     * @return 只含本轮消息的会话详情
     */
    public SessionDetailVO detailByRunId(String runId) {
        if (StrUtil.isBlank(runId)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "runId 不能为空");
        }
        String trimmed = runId.trim();
        List<MessageEntity> rows = messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getRunId, trimmed)
                .eq(MessageEntity::getDeleted, 0)
                .orderByAsc(MessageEntity::getCreateTime)
                .last("LIMIT " + DEFAULT_MESSAGE_PAGE_SIZE));
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在");
        }
        String sessionNum = rows.stream()
                .map(MessageEntity::getSessionNum)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        if (sessionNum == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在");
        }
        List<MessageVO> messages = rows.stream()
                .filter(row -> trimmed.equals(row.getRunId()) && sessionNum.equals(row.getSessionNum()))
                .map(this::toMessageVO)
                .toList();
        if (messages.isEmpty()) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return assembleDetail(requireExistingSession(sessionNum), messages);
    }

    /** 组装会话头，并挂上给定消息与按 Run 汇总的轮次。 */
    private SessionDetailVO assembleDetail(SessionEntity entity, List<MessageVO> messages) {
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
        vo.setMessages(messages);
        vo.setTurns(toTurns(messages));
        return vo;
    }

    /**
     * 按页拉取某个会话的消息。缺省第 1 页、每页 200 条。
     *
     * @param sessionNum 会话业务编号
     * @param pageNo     页码，从 1 开始；空则第 1 页
     * @param pageSize   每页条数；空则 200，最大 200
     * @return 当前页消息
     */
    public PageVO<MessageVO> listMessages(String sessionNum, Integer pageNo, Integer pageSize) {
        requireExistingSession(sessionNum);
        MessagePage page = loadMessagePage(sessionNum, pageNo, pageSize);
        return PageVO.of(page.messages(), page.total(), page.pageNo(), page.pageSize());
    }

    /**
     * 统计会话消息并取出当前页。页码、每页条数先归一化，超出总条数时返回空列表。
     */
    private MessagePage loadMessagePage(String sessionNum, Integer pageNo, Integer pageSize) {
        int safePageNo = normalizePageNo(pageNo);
        int safePageSize = normalizePageSize(pageSize);
        long total = messageMapper.selectCount(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getSessionNum, sessionNum)
                .eq(MessageEntity::getDeleted, 0));
        long offset = (long) (safePageNo - 1) * safePageSize;
        if (offset >= total) {
            return new MessagePage(List.of(), total, safePageNo, safePageSize);
        }
        List<MessageVO> messages = messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getSessionNum, sessionNum)
                        .eq(MessageEntity::getDeleted, 0)
                        .orderByAsc(MessageEntity::getCreateTime)
                        .last("LIMIT " + offset + ", " + safePageSize))
                .stream()
                .map(this::toMessageVO)
                .toList();
        return new MessagePage(messages, total, safePageNo, safePageSize);
    }

    /** 空页码视为第 1 页；小于 1 视为参数错误。 */
    private static int normalizePageNo(Integer pageNo) {
        if (pageNo == null) {
            return 1;
        }
        if (pageNo < 1) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "pageNo 必须 ≥ 1");
        }
        return pageNo;
    }

    /** 空每页条数视为 200；小于 1 视为参数错误；大于 200 时压回 200。 */
    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        if (pageSize < 1) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "pageSize 必须 ≥ 1");
        }
        return Math.min(pageSize, DEFAULT_MESSAGE_PAGE_SIZE);
    }

    /** 一页消息及其分页位置。 */
    private record MessagePage(List<MessageVO> messages, long total, int pageNo, int pageSize) {
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
