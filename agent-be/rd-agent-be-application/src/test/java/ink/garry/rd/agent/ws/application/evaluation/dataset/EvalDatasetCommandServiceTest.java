package ink.garry.rd.agent.ws.application.evaluation.dataset;

import ink.garry.rd.agent.ws.application.evaluation.support.XlsxDatasetImporter;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CreateDatasetParam;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.factory.EvalDatasetFactory;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvalDatasetCommandService 核心创建路径单测。
 */
@ExtendWith(MockitoExtension.class)
class EvalDatasetCommandServiceTest {

    @Mock
    private EvalDatasetFactory evalDatasetFactory;
    @Mock
    private EvalDatasetQueryService evalDatasetQueryService;
    @Mock
    private XlsxDatasetImporter xlsxDatasetImporter;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @InjectMocks
    private EvalDatasetCommandService commandService;

    @BeforeEach
    void setUp() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void create_success() {
        when(evalDatasetQueryService.existsByName(eq("WS1"), eq("集1"), eq(null))).thenReturn(false);
        EvalDataset mockDs = mock(EvalDataset.class);
        when(mockDs.getNum()).thenReturn("EDS1");
        when(evalDatasetFactory.create(eq("WS1"), eq("集1"), any(), eq(DatasetType.AGENT), eq("AGT1"), any()))
                .thenReturn(mockDs);

        CreateDatasetParam param = new CreateDatasetParam();
        param.setName("集1");
        param.setType("AGENT");
        param.setAgentNum("AGT1");
        param.setSchemaJson("[{\"name\":\"input\"}]");

        String num = commandService.create(param, "WS1", "u1");
        assertEquals("EDS1", num);
        verify(mockDs).save("u1");
    }

    @Test
    void create_conflictName() {
        when(evalDatasetQueryService.existsByName(eq("WS1"), eq("集1"), eq(null))).thenReturn(true);
        CreateDatasetParam param = new CreateDatasetParam();
        param.setName("集1");
        param.setType("CUSTOM");
        param.setSchemaJson("[]");
        assertThrows(BusinessException.class, () -> commandService.create(param, "WS1", "u1"));
    }
}
