package ink.garry.rd.agent.ws.application.evaluation.task;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.task.PublishGateCheckVO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDetailDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseQueryService;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 发布门禁：关闭时不要求 versionNum；开启且无版本时视为首次发布放行。
 */
@ExtendWith(MockitoExtension.class)
class EvalPublishGateServiceTest {

    private EvalPublishGateService service;

    @Mock
    private KnowledgeBaseQueryService knowledgeBaseQueryService;

    @BeforeEach
    void setUp() {
        service = new EvalPublishGateService();
        ReflectionTestUtils.setField(service, "passRateThreshold", 0.8d);
        ReflectionTestUtils.setField(service, "knowledgeBaseQueryService", knowledgeBaseQueryService);
    }

    @Test
    void whenGateDisabled_blankVersionNum_shouldPass() {
        ReflectionTestUtils.setField(service, "enabled", false);
        PublishGateCheckVO vo = service.checkPublishGate("AGT1", null, "WS1");
        assertTrue(vo.isPassed());
        assertEquals(false, vo.isEnabled());
        assertDoesNotThrow(() -> service.checkAgentPublish("AGT1", null, "WS1"));
    }

    @Test
    void whenGateEnabled_blankVersionNum_shouldSkipAsFirstPublish() {
        ReflectionTestUtils.setField(service, "enabled", true);
        PublishGateCheckVO vo = service.checkPublishGate("AGT1", "  ", "WS1");
        assertTrue(vo.isEnabled());
        assertTrue(vo.isPassed());
        assertTrue(vo.getMessage().contains("首次发布"));
        assertDoesNotThrow(() -> service.checkAgentPublish("AGT1", null, "WS1"));
    }

    @Test
    void checkKnowledgeBasesReady_allReady_shouldPass() {
        when(knowledgeBaseQueryService.detail("KB1", null))
                .thenReturn(KbDetailDTO.builder().kbNum("KB1").status("READY").build());
        ConfigSnapshot snap = ConfigSnapshot.builder()
                .knowledgeBaseBindings(List.of(
                        KnowledgeBaseBinding.builder().kbNum("KB1").build()))
                .build();
        assertDoesNotThrow(() -> service.checkKnowledgeBasesReady(snap));
    }

    @Test
    void checkKnowledgeBasesReady_notReady_shouldReject() {
        when(knowledgeBaseQueryService.detail("KB1", null))
                .thenReturn(KbDetailDTO.builder().kbNum("KB1").status("INDEXING").build());
        ConfigSnapshot snap = ConfigSnapshot.builder()
                .knowledgeBaseBindings(List.of(
                        KnowledgeBaseBinding.builder().kbNum("KB1").build()))
                .build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkKnowledgeBasesReady(snap));
        assertEquals(BizCode.KB_STATUS_INVALID.getCode(), ex.getCode());
    }
}
