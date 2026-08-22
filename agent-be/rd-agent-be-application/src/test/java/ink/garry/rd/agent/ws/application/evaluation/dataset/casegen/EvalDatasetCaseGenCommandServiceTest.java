package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.client.agent.AgentDebugVersionVO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.RetryCaseGenParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.StartCaseGenParam;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetCaseGenJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动生成 Case 写侧：启动 / 重试 / 参数规范化。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalDatasetCaseGenCommandServiceTest {

    @Mock
    private EvalDatasetCaseGenJobMapper caseGenJobMapper;
    @Mock
    private EvalNumGateway evalNumGateway;
    @Mock
    private EvalDatasetQueryService evalDatasetQueryService;
    @Mock
    private EvalDatasetCaseGenQueryService caseGenQueryService;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private EvalDatasetCaseGenWorker caseGenWorker;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @InjectMocks
    private EvalDatasetCaseGenCommandService commandService;

    @BeforeEach
    void setUp() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void normalizeTargetCount_nullAndCap() {
        assertNull(EvalDatasetCaseGenCommandService.normalizeTargetCount(null));
        assertEquals(50, EvalDatasetCaseGenCommandService.normalizeTargetCount(100));
        assertEquals(10, EvalDatasetCaseGenCommandService.normalizeTargetCount(10));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> EvalDatasetCaseGenCommandService.normalizeTargetCount(0));
        assertEquals(BizCode.INVALID_PARAM.getCode(), ex.getCode());
    }

    @Test
    void normalizeMode_defaultAndInvalid() {
        assertEquals("APPEND", EvalDatasetCaseGenCommandService.normalizeMode(null));
        assertEquals("OVERRIDE", EvalDatasetCaseGenCommandService.normalizeMode("override"));
        assertThrows(BusinessException.class,
                () -> EvalDatasetCaseGenCommandService.normalizeMode("MERGE"));
    }

    @Test
    void start_success_defaultsOnlineVersion_andTriggersWorker() {
        stubDataset();
        stubGeneratorOnline("AGT-G", "v1.0.0");
        when(caseGenJobMapper.selectCount(any())).thenReturn(0L);
        when(evalNumGateway.generateCaseGenJobNum()).thenReturn("ECG1");

        StartCaseGenParam param = new StartCaseGenParam();
        param.setDatasetNum("EDS1");
        param.setGeneratorAgentNum("AGT-G");
        param.setTargetCount(80);
        param.setClearDraft(true);
        param.setInstructionMode("APPEND");
        param.setUserInstruction("多覆盖边界");

        String jobNum = commandService.start(param, "WS1", "u1");
        assertEquals("ECG1", jobNum);

        ArgumentCaptor<EvalDatasetCaseGenJobEntity> cap =
                ArgumentCaptor.forClass(EvalDatasetCaseGenJobEntity.class);
        verify(caseGenJobMapper).insert(cap.capture());
        EvalDatasetCaseGenJobEntity saved = cap.getValue();
        assertEquals("ECG1", saved.getNum());
        assertEquals("EDS1", saved.getDatasetNum());
        assertEquals("AGT-G", saved.getGeneratorAgentNum());
        assertEquals("v1.0.0", saved.getGeneratorAgentVersionNum());
        assertEquals(50, saved.getTargetCount());
        assertTrue(Boolean.TRUE.equals(saved.getClearDraft()));
        assertEquals("PENDING", saved.getStatus());
        assertEquals("多覆盖边界", saved.getUserInstruction());
        verify(caseGenWorker).runAsync("ECG1", "u1");
    }

    @Test
    void start_usesRequestedVersion() {
        stubDataset();
        AgentDetailViewDTO detail = new AgentDetailViewDTO();
        detail.setNum("AGT-G");
        detail.setCurrentVersionNum("v1.0.0");
        when(agentQueryService.detail(eq("AGT-G"), eq(null))).thenReturn(detail);
        when(agentQueryService.debugVersionList("AGT-G")).thenReturn(List.of(
                AgentDebugVersionVO.builder().versionNum("v1.0.0").status("PUBLISHED").current(true).build(),
                AgentDebugVersionVO.builder().versionNum("v0.9.0").status("ARCHIVED").current(false).build()
        ));
        when(caseGenJobMapper.selectCount(any())).thenReturn(0L);
        when(evalNumGateway.generateCaseGenJobNum()).thenReturn("ECG2");

        StartCaseGenParam param = new StartCaseGenParam();
        param.setDatasetNum("EDS1");
        param.setGeneratorAgentNum("AGT-G");
        param.setGeneratorAgentVersionNum("v0.9.0");

        commandService.start(param, "WS1", "u1");
        ArgumentCaptor<EvalDatasetCaseGenJobEntity> cap =
                ArgumentCaptor.forClass(EvalDatasetCaseGenJobEntity.class);
        verify(caseGenJobMapper).insert(cap.capture());
        assertEquals("v0.9.0", cap.getValue().getGeneratorAgentVersionNum());
    }

    @Test
    void start_activeJobConflict() {
        stubDataset();
        stubGeneratorOnline("AGT-G", "v1.0.0");
        when(caseGenJobMapper.selectCount(any())).thenReturn(1L);

        StartCaseGenParam param = new StartCaseGenParam();
        param.setDatasetNum("EDS1");
        param.setGeneratorAgentNum("AGT-G");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commandService.start(param, "WS1", "u1"));
        assertEquals(BizCode.CONFLICT.getCode(), ex.getCode());
        verify(caseGenJobMapper, never()).insert(any());
        verify(caseGenWorker, never()).runAsync(anyString(), anyString());
    }

    @Test
    void start_noOnlineVersion_throws() {
        stubDataset();
        AgentDetailViewDTO detail = new AgentDetailViewDTO();
        detail.setNum("AGT-G");
        when(agentQueryService.detail(eq("AGT-G"), eq(null))).thenReturn(detail);
        when(agentQueryService.debugVersionList("AGT-G")).thenReturn(List.of(
                AgentDebugVersionVO.builder().status("DRAFT").versionNum(null).current(false).build()
        ));

        StartCaseGenParam param = new StartCaseGenParam();
        param.setDatasetNum("EDS1");
        param.setGeneratorAgentNum("AGT-G");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commandService.start(param, "WS1", "u1"));
        assertEquals(BizCode.INVALID_PARAM.getCode(), ex.getCode());
    }

    @Test
    void retry_onlyFailed_andCopiesConfig() {
        CaseGenJobVO old = new CaseGenJobVO();
        old.setNum("ECG-OLD");
        old.setDatasetNum("EDS1");
        old.setGeneratorAgentNum("AGT-G");
        old.setGeneratorAgentVersionNum("v1.0.0");
        old.setTargetCount(5);
        old.setClearDraft(false);
        old.setInstructionMode("OVERRIDE");
        old.setUserInstruction("x");
        old.setStatus("FAILED");
        when(caseGenQueryService.detail("ECG-OLD", "WS1")).thenReturn(old);
        when(caseGenJobMapper.selectCount(any())).thenReturn(0L);
        when(evalNumGateway.generateCaseGenJobNum()).thenReturn("ECG-NEW");

        RetryCaseGenParam param = new RetryCaseGenParam();
        param.setJobNum("ECG-OLD");
        String jobNum = commandService.retry(param, "WS1", "u1");
        assertEquals("ECG-NEW", jobNum);

        ArgumentCaptor<EvalDatasetCaseGenJobEntity> cap =
                ArgumentCaptor.forClass(EvalDatasetCaseGenJobEntity.class);
        verify(caseGenJobMapper).insert(cap.capture());
        assertEquals("ECG-OLD", cap.getValue().getRetryOfNum());
        assertEquals("OVERRIDE", cap.getValue().getInstructionMode());
        assertEquals(5, cap.getValue().getTargetCount());
        verify(caseGenWorker).runAsync("ECG-NEW", "u1");
    }

    @Test
    void retry_nonFailed_throws() {
        CaseGenJobVO old = new CaseGenJobVO();
        old.setNum("ECG-OLD");
        old.setStatus("FINISHED");
        when(caseGenQueryService.detail("ECG-OLD", "WS1")).thenReturn(old);

        RetryCaseGenParam param = new RetryCaseGenParam();
        param.setJobNum("ECG-OLD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> commandService.retry(param, "WS1", "u1"));
        assertEquals(BizCode.INVALID_PARAM.getCode(), ex.getCode());
    }

    private void stubDataset() {
        EvalDatasetDetailVO ds = new EvalDatasetDetailVO();
        ds.setNum("EDS1");
        ds.setType("CUSTOM");
        when(evalDatasetQueryService.detail("EDS1", "WS1")).thenReturn(ds);
    }

    private void stubGeneratorOnline(String agentNum, String version) {
        AgentDetailViewDTO detail = new AgentDetailViewDTO();
        detail.setNum(agentNum);
        detail.setCurrentVersionNum(version);
        when(agentQueryService.detail(eq(agentNum), eq(null))).thenReturn(detail);
        when(agentQueryService.debugVersionList(agentNum)).thenReturn(List.of(
                AgentDebugVersionVO.builder()
                        .versionNum(version)
                        .status("PUBLISHED")
                        .current(true)
                        .build()
        ));
    }
}
