package ink.garry.rd.agent.ws.application.evaluation.task;

import ink.garry.rd.agent.ws.client.evaluation.task.PublishGateCheckVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布门禁：关闭时不要求 versionNum；开启且无版本时视为首次发布放行。
 */
class EvalPublishGateServiceTest {

    private EvalPublishGateService service;

    @BeforeEach
    void setUp() {
        service = new EvalPublishGateService();
        ReflectionTestUtils.setField(service, "passRateThreshold", 0.8d);
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
}
