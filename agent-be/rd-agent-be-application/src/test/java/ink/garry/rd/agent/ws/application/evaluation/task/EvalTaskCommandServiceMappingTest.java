package ink.garry.rd.agent.ws.application.evaluation.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.application.evaluation.grader.EvalGraderQueryService;
import ink.garry.rd.agent.ws.application.evaluation.support.GraderBindingSnapshot;
import ink.garry.rd.agent.ws.client.evaluation.grader.EvalGraderVO;
import ink.garry.rd.agent.ws.client.evaluation.task.CreateAndStartTaskParam;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskItemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建任务时评估器 mapping 快照：默认约定 vs 自定义变量。
 */
@ExtendWith(MockitoExtension.class)
class EvalTaskCommandServiceMappingTest {

    @Mock
    private EvalTaskFactory evalTaskFactory;
    @Mock
    private EvalTaskItemFactory evalTaskItemFactory;
    @Mock
    private EvalDatasetQueryService evalDatasetQueryService;
    @Mock
    private EvalGraderQueryService evalGraderQueryService;
    @Mock
    private EvalTaskQueryService evalTaskQueryService;
    @Mock
    private EvalTaskWorker evalTaskWorker;
    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private EvalTaskCommandService evalTaskCommandService;

    @BeforeEach
    void setUpLock() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void createAndStart_emptyMapping_fillsDefaultResponseReference() {
        when(evalDatasetQueryService.versionExists("EDS1", 1)).thenReturn(true);
        stubGrader("G1", "LLM", null, "{\"promptTemplate\":\"x\",\"modelNum\":\"M1\"}");
        stubTaskCreate();

        CreateAndStartTaskParam param = baseParam();
        CreateAndStartTaskParam.GraderBindingParam gb = new CreateAndStartTaskParam.GraderBindingParam();
        gb.setGraderNum("G1");
        gb.setMapping(null);
        param.setGraders(List.of(gb));

        evalTaskCommandService.createAndStart(param, "WS1", "u1");

        Map<String, String> mapping = captureFirstMapping();
        assertEquals("$actual_output", mapping.get("response"));
        assertEquals("$row.reference", mapping.get("reference"));
        assertEquals(2, mapping.size());
    }

    @Test
    void createAndStart_emptyMap_alsoFillsDefault() {
        when(evalDatasetQueryService.versionExists("EDS1", 1)).thenReturn(true);
        stubGrader("G1", "BUILTIN", "NON_EMPTY", "{}");
        stubTaskCreate();

        CreateAndStartTaskParam param = baseParam();
        CreateAndStartTaskParam.GraderBindingParam gb = new CreateAndStartTaskParam.GraderBindingParam();
        gb.setGraderNum("G1");
        gb.setMapping(Map.of());
        param.setGraders(List.of(gb));

        evalTaskCommandService.createAndStart(param, "WS1", "u1");

        Map<String, String> mapping = captureFirstMapping();
        assertEquals("$actual_output", mapping.get("response"));
        assertEquals("$row.reference", mapping.get("reference"));
    }

    @Test
    void createAndStart_customMapping_persistedAsIs() {
        when(evalDatasetQueryService.versionExists("EDS1", 1)).thenReturn(true);
        stubGrader("G_LLM", "LLM", null, "{\"promptTemplate\":\"{{answer}} {{gold}}\",\"modelNum\":\"M1\"}");
        stubTaskCreate();

        CreateAndStartTaskParam param = baseParam();
        CreateAndStartTaskParam.GraderBindingParam gb = new CreateAndStartTaskParam.GraderBindingParam();
        gb.setGraderNum("G_LLM");
        gb.setMapping(Map.of(
                "answer", "$actual_output",
                "gold", "$row.expected_answer",
                "policy", "$row.policy"));
        param.setGraders(List.of(gb));

        evalTaskCommandService.createAndStart(param, "WS1", "u1");

        Map<String, String> mapping = captureFirstMapping();
        assertEquals("$actual_output", mapping.get("answer"));
        assertEquals("$row.expected_answer", mapping.get("gold"));
        assertEquals("$row.policy", mapping.get("policy"));
        assertEquals(3, mapping.size());
    }

    private void stubGrader(String num, String kind, String builtin, String configJson) {
        EvalGraderVO vo = new EvalGraderVO();
        vo.setNum(num);
        vo.setVersion(1);
        vo.setKind(kind);
        vo.setBuiltinCode(builtin);
        vo.setConfigJson(configJson);
        when(evalGraderQueryService.detail(num, "WS1")).thenReturn(vo);
    }

    private void stubTaskCreate() {
        EvalTask task = mock(EvalTask.class);
        when(task.getNum()).thenReturn("ETK1");
        doNothing().when(task).save(anyString());
        doNothing().when(task).markRunning(anyString());
        when(evalTaskFactory.create(
                eq("WS1"), eq("t1"), any(), eq("EDS1"), eq(1),
                any(), eq("AGT1"), eq("AV1"), anyString(), isNull(), eq("u1")))
                .thenReturn(task);
    }

    private CreateAndStartTaskParam baseParam() {
        CreateAndStartTaskParam param = new CreateAndStartTaskParam();
        param.setName("t1");
        param.setDatasetNum("EDS1");
        param.setDatasetVersion(1);
        param.setBindMode("AGENT");
        param.setAgentNum("AGT1");
        param.setAgentVersionNum("AV1");
        return param;
    }

    private Map<String, String> captureFirstMapping() {
        ArgumentCaptor<String> bindingsCaptor = ArgumentCaptor.forClass(String.class);
        verify(evalTaskFactory).create(
                eq("WS1"), eq("t1"), any(), eq("EDS1"), eq(1),
                any(), eq("AGT1"), eq("AV1"), bindingsCaptor.capture(), isNull(), eq("u1"));
        List<GraderBindingSnapshot> snaps = JSON.parseObject(
                bindingsCaptor.getValue(),
                new TypeReference<List<GraderBindingSnapshot>>() {
                });
        assertEquals(1, snaps.size());
        return snaps.get(0).getMapping();
    }
}
