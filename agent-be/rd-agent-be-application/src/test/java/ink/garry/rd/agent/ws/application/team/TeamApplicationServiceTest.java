package ink.garry.rd.agent.ws.application.team;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.team.AgentTeamRunVO;
import ink.garry.rd.agent.ws.client.team.TeamRunParam;
import ink.garry.rd.agent.ws.domain.team.gateway.TeamNumGateway;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.GraphRuntime;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRepository;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRepository.TeamRecord;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRunRepository;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRunRepository.RunRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 启动运行会分配共享目录，并在当前线程里把唯一节点跑完。
 */
@ExtendWith(MockitoExtension.class)
class TeamApplicationServiceTest {

    @Mock
    private AgentTeamRepository agentTeamRepository;
    @Mock
    private AgentTeamRunRepository agentTeamRunRepository;
    @Mock
    private TeamNumGateway teamNumGateway;
    @Mock
    private TeamAgentInvoker teamAgentInvoker;

    @InjectMocks
    private TeamApplicationService service;

    private final AtomicReference<RunRecord> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service.useCallerThread();
        when(teamNumGateway.nextRunNum()).thenReturn("ATR1");
        when(agentTeamRepository.find("WS1", "ATM1")).thenReturn(Optional.of(team()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(agentTeamRunRepository).insert(any(), anyString());
        when(agentTeamRunRepository.find("WS1", "ATR1")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        doAnswer(invocation -> {
            RunRecord current = stored.get();
            stored.set(new RunRecord(current.num(), current.teamNum(), current.workspaceNum(), current.teamName(),
                    current.mode(), current.inputText(), invocation.getArgument(1), current.ossSubPath(),
                    current.snapshotJson(), invocation.getArgument(2), current.updateNo(), current.createTime()));
            return null;
        }).when(agentTeamRunRepository).updateRuntime(anyString(), anyString(), anyString(), anyString());
        when(teamAgentInvoker.invoke(anyString(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new TeamAgentInvoker.Result("SES1", "已写好方案"));
    }

    @Test
    void start_allocatesSharedDirectoryAndFinishesTheOnlyNode() {
        TeamRunParam param = new TeamRunParam();
        param.setTeamNum("ATM1");
        param.setInput("整理发布说明");

        AgentTeamRunVO vo = service.start("WS1", param, "u1");

        assertEquals("WS1/runs/ATR1", vo.getOssSubPath());
        assertEquals("succeeded", stored.get().status());
        GraphRuntime runtime = JSON.parseObject(stored.get().runtimeJson(), GraphRuntime.class);
        assertEquals("已写好方案", runtime.getNodes().get("plan").getOutput());
        assertEquals("SES1", runtime.getNodes().get("plan").getSessionNum());
        assertTrue(stored.get().snapshotJson().contains("\"x\":48"));
    }

    private static TeamRecord team() {
        String definition = """
                {"mode":"orchestration","nodes":[{"id":"plan","kind":"agent","name":"规划","x":48,"y":140,
                "agentNum":"AGT1","agentName":"规划员","version":"","promptTemplate":"{{run.input}}","maxAttempts":1,
                "rejectTo":"","maxReworks":0,"gatePrompt":""}],"edges":[],"leader":{},"workers":[]}
                """;
        return new TeamRecord("ATM1", "WS1", "发布", "", "orchestration", "DRAFT",
                definition, "u1", LocalDateTime.now());
    }
}
