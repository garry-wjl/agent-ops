package ink.garry.rd.agent.ws.application.session;

import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
import ink.garry.rd.agent.ws.client.session.SessionTurnVO;
import ink.garry.rd.agent.ws.infra.session.entity.MessageEntity;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.session.entity.SessionEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.MessageMapper;
import ink.garry.rd.agent.ws.infra.session.mapper.SessionMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionQueryService} 行为测试。
 * <p>
 * 覆盖本次 hotfix 的核心契约：会话可见性由"创建人私有"改为"Agent 维度"，
 * 使 API 来源会话（creatorUserId="system"）也能出现在历史列表且详情可读。
 * <p>
 * Mapper 依赖用 JDK 动态代理手写打桩（与 {@code ModelQueryServiceScopeTest} 一致），
 * 避免 Mockito/ByteBuddy 生成的 mock 类触发 JaCoCo 插桩异常。
 */
class SessionQueryServiceTest {

    /** agentNum 为空必须直接报参数错误，避免退化成全量会话查询导致跨 Agent 泄露。 */
    @Test
    void pageList_shouldRejectBlankAgentNum() {
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, null, null), messageMapper(List.of()));
        SessionListQuery query = new SessionListQuery();
        query.setAgentNum("  ");
        query.setPageNo(1);
        query.setPageSize(20);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.pageList(query, null));
        assertTrue(ex.getMessage().contains("agentNum"));
    }

    /** 列表应原样返回该 Agent 下所有来源的会话，包括 API 来源（creatorUserId="system"）。 */
    @Test
    void pageList_shouldReturnApiOriginSessionsRegardlessOfCreator() {
        SessionEntity webSession = session("SES-WEB", "AG-1", "user-123", "DEBUG_CONSOLE");
        SessionEntity apiSession = session("SES-API", "AG-1", "system", "API");
        Page<SessionEntity> page = Page.of(1, 20);
        page.setRecords(List.of(webSession, apiSession));
        page.setTotal(2);

        SessionQueryService service = new SessionQueryService(
                sessionMapper(page, null, null), messageMapper(List.of()));
        SessionListQuery query = new SessionListQuery();
        query.setAgentNum("AG-1");
        query.setPageNo(1);
        query.setPageSize(20);

        PageVO<SessionListVO> result = service.pageList(query, null);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        assertTrue(result.getList().stream().anyMatch(v -> "SES-API".equals(v.getNum())),
                "API 来源会话必须出现在历史列表中");
    }

    /** 详情不再做创建人归属校验：非本人创建（如 API/system 会话）也应能读取，不再抛 403。 */
    @Test
    void detail_shouldNotRejectNonCreator() {
        SessionEntity apiSession = session("SES-API", "AG-1", "system", "API");
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, apiSession, null), messageMapper(List.of()));

        SessionDetailVO vo = service.detail("SES-API");

        assertEquals("SES-API", vo.getNum());
        assertEquals("API", vo.getOrigin());
    }

    /**
     * 详情按 Run 收成一轮：带上两条消息编号、Run ID 和助手消息上的 Token。
     * 没有 Run ID 的历史消息留在 messages 里，但不进 turns。
     */
    @Test
    void detail_groupsTurnsAndLeavesHistoricalBlank() {
        MessageEntity oldUser = message("MSG-OLD-U", "USER", null, null, null, null, null);
        MessageEntity oldAssistant = message("MSG-OLD-A", "ASSISTANT", null, null, null, null, null);
        MessageEntity user = message("MSG-U", "USER", "RUN-1", null, null, null, null);
        MessageEntity assistant = message("MSG-A", "ASSISTANT", "RUN-1", 30, 13, 3, 43);
        MessageEntity unfinished = message("MSG-U2", "USER", "RUN-2", null, null, null, null);
        SessionEntity session = session("SES-1", "AG-1", "alice", "DEBUG_CONSOLE");
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, session, null),
                messageMapper(List.of(oldUser, oldAssistant, user, assistant, unfinished)));

        SessionDetailVO vo = service.detail("SES-1");

        assertEquals(5, vo.getMessages().size());
        assertNull(vo.getMessages().get(0).getRunId());
        assertNull(vo.getMessages().get(0).getTotalTokens());
        assertEquals("RUN-1", vo.getMessages().get(3).getRunId());
        assertEquals(43, vo.getMessages().get(3).getTotalTokens());

        assertEquals(2, vo.getTurns().size());
        SessionTurnVO finished = vo.getTurns().get(0);
        assertEquals("RUN-1", finished.getRunId());
        assertEquals("MSG-U", finished.getUserMessageNum());
        assertEquals("MSG-A", finished.getAssistantMessageNum());
        assertEquals(30, finished.getInputTokens());
        assertEquals(13, finished.getOutputTokens());
        assertEquals(3, finished.getCachedTokens());
        assertEquals(43, finished.getTotalTokens());

        SessionTurnVO open = vo.getTurns().get(1);
        assertEquals("RUN-2", open.getRunId());
        assertEquals("MSG-U2", open.getUserMessageNum());
        assertNull(open.getAssistantMessageNum());
        assertNull(open.getTotalTokens());
    }

    /**
     * 按 Run ID 查到的就是同一份会话详情：整段消息都在，Token 记在助手消息和对应 turn 上。
     */
    @Test
    void detailByRunId_returnsTheSameDetailIncludingTokens() {
        MessageEntity user = message("MSG-U", "USER", "RUN-1", null, null, null, null);
        user.setSessionNum("SES-1");
        MessageEntity assistant = message("MSG-A", "ASSISTANT", "RUN-1", 30, 13, 3, 43);
        assistant.setSessionNum("SES-1");
        SessionEntity session = session("SES-1", "AG-1", "alice", "API");
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, session, null),
                messageMapper(List.of(user, assistant), user));

        SessionDetailVO vo = service.detailByRunId("  RUN-1  ");

        assertEquals("SES-1", vo.getNum());
        assertEquals("API", vo.getOrigin());
        assertEquals(2, vo.getMessages().size());
        assertEquals(43, vo.getMessages().get(1).getTotalTokens());
        assertEquals(1, vo.getTurns().size());
        assertEquals("RUN-1", vo.getTurns().get(0).getRunId());
        assertEquals("MSG-U", vo.getTurns().get(0).getUserMessageNum());
        assertEquals("MSG-A", vo.getTurns().get(0).getAssistantMessageNum());
        assertEquals(30, vo.getTurns().get(0).getInputTokens());
        assertEquals(13, vo.getTurns().get(0).getOutputTokens());
        assertEquals(3, vo.getTurns().get(0).getCachedTokens());
        assertEquals(43, vo.getTurns().get(0).getTotalTokens());
    }

    /** 空 Run ID 是参数错误，不查库。 */
    @Test
    void detailByRunId_shouldRejectBlankRunId() {
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, null, null), messageMapper(List.of()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detailByRunId("  "));
        assertTrue(ex.getMessage().contains("runId"));
    }

    /** 没有这条 Run，或消息上没有会话编号，都按会话不存在处理。 */
    @Test
    void detailByRunId_shouldThrowWhenRunMissing() {
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, null, null), messageMapper(List.of(), null));
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.detailByRunId("RUN-NONE"));
        assertTrue(missing.getMessage().contains("会话不存在"));

        MessageEntity orphan = message("MSG-U", "USER", "RUN-ORPHAN", null, null, null, null);
        SessionQueryService orphaned = new SessionQueryService(
                sessionMapper(null, null, null), messageMapper(List.of(), orphan));
        BusinessException noSession = assertThrows(BusinessException.class,
                () -> orphaned.detailByRunId("RUN-ORPHAN"));
        assertTrue(noSession.getMessage().contains("会话不存在"));
    }

    /** 会话不存在仍应抛资源不存在。 */
    @Test
    void detail_shouldThrowWhenSessionMissing() {
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, null, null), messageMapper(List.of()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail("SES-NONE"));
        assertTrue(ex.getMessage().contains("会话不存在"));
    }

    private static MessageEntity message(String num, String role, String runId,
                                         Integer input, Integer output, Integer cached, Integer total) {
        MessageEntity entity = new MessageEntity();
        entity.setNum(num);
        entity.setRole(role);
        entity.setContent(num);
        entity.setRunId(runId);
        entity.setInputTokens(input);
        entity.setOutputTokens(output);
        entity.setCachedTokens(cached);
        entity.setTotalTokens(total);
        return entity;
    }

    private static SessionEntity session(String num, String agentNum, String creatorUserId, String origin) {
        SessionEntity entity = new SessionEntity();
        entity.setNum(num);
        entity.setAgentNum(agentNum);
        entity.setCreatorUserId(creatorUserId);
        entity.setOrigin(origin);
        entity.setTitle(num + "-title");
        return entity;
    }

    /**
     * 打桩 SessionMapper：selectPage 返回 {@code page}，selectOne 返回 {@code one}。
     */
    private static SessionMapper sessionMapper(Page<SessionEntity> page, SessionEntity one, Void ignored) {
        return (SessionMapper) Proxy.newProxyInstance(
                SessionMapper.class.getClassLoader(),
                new Class<?>[]{SessionMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectPage" -> page;
                    case "selectOne" -> one;
                    case "toString" -> "SessionMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 打桩 MessageMapper：selectList 返回给定消息列表，selectOne 返回定位 Run 用的那一条。
     */
    private static MessageMapper messageMapper(List<?> selectListResult) {
        return messageMapper(selectListResult, null);
    }

    private static MessageMapper messageMapper(List<?> selectListResult, MessageEntity selectOneResult) {
        return (MessageMapper) Proxy.newProxyInstance(
                MessageMapper.class.getClassLoader(),
                new Class<?>[]{MessageMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectList" -> selectListResult;
                    case "selectOne" -> selectOneResult;
                    case "toString" -> "MessageMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
