package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetDetailVO;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.factory.EvalDatasetFactory;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetCaseGenJobMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Worker：成功写入草稿 / 失败落 FAILED / 清空草稿路径。
 */
@ExtendWith(MockitoExtension.class)
class EvalDatasetCaseGenWorkerTest {

    @Mock
    private EvalDatasetCaseGenJobMapper caseGenJobMapper;
    @Mock
    private EvalDatasetQueryService evalDatasetQueryService;
    @Mock
    private EvalDatasetFactory evalDatasetFactory;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private AgentInvokeService agentInvokeService;

    @InjectMocks
    private EvalDatasetCaseGenWorker worker;

    @Test
    void runAsync_appendValidCases_marksFinished() {
        ReflectionTestUtils.setField(worker, "invokeTimeoutSeconds", 30);
        EvalDatasetCaseGenJobEntity job = pendingJob(false);
        when(caseGenJobMapper.selectOne(any())).thenReturn(job);

        EvalDatasetDetailVO ds = new EvalDatasetDetailVO();
        ds.setNum("EDS1");
        ds.setName("集1");
        ds.setType("CUSTOM");
        ds.setSchemaJson("[{\"name\":\"input\",\"type\":\"string\"}]");
        when(evalDatasetQueryService.detail("EDS1", "WS1")).thenReturn(ds);

        String agentOut = "[{\"input\":\"q1\"},{\"input\":\"q2\"},{\"zzz\":1}]";
        when(agentInvokeService.invokeStream(
                eq("AGT-G"), anyString(), isNull(), isNull(), eq("u1"), eq("v1.0.0"), isNull()))
                .thenReturn(textFlux(agentOut));

        EvalDataset dataset = mock(EvalDataset.class);
        when(evalDatasetFactory.createByNum("EDS1")).thenReturn(dataset);

        worker.runAsync("ECG1", "u1");

        verify(dataset).appendDraftRow(contains("q1"), eq("u1"));
        verify(dataset).appendDraftRow(contains("q2"), eq("u1"));
        ArgumentCaptor<EvalDatasetCaseGenJobEntity> cap =
                ArgumentCaptor.forClass(EvalDatasetCaseGenJobEntity.class);
        verify(caseGenJobMapper, atLeastOnce()).updateById(cap.capture());
        assertTrue(cap.getAllValues().stream()
                .anyMatch(e -> "FINISHED".equals(e.getStatus())));
        EvalDatasetCaseGenJobEntity finished = cap.getAllValues().stream()
                .filter(e -> "FINISHED".equals(e.getStatus()))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertEquals(2, finished.getWrittenCount());
        assertEquals(1, finished.getSkippedCount());
    }

    @Test
    void runAsync_clearDraft_usesReplace() {
        ReflectionTestUtils.setField(worker, "invokeTimeoutSeconds", 30);
        EvalDatasetCaseGenJobEntity job = pendingJob(true);
        when(caseGenJobMapper.selectOne(any())).thenReturn(job);

        EvalDatasetDetailVO ds = new EvalDatasetDetailVO();
        ds.setNum("EDS1");
        ds.setName("集1");
        ds.setType("CUSTOM");
        ds.setSchemaJson("[{\"name\":\"input\"}]");
        when(evalDatasetQueryService.detail("EDS1", "WS1")).thenReturn(ds);
        when(agentInvokeService.invokeStream(
                eq("AGT-G"), anyString(), isNull(), isNull(), eq("u1"), eq("v1.0.0"), isNull()))
                .thenReturn(textFlux("[{\"input\":\"only\"}]"));

        EvalDataset dataset = mock(EvalDataset.class);
        when(evalDatasetFactory.createByNum("EDS1")).thenReturn(dataset);

        worker.runAsync("ECG1", "u1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> rows = ArgumentCaptor.forClass(List.class);
        verify(dataset).replaceDraftRows(rows.capture(), eq("u1"));
        assertEquals(1, rows.getValue().size());
        assertTrue(rows.getValue().get(0).contains("only"));
    }

    @Test
    void runAsync_blankAgentOutput_marksFailed() {
        ReflectionTestUtils.setField(worker, "invokeTimeoutSeconds", 30);
        EvalDatasetCaseGenJobEntity job = pendingJob(false);
        when(caseGenJobMapper.selectOne(any())).thenReturn(job);

        EvalDatasetDetailVO ds = new EvalDatasetDetailVO();
        ds.setNum("EDS1");
        ds.setName("集1");
        ds.setType("CUSTOM");
        ds.setSchemaJson("[{\"name\":\"input\"}]");
        when(evalDatasetQueryService.detail("EDS1", "WS1")).thenReturn(ds);
        when(agentInvokeService.invokeStream(
                eq("AGT-G"), anyString(), isNull(), isNull(), eq("u1"), eq("v1.0.0"), isNull()))
                .thenReturn(Flux.empty());

        worker.runAsync("ECG1", "u1");

        ArgumentCaptor<EvalDatasetCaseGenJobEntity> cap =
                ArgumentCaptor.forClass(EvalDatasetCaseGenJobEntity.class);
        verify(caseGenJobMapper, atLeastOnce()).updateById(cap.capture());
        assertTrue(cap.getAllValues().stream()
                .anyMatch(e -> "FAILED".equals(e.getStatus())));
    }

    private EvalDatasetCaseGenJobEntity pendingJob(boolean clearDraft) {
        EvalDatasetCaseGenJobEntity job = new EvalDatasetCaseGenJobEntity();
        job.setNum("ECG1");
        job.setWorkspaceNum("WS1");
        job.setDatasetNum("EDS1");
        job.setGeneratorAgentNum("AGT-G");
        job.setGeneratorAgentVersionNum("v1.0.0");
        job.setClearDraft(clearDraft);
        job.setInstructionMode("APPEND");
        job.setStatus("PENDING");
        job.setTargetCount(10);
        job.setDeleted(0);
        return job;
    }

    /** SegmentAccumulator 只消费 isLast=true 的 REASONING 帧。 */
    private Flux<Event> textFlux(String text) {
        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder().text(text).build()))
                .build();
        Event event = new Event(EventType.REASONING, msg, true);
        return Flux.just(event);
    }
}
