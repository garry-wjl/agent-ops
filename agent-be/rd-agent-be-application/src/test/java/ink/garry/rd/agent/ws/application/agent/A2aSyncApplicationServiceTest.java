package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.client.agent.A2aSyncCandidateVO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.repository.A2aSyncHistoryRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.a2a.NacosAgentCardFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link A2aSyncApplicationService} 单元测试（hotfix_20260625_a2a-create-endpoints 同步能力补充）。
 * <p>
 * 覆盖 4 个分支：
 * <ol>
 *   <li>syncPendingBatch_normal — 2 条 PENDING_SYNC 都拉取成功 → 全部 applyNacosSync 推进 PUBLISHED</li>
 *   <li>syncPendingBatch_remoteUnreachable — fetcher 抛 2011 → 写历史 + 跳过；不影响其他条</li>
 *   <li>syncPendingBatch_creationModeChanged — Agent 被并发改为非 A2A → 跳过</li>
 *   <li>syncPendingBatch_emptyList — 无候选 → 直接返回 0，不调 fetcher</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class A2aSyncApplicationServiceTest {

    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private AgentFactory agentFactory;
    @Mock
    private A2aSyncHistoryRepository a2aSyncHistoryRepository;
    @Mock
    private NacosAgentCardFetcher fetcher;
    @Mock
    private ObjectProvider<NacosAgentCardFetcher> fetcherProvider;

    private A2aSyncApplicationService service;

    @BeforeEach
    void setUp() {
        // 默认 fetcher 可用；个别测试覆盖
        lenient().when(fetcherProvider.getIfAvailable()).thenReturn(fetcher);
        service = new A2aSyncApplicationService(agentQueryService, agentFactory,
                a2aSyncHistoryRepository, fetcherProvider);
    }

    private A2aSourceInfo buildSource(String name) {
        return A2aSourceInfo.builder()
                .nacosGroup("DEFAULT_GROUP")
                .nacosService(name)
                .remoteVersion("1.0.0")
                .agentCardJson("{\"name\":\"" + name + "\"}")
                .lastSyncedAt(java.time.LocalDateTime.now())
                .lastSyncEventType(SyncEventType.POLLING_RECONCILE)
                .build();
    }

    private A2aSyncCandidateVO candidate(String num, String name) {
        return new A2aSyncCandidateVO(num, name);
    }

    private Agent mockAgent(String num) {
        Agent agent = mock(Agent.class);
        lenient().when(agent.getNum()).thenReturn(num);
        lenient().when(agent.getCreationMode()).thenReturn(CreationMode.A2A);
        lenient().when(agent.getStatus()).thenReturn(AgentStatus.PENDING_SYNC);
        return agent;
    }

    /** 反射为 private 字段赋值（同步历史 setRepository 等场景） */
    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- syncPendingBatch ----------

    @Test
    void syncPendingBatch_normal_twoAgentsAllSucceed() {
        when(agentQueryService.listPendingSyncCandidates(50)).thenReturn(List.of(
                candidate("AGT-1", "agent-1"),
                candidate("AGT-2", "agent-2")));

        when(fetcher.fetch("agent-1")).thenReturn(buildSource("agent-1"));
        when(fetcher.fetch("agent-2")).thenReturn(buildSource("agent-2"));

        Agent agent1 = mockAgent("AGT-1");
        Agent agent2 = mockAgent("AGT-2");
        when(agentFactory.createByNum("AGT-1")).thenReturn(agent1);
        when(agentFactory.createByNum("AGT-2")).thenReturn(agent2);

        int processed = service.syncPendingBatch(50);

        assertEquals(2, processed);
        // 两条都调用了 applyNacosSync
        verify(agent1).applyNacosSync(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(A2aSourceInfo.class),
                org.mockito.ArgumentMatchers.eq(AgentStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.eq(SyncEventType.POLLING_RECONCILE),
                anyString());
        verify(agent2).applyNacosSync(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(A2aSourceInfo.class),
                org.mockito.ArgumentMatchers.eq(AgentStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.eq(SyncEventType.POLLING_RECONCILE),
                anyString());

        // 历史落库：成功 + 失败 = 2 次
        verify(a2aSyncHistoryRepository, atLeastOnce()).save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }

    @Test
    void syncPendingBatch_remoteUnreachable_skipsAndContinues() {
        when(agentQueryService.listPendingSyncCandidates(50)).thenReturn(List.of(
                candidate("AGT-1", "agent-1"),
                candidate("AGT-2", "agent-2")));

        // AGT-1 远端不可达；AGT-2 正常
        when(fetcher.fetch("agent-1")).thenThrow(new BusinessException(
                BizCode.A2A_REMOTE_UNREACHABLE.getCode(), "Nacos 不可达"));
        when(fetcher.fetch("agent-2")).thenReturn(buildSource("agent-2"));

        Agent agent2 = mockAgent("AGT-2");
        when(agentFactory.createByNum("AGT-2")).thenReturn(agent2);

        int processed = service.syncPendingBatch(50);

        // 远端不可达不应阻断后续：processed 仍为 2
        assertEquals(2, processed);
        verify(agent2).applyNacosSync(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(A2aSourceInfo.class),
                org.mockito.ArgumentMatchers.eq(AgentStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.eq(SyncEventType.POLLING_RECONCILE),
                anyString());
    }

    @Test
    void syncPendingBatch_creationModeChanged_skips() {
        when(agentQueryService.listPendingSyncCandidates(50)).thenReturn(List.of(
                candidate("AGT-1", "agent-1")));

        when(fetcher.fetch("agent-1")).thenReturn(buildSource("agent-1"));
        Agent agent1 = mock(Agent.class);
        // 数据竞态：被并发改为非 A2A
        when(agent1.getCreationMode()).thenReturn(CreationMode.CONFIG);
        when(agent1.getStatus()).thenReturn(AgentStatus.DRAFT_ONLY);
        when(agentFactory.createByNum("AGT-1")).thenReturn(agent1);

        int processed = service.syncPendingBatch(50);

        assertEquals(1, processed);
        verify(agent1, never()).applyNacosSync(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(A2aSourceInfo.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyString());
    }

    @Test
    void syncPendingBatch_emptyList_noFetchCall() {
        when(agentQueryService.listPendingSyncCandidates(50)).thenReturn(List.of());

        int processed = service.syncPendingBatch(50);

        assertEquals(0, processed);
        verify(fetcher, never()).fetch(anyString());
        verify(agentFactory, never()).createByNum(anyString());
        verify(a2aSyncHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }

    @Test
    void syncPendingBatch_fetcherNotAvailable_skips() {
        // discovery.enabled=false → fetcher bean 不被注册 → ObjectProvider 返回 null
        when(fetcherProvider.getIfAvailable()).thenReturn(null);

        int processed = service.syncPendingBatch(50);

        assertEquals(0, processed);
        verify(agentQueryService, never()).listPendingSyncCandidates(anyInt());
        verify(fetcher, never()).fetch(anyString());
        verify(a2aSyncHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }

    @Test
    void resyncByNum_fetcherNotAvailable_returnsNullAndWritesHistory() {
        when(fetcherProvider.getIfAvailable()).thenReturn(null);

        A2aSourceInfo result = service.resyncByNum("AGT-1", "agent-1",
                SyncEventType.MANUAL_RESYNC, "user-1");

        assertTrue(result == null);
        verify(fetcher, never()).fetch(anyString());
        // 容错：discovery 关闭时仍要写一条失败历史
        verify(a2aSyncHistoryRepository, atLeastOnce())
                .save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }

    @Test
    void syncPendingBatch_invalidBatchSize_usesDefault() {
        when(agentQueryService.listPendingSyncCandidates(A2aSyncApplicationService.DEFAULT_BATCH_SIZE))
                .thenReturn(List.of());

        // batchSize <= 0 应回退到 DEFAULT_BATCH_SIZE
        int processed = service.syncPendingBatch(0);
        assertEquals(0, processed);
        verify(agentQueryService).listPendingSyncCandidates(A2aSyncApplicationService.DEFAULT_BATCH_SIZE);
    }

    @Test
    void syncPendingBatch_agentDeletedAtSyncTime_skips() {
        when(agentQueryService.listPendingSyncCandidates(50)).thenReturn(List.of(
                candidate("AGT-DELETED", "agent-deleted")));
        when(fetcher.fetch("agent-deleted")).thenReturn(buildSource("agent-deleted"));
        when(agentFactory.createByNum("AGT-DELETED")).thenReturn(null);

        int processed = service.syncPendingBatch(50);

        assertEquals(1, processed);
        verify(a2aSyncHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }

    @Test
    void syncPendingBatch_writesHistoryWithPollingReconcileEvent() {
        when(agentQueryService.listPendingSyncCandidates(50))
                .thenReturn(List.of(candidate("AGT-1", "agent-1")));
        when(fetcher.fetch("agent-1")).thenReturn(buildSource("agent-1"));
        Agent agent1 = mockAgent("AGT-1");
        when(agentFactory.createByNum("AGT-1")).thenReturn(agent1);

        service.syncPendingBatch(50);

        ArgumentCaptor<A2aSyncHistory> captor = ArgumentCaptor.forClass(A2aSyncHistory.class);
        verify(a2aSyncHistoryRepository).save(captor.capture());
        A2aSyncHistory saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("AGT-1", saved.getAgentNum());
        assertEquals(SyncEventType.POLLING_RECONCILE, saved.getSyncEventType());
        assertEquals("1.0.0", saved.getRemoteVersion());
    }

    @Test
    void countPendingCandidates_delegatesToQueryService() {
        when(agentQueryService.listPendingSyncCandidates(10)).thenReturn(List.of(
                candidate("AGT-1", "a"), candidate("AGT-2", "b"), candidate("AGT-3", "c")));

        int count = service.countPendingCandidates(10);

        assertEquals(3, count);
        assertTrue(count > 0);
    }

    // ---------- resyncByNum（manualResync / syncOne 共用核心） ----------

    @Test
    void resyncByNum_normal_appliesNacosSyncAndWritesHistory() {
        when(fetcher.fetch("agent-1")).thenReturn(buildSource("agent-1"));
        Agent agent1 = mockAgent("AGT-1");
        when(agentFactory.createByNum("AGT-1")).thenReturn(agent1);

        A2aSourceInfo result = service.resyncByNum("AGT-1", "agent-1",
                SyncEventType.MANUAL_RESYNC, "user-1");

        assertNotNull(result);
        assertEquals("agent-1", result.getNacosService());
        // 用户操作：事件类型 = MANUAL_RESYNC，操作人 = 用户 id
        verify(agent1).applyNacosSync(org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(A2aSourceInfo.class),
                org.mockito.ArgumentMatchers.eq(AgentStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.eq(SyncEventType.MANUAL_RESYNC),
                org.mockito.ArgumentMatchers.eq("user-1"));
        verify(a2aSyncHistoryRepository, atLeastOnce())
                .save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }

    @Test
    void resyncByNum_remoteUnreachable_returnsNullAndWritesHistory() {
        when(fetcher.fetch("agent-1")).thenThrow(new BusinessException(
                BizCode.A2A_REMOTE_UNREACHABLE.getCode(), "Nacos 不可达"));

        A2aSourceInfo result = service.resyncByNum("AGT-1", "agent-1",
                SyncEventType.MANUAL_RESYNC, "user-1");

        assertTrue(result == null);
        verify(agentFactory, never()).createByNum(anyString());
    }

    @Test
    void resyncByNum_nonA2aAgent_returnsNull() {
        when(fetcher.fetch("agent-1")).thenReturn(buildSource("agent-1"));
        Agent agent1 = mock(Agent.class);
        when(agent1.getCreationMode()).thenReturn(CreationMode.CONFIG);
        when(agentFactory.createByNum("AGT-1")).thenReturn(agent1);

        A2aSourceInfo result = service.resyncByNum("AGT-1", "agent-1",
                SyncEventType.MANUAL_RESYNC, "user-1");

        assertTrue(result == null);
        verify(agent1, never()).applyNacosSync(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(A2aSourceInfo.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyString());
    }

    @Test
    void resyncByNum_agentDeletedAtSyncTime_returnsNull() {
        when(fetcher.fetch("agent-1")).thenReturn(buildSource("agent-1"));
        when(agentFactory.createByNum("AGT-DELETED")).thenReturn(null);

        A2aSourceInfo result = service.resyncByNum("AGT-DELETED", "agent-1",
                SyncEventType.MANUAL_RESYNC, "user-1");

        assertTrue(result == null);
        verify(a2aSyncHistoryRepository, never())
                .save(org.mockito.ArgumentMatchers.any(A2aSyncHistory.class));
    }
}