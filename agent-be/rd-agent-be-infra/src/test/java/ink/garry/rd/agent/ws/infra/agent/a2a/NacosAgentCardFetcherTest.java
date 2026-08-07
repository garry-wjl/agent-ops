package ink.garry.rd.agent.ws.infra.agent.a2a;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link NacosAgentCardFetcher} 单元测试。
 * <p>
 * fetcher 走构造器注入 {@link AiService}（启动期 fail-fast，不接受 {@code ObjectProvider}）。
 * 覆盖：
 * <ol>
 *   <li>正常拉取 → 业务返回 {@code A2aSourceInfo} 含 nacosService=name</li>
 *   <li>{@code AiService} 抛 {@link NacosException} → 业务抛 {@link BusinessException}(2011)</li>
 *   <li>Nacos 返回 {@code null} → 业务抛 {@link BusinessException}(2001)</li>
 *   <li>Nacos 返回 {@code AgentCard.name} 为空 → 业务抛 {@link BusinessException}(2001)</li>
 * </ol>
 */
class NacosAgentCardFetcherTest {

    private static final int CODE_A2A_REMOTE_UNREACHABLE = 2011;
    private static final int CODE_AGENT_NOT_FOUND = 2001;

    private NacosAgentCardFetcher newFetcher(AiService aiService) {
        return new NacosAgentCardFetcher(aiService);
    }

    @Test
    void fetch_normal_returnsSourceWithNacosService() throws NacosException {
        AiService aiService = mock(AiService.class);
        AgentCardDetailInfo card = new AgentCardDetailInfo();
        card.setName("agent-1");
        card.setVersion("1.0.0");
        card.setUrl("https://nacos.garrycorp.com/agent-1/a2a/invoke");
        when(aiService.getAgentCard(eq("agent-1"))).thenReturn(card);

        NacosAgentCardFetcher fetcher = newFetcher(aiService);

        var source = fetcher.fetch("agent-1");

        assertNotNull(source);
        assertEquals("agent-1", source.getNacosService());
        assertEquals("1.0.0", source.getRemoteVersion());
    }

    @Test
    void fetch_aiServiceThrowsNacosException_throwsBusiness2011() throws NacosException {
        AiService aiService = mock(AiService.class);
        when(aiService.getAgentCard(eq("agent-bad")))
                .thenThrow(new NacosException(404, "agent not found in nacos"));

        NacosAgentCardFetcher fetcher = newFetcher(aiService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fetcher.fetch("agent-bad"));
        assertEquals(CODE_A2A_REMOTE_UNREACHABLE, ex.getCode());
    }

    @Test
    void fetch_aiServiceReturnsNull_throwsBusiness2001() throws NacosException {
        AiService aiService = mock(AiService.class);
        when(aiService.getAgentCard(eq("agent-missing"))).thenReturn(null);

        NacosAgentCardFetcher fetcher = newFetcher(aiService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fetcher.fetch("agent-missing"));
        assertEquals(CODE_AGENT_NOT_FOUND, ex.getCode());
    }

    @Test
    void fetch_aiServiceReturnsBlankName_throwsBusiness2001() throws NacosException {
        AiService aiService = mock(AiService.class);
        AgentCardDetailInfo card = new AgentCardDetailInfo();
        card.setName("");  // 关键字段缺失
        card.setUrl("https://nacos.garrycorp.com/x/a2a/invoke");
        when(aiService.getAgentCard(eq("agent-bad-name"))).thenReturn(card);

        NacosAgentCardFetcher fetcher = newFetcher(aiService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fetcher.fetch("agent-bad-name"));
        assertEquals(CODE_AGENT_NOT_FOUND, ex.getCode());
    }
}
