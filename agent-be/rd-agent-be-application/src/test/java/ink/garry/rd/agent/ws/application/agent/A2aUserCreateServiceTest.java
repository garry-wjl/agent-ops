package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.client.agent.A2aCreateParam;
import ink.garry.rd.agent.ws.client.agent.A2aDraftParam;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.repository.A2aSyncHistoryRepository;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.a2a.NacosAgentCardFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link A2aUserCreateService} 单元测试（hotfix_20260625_a2a-create-endpoints）。
 * <p>
 * 覆盖 10 个分支：
 * <ol>
 *   <li>createA2a_normal — fetch 成功 + 无重名 + 无 draftAgentNum → createA2aAgent + save + 写历史</li>
 *   <li>createA2a_alreadySubscribed — 同 nacosServiceKey 已存在非草稿 → 抛 2012</li>
 *   <li>createA2a_remoteUnreachable — fetch 抛 2011 → 透传</li>
 *   <li>createA2a_draftPromotion — 带 draftAgentNum + 草稿存在 → applyNacosSync 路径</li>
 *   <li>createA2a_writesSyncHistory — 验证 A2aSyncHistory.save 被调用 1 次</li>
 *   <li>saveA2aDraft_create — 无 agentNum → 新建草稿（status=DRAFT_ONLY, a2aSource=null）</li>
 *   <li>saveA2aDraft_update — 有 agentNum + DRAFT_ONLY → update name/description</li>
 *   <li>saveA2aDraft_update_nonDraft — 已 PENDING_SYNC → 抛 2010</li>
 *   <li>unsubscribeA2a — DRAFT_ONLY 状态 → delete 路径</li>
 *   <li>unsubscribeA2a_offline — OFFLINE 状态 → 拒绝（2003）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class A2aUserCreateServiceTest {

    @Mock
    private AgentFactory agentFactory;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private A2aSyncHistoryRepository a2aSyncHistoryRepository;
    @Mock
    private NacosAgentCardFetcher fetcher;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;
    @Mock
    private AgentQueryService agentQueryService;

    private A2aUserCreateService service;

    @BeforeEach
    void setUp() {
        service = new A2aUserCreateService(agentFactory, agentRepository, a2aSyncHistoryRepository,
                fetcher, redissonClient);
        // 用反射注入 @Autowired @Lazy 的 queryService（避免构造循环）
        injectField("agentQueryService", agentQueryService);

        // 默认 lock 立即拿到
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectField(String name, Object value) {
        try {
            var field = A2aUserCreateService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private A2aSourceInfo buildSource() {
        return A2aSourceInfo.builder()
                .nacosGroup("DEFAULT_GROUP")
                .nacosService("test-agent")
                .remoteVersion("1.0.0")
                .agentCardJson("{\"name\":\"test-agent\"}")
                .lastSyncedAt(java.time.LocalDateTime.now())
                .lastSyncEventType(SyncEventType.MANUAL_RESYNC)
                .build();
    }

    private Agent mockAgent(String num, CreationMode mode, AgentStatus status) {
        Agent agent = mock(Agent.class);
        when(agent.getNum()).thenReturn(num);
        when(agent.getCreationMode()).thenReturn(mode);
        when(agent.getStatus()).thenReturn(status);
        return agent;
    }

    private AgentDetailViewDTO mockDetail(String num) {
        AgentDetailViewDTO dto = mock(AgentDetailViewDTO.class);
        lenient().when(dto.getNum()).thenReturn(num);
        return dto;
    }

    // ---------- createA2a ----------

    @Test
    void createA2a_normal_shouldCreateAgentAndWriteHistory() {
        A2aCreateParam param = new A2aCreateParam();
        param.setNacosAgentName("test-agent");
        param.setDisplayName("Display");
        param.setDescription("desc");

        A2aSourceInfo source = buildSource();
        when(fetcher.fetch("test-agent")).thenReturn(source);
        when(agentQueryService.findNumByNacosServiceKey("DEFAULT_GROUP@@test-agent")).thenReturn(null);

        Agent created = mockAgent("AGT-NEW", CreationMode.A2A, AgentStatus.PENDING_SYNC);
        when(agentFactory.createA2aAgent(eq(source), eq("Display"), eq("desc"), eq(AgentStatus.PENDING_SYNC)))
                .thenReturn(created);

        AgentDetailViewDTO detail = mockDetail("AGT-NEW");
        when(agentQueryService.detail("AGT-NEW", null)).thenReturn(detail);

        AgentDetailViewDTO result = service.createA2a(param, "user-1");

        assertNotNull(result);
        assertEquals("AGT-NEW", result.getNum());
        verify(created).save("user-1");
        verify(a2aSyncHistoryRepository, org.mockito.Mockito.atLeastOnce()).save(any(A2aSyncHistory.class));
    }

    @Test
    void createA2a_alreadySubscribed_shouldThrow2012() {
        A2aCreateParam param = new A2aCreateParam();
        param.setNacosAgentName("test-agent");

        A2aSourceInfo source = buildSource();
        when(fetcher.fetch("test-agent")).thenReturn(source);
        when(agentQueryService.findNumByNacosServiceKey("DEFAULT_GROUP@@test-agent"))
                .thenReturn("AGT-EXIST");
        Agent existed = mockAgent("AGT-EXIST", CreationMode.A2A, AgentStatus.PUBLISHED);
        when(agentRepository.findByNum("AGT-EXIST")).thenReturn(existed);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createA2a(param, "user-1"));
        assertEquals(BizCode.A2A_AGENT_ALREADY_SUBSCRIBED.getCode(), ex.getCode());

        verify(agentFactory, never()).createA2aAgent(any(), anyString(), any(), any());
    }

    @Test
    void createA2a_remoteUnreachable_shouldPropagate2011() {
        A2aCreateParam param = new A2aCreateParam();
        param.setNacosAgentName("test-agent");

        when(fetcher.fetch("test-agent")).thenThrow(new BusinessException(
                BizCode.A2A_REMOTE_UNREACHABLE.getCode(), "远端不可达"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createA2a(param, "user-1"));
        assertEquals(BizCode.A2A_REMOTE_UNREACHABLE.getCode(), ex.getCode());

        verify(agentRepository, never()).findByNum(anyString());
    }

    @Test
    void createA2a_draftPromotion_shouldCallApplyNacosSync() {
        A2aCreateParam param = new A2aCreateParam();
        param.setNacosAgentName("test-agent");
        param.setDraftAgentNum("AGT-DRAFT");

        A2aSourceInfo source = buildSource();
        when(fetcher.fetch("test-agent")).thenReturn(source);
        // 唯一性预检：未命中非草稿 → 走草稿转正
        when(agentQueryService.findNumByNacosServiceKey("DEFAULT_GROUP@@test-agent")).thenReturn("AGT-DRAFT");

        Agent draft = mockAgent("AGT-DRAFT", CreationMode.A2A, AgentStatus.DRAFT_ONLY);
        when(agentRepository.findByNum("AGT-DRAFT")).thenReturn(draft);

        AgentDetailViewDTO detail = mockDetail("AGT-DRAFT");
        when(agentQueryService.detail("AGT-DRAFT", null)).thenReturn(detail);

        AgentDetailViewDTO result = service.createA2a(param, "user-1");

        assertNotNull(result);
        verify(draft).applyNacosSync(eq("test-agent"), any(), eq(source), eq(AgentStatus.PENDING_SYNC),
                eq(SyncEventType.MANUAL_RESYNC), eq("user-1"));
        // 草稿转正不应再走 createA2aAgent
        verify(agentFactory, never()).createA2aAgent(any(), anyString(), any(), any());
    }

    @Test
    void createA2a_writesSyncHistory_withManualResyncEvent() {
        A2aCreateParam param = new A2aCreateParam();
        param.setNacosAgentName("test-agent");

        A2aSourceInfo source = buildSource();
        when(fetcher.fetch("test-agent")).thenReturn(source);
        when(agentQueryService.findNumByNacosServiceKey("DEFAULT_GROUP@@test-agent")).thenReturn(null);

        Agent created = mockAgent("AGT-NEW", CreationMode.A2A, AgentStatus.PENDING_SYNC);
        when(agentFactory.createA2aAgent(eq(source), anyString(), any(), any())).thenReturn(created);
        AgentDetailViewDTO detail = mockDetail("AGT-NEW");
        when(agentQueryService.detail("AGT-NEW", null)).thenReturn(detail);

        service.createA2a(param, "user-1");

        org.mockito.ArgumentCaptor<A2aSyncHistory> captor =
                org.mockito.ArgumentCaptor.forClass(A2aSyncHistory.class);
        verify(a2aSyncHistoryRepository).save(captor.capture());
        A2aSyncHistory saved = captor.getValue();
        assertEquals("AGT-NEW", saved.getAgentNum());
        assertEquals(SyncEventType.MANUAL_RESYNC, saved.getSyncEventType());
        assertEquals("user-1", saved.getTriggeredBy());
    }

    // ---------- saveA2aDraft ----------

    @Test
    void saveA2aDraft_createNew_shouldCreateDraftWithNullA2aSource() {
        A2aDraftParam param = new A2aDraftParam();
        param.setDisplayName("我的 A2A 草稿");
        param.setDescription("草稿描述");
        // agentNum = null → 新建

        Agent created = mockAgent("AGT-DRAFT", CreationMode.A2A, AgentStatus.DRAFT_ONLY);
        when(agentFactory.createA2aAgent(eq(null), eq("我的 A2A 草稿"), eq("草稿描述"), eq(AgentStatus.DRAFT_ONLY)))
                .thenReturn(created);

        String num = service.saveA2aDraft(param, "user-1");

        assertEquals("AGT-DRAFT", num);
        verify(created).save("user-1");
    }

    @Test
    void saveA2aDraft_updateExistingDraft_shouldOverwriteNameAndDescription() {
        A2aDraftParam param = new A2aDraftParam();
        param.setAgentNum("AGT-DRAFT");
        param.setDisplayName("改名后的草稿");
        param.setDescription("新描述");

        Agent draft = mockAgent("AGT-DRAFT", CreationMode.A2A, AgentStatus.DRAFT_ONLY);
        when(agentRepository.findByNum("AGT-DRAFT")).thenReturn(draft);

        String num = service.saveA2aDraft(param, "user-1");

        assertEquals("AGT-DRAFT", num);
        verify(draft).setName("改名后的草稿");
        verify(draft).setDescription("新描述");
        verify(draft).save("user-1");
        // 更新路径不应调工厂
        verify(agentFactory, never()).createA2aAgent(any(), anyString(), any(), any());
    }

    @Test
    void saveA2aDraft_updateNonDraft_shouldThrow2010() {
        A2aDraftParam param = new A2aDraftParam();
        param.setAgentNum("AGT-DRAFT");
        param.setDisplayName("改名");

        Agent existing = mockAgent("AGT-DRAFT", CreationMode.A2A, AgentStatus.PENDING_SYNC);
        when(agentRepository.findByNum("AGT-DRAFT")).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveA2aDraft(param, "user-1"));
        assertEquals(BizCode.A2A_AGENT_UNMODIFIABLE.getCode(), ex.getCode());

        verify(existing, never()).save(anyString());
    }

    // ---------- unsubscribeA2a ----------

    @Test
    void unsubscribeA2a_draftOnly_shouldCallDelete() {
        Agent draft = mockAgent("AGT-DRAFT", CreationMode.A2A, AgentStatus.DRAFT_ONLY);
        when(agentFactory.createByNum("AGT-DRAFT")).thenReturn(draft);

        service.unsubscribeA2a("AGT-DRAFT", "user-1");

        verify(draft).delete("user-1");
        verify(a2aSyncHistoryRepository, never()).save(any(A2aSyncHistory.class));
    }

    @Test
    void unsubscribeA2a_offline_shouldReject() {
        Agent offline = mockAgent("AGT-OFFLINE", CreationMode.A2A, AgentStatus.OFFLINE);
        when(agentFactory.createByNum("AGT-OFFLINE")).thenReturn(offline);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.unsubscribeA2a("AGT-OFFLINE", "user-1"));
        assertEquals(BizCode.AGENT_MODE_UNSUPPORTED.getCode(), ex.getCode());

        verify(offline, never()).delete(anyString());
    }
}