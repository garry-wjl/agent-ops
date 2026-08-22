package ink.garry.rd.agent.ws.infra.evaluation.gateway;

import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalNumGatewayImplTest {

    @Mock
    private BizNumGenerator bizNumGenerator;

    @InjectMocks
    private EvalNumGatewayImpl gateway;

    @Test
    void generateCaseGenJobNum_usesEcgPrefix() {
        when(bizNumGenerator.generate("ECG")).thenReturn("ECG20260822001");
        assertEquals("ECG20260822001", gateway.generateCaseGenJobNum());
        verify(bizNumGenerator).generate(eq("ECG"));
    }

    @Test
    void generateDatasetNum_usesEdsPrefix() {
        when(bizNumGenerator.generate("EDS")).thenReturn("EDS1");
        assertEquals("EDS1", gateway.generateDatasetNum());
    }
}
