package ink.garry.rd.agent.ws.application.evaluation.dataset;

import ink.garry.rd.agent.ws.client.evaluation.dataset.AddDatasetRowParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AddDatasetRowResultVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DeleteDatasetRowParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.UpdateDatasetRowParam;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.factory.EvalDatasetFactory;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Map;
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
 * 评测集手动增删改草稿行单测。
 */
@ExtendWith(MockitoExtension.class)
class EvalDatasetRowManualTest {

    @Mock
    private EvalDatasetFactory evalDatasetFactory;
    @Mock
    private EvalDatasetQueryService evalDatasetQueryService;
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
    void addRow_withDataMap() {
        EvalDataset ds = mock(EvalDataset.class);
        when(evalDatasetFactory.createByNum("EDS1")).thenReturn(ds);
        when(ds.appendDraftRowWithNum(anyString(), eq("u1")))
                .thenReturn(new String[]{"EDR1", "0"});

        AddDatasetRowParam param = new AddDatasetRowParam();
        param.setDatasetNum("EDS1");
        param.setData(Map.of("input", "hi", "reference", "hello"));

        AddDatasetRowResultVO vo = commandService.addRow(param, "u1");
        assertEquals("EDR1", vo.getRowNum());
        assertEquals(0, vo.getRowIndex());
        verify(ds).appendDraftRowWithNum(anyString(), eq("u1"));
    }

    @Test
    void addRow_missingData() {
        AddDatasetRowParam param = new AddDatasetRowParam();
        param.setDatasetNum("EDS1");
        assertThrows(BusinessException.class, () -> commandService.addRow(param, "u1"));
    }

    @Test
    void deleteRow_ok() {
        EvalDataset ds = mock(EvalDataset.class);
        when(evalDatasetFactory.createByNum("EDS1")).thenReturn(ds);

        DeleteDatasetRowParam param = new DeleteDatasetRowParam();
        param.setDatasetNum("EDS1");
        param.setRowNum("EDR1");
        commandService.deleteRow(param, "u1");
        verify(ds).deleteDraftRow("EDR1", "u1");
    }

    @Test
    void updateRow_withDataMap() {
        EvalDataset ds = mock(EvalDataset.class);
        when(evalDatasetFactory.createByNum("EDS1")).thenReturn(ds);

        UpdateDatasetRowParam param = new UpdateDatasetRowParam();
        param.setDatasetNum("EDS1");
        param.setRowNum("EDR1");
        param.setData(Map.of("input", "updated"));

        commandService.updateRow(param, "u1");
        verify(ds).updateDraftRow(eq("EDR1"), anyString(), eq("u1"));
    }

    @Test
    void updateRow_missingData() {
        UpdateDatasetRowParam param = new UpdateDatasetRowParam();
        param.setDatasetNum("EDS1");
        param.setRowNum("EDR1");
        assertThrows(BusinessException.class, () -> commandService.updateRow(param, "u1"));
    }
}
