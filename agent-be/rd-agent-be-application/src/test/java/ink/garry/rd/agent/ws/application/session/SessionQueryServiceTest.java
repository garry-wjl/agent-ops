package ink.garry.rd.agent.ws.application.session;

import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
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

    /** 会话不存在仍应抛资源不存在。 */
    @Test
    void detail_shouldThrowWhenSessionMissing() {
        SessionQueryService service = new SessionQueryService(
                sessionMapper(null, null, null), messageMapper(List.of()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail("SES-NONE"));
        assertTrue(ex.getMessage().contains("会话不存在"));
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
     * 打桩 MessageMapper：selectList 返回给定消息列表。
     */
    private static MessageMapper messageMapper(List<?> selectListResult) {
        return (MessageMapper) Proxy.newProxyInstance(
                MessageMapper.class.getClassLoader(),
                new Class<?>[]{MessageMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectList" -> selectListResult;
                    case "toString" -> "MessageMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
