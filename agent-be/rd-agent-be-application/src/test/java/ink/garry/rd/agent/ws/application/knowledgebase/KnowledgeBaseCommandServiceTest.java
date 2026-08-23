package ink.garry.rd.agent.ws.application.knowledgebase;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbCreateParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDTO;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.factory.KnowledgeBaseFactory;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KbIndexTaskRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.knowledgebase.config.KnowledgeBaseProperties;
import ink.garry.rd.agent.ws.infra.knowledgebase.support.KnowledgeBaseRagGatewayRegistry;
import ink.garry.rd.agent.ws.domain.attachment.factory.ChatAttachmentFactory;
import ink.garry.rd.agent.ws.application.knowledgebase.task.KbIndexTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseCommandServiceTest {

    @Mock private KnowledgeBaseFactory knowledgeBaseFactory;
    @Mock private KnowledgeBaseQueryService knowledgeBaseQueryService;
    @Mock private KnowledgeBaseRagGatewayRegistry ragGatewayRegistry;
    @Mock private KbIndexTaskRepository kbIndexTaskRepository;
    @Mock private KbNumGateway kbNumGateway;
    @Mock private ChatAttachmentFactory chatAttachmentFactory;
    @Mock private KbIndexTaskExecutor kbIndexTaskExecutor;
    @Mock private RedissonClient redissonClient;
    @Mock private KnowledgeBaseProperties knowledgeBaseProperties;
    @Mock private RLock rLock;

    @InjectMocks
    private KnowledgeBaseCommandService commandService;

    @BeforeEach
    void setUp() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void create_success() {
        when(knowledgeBaseQueryService.existsByName(eq("WS1"), eq("技术库"), eq(null))).thenReturn(false);
        KnowledgeBase kb = mock(KnowledgeBase.class);
        when(kb.getNum()).thenReturn("KB1");
        when(kb.getKbType()).thenReturn(KbType.SIMPLE);
        when(kb.getStatus()).thenReturn(ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbStatus.READY);
        when(kb.getIndexConfig()).thenReturn(ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig.builder().configVersion(1).build());
        when(knowledgeBaseFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(kb);
        var gateway = mock(ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KnowledgeBaseRagGateway.class);
        when(ragGatewayRegistry.get(KbType.SIMPLE)).thenReturn(gateway);
        when(gateway.provision(kb)).thenReturn(KbSourceConfig.builder().vectorCollectionName("kb_KB1").dimensions(1024).build());

        KbCreateParamDTO param = new KbCreateParamDTO();
        param.setWorkspaceNum("WS1");
        param.setName("技术库");
        param.setKbType("SIMPLE");

        KbDTO dto = commandService.create(param, "u1");
        assertEquals("KB1", dto.getKbNum());
        verify(kb).save("u1");
        verify(kb).applyProvisioned(any(), eq("u1"));
    }

    @Test
    void create_nameConflict() {
        when(knowledgeBaseQueryService.existsByName(eq("WS1"), eq("技术库"), eq(null))).thenReturn(true);
        KbCreateParamDTO param = new KbCreateParamDTO();
        param.setWorkspaceNum("WS1");
        param.setName("技术库");
        param.setKbType("SIMPLE");
        BusinessException ex = assertThrows(BusinessException.class, () -> commandService.create(param, "u1"));
        assertEquals(BizCode.KB_NAME_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    void delete_boundByAgent_shouldReject() {
        KnowledgeBase kb = mock(KnowledgeBase.class);
        when(kb.getNum()).thenReturn("KB1");
        when(knowledgeBaseFactory.createByNum("KB1")).thenReturn(kb);
        when(knowledgeBaseQueryService.countAgentsBound("KB1")).thenReturn(2);

        BusinessException ex = assertThrows(BusinessException.class, () -> commandService.delete("KB1", "u1"));
        assertEquals(BizCode.KB_BOUND_BY_AGENT.getCode(), ex.getCode());
    }

    @Test
    void updateIndexConfig_dimensionConflict_shouldReject() {
        KnowledgeBase kb = mock(KnowledgeBase.class);
        when(kb.getNum()).thenReturn("KB1");
        when(kb.getIndexConfig()).thenReturn(KbIndexConfig.builder().configVersion(1).build());
        when(knowledgeBaseFactory.createByNum("KB1")).thenReturn(kb);
        when(knowledgeBaseQueryService.countIndexedFiles("KB1")).thenReturn(1);
        when(knowledgeBaseQueryService.isEmbeddingDimensionCompatible(any(), any())).thenReturn(false);

        ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUpdateIndexConfigParamDTO param =
                new ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUpdateIndexConfigParamDTO();
        param.setKbNum("KB1");

        doThrow(new BusinessException(BizCode.KB_EMBEDDING_DIMENSION_CONFLICT.getCode(), "维度冲突"))
                .when(kb).updateIndexConfig(any(), eq(1), eq(false), eq("u1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commandService.updateIndexConfig(param, "u1"));
        assertEquals(BizCode.KB_EMBEDDING_DIMENSION_CONFLICT.getCode(), ex.getCode());
    }
}
