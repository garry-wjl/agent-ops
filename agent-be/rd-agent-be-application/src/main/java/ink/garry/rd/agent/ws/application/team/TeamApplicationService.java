package ink.garry.rd.agent.ws.application.team;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.team.AgentTeamBucketVO;
import ink.garry.rd.agent.ws.client.team.AgentTeamRunVO;
import ink.garry.rd.agent.ws.client.team.AgentTeamVO;
import ink.garry.rd.agent.ws.client.team.TeamGateParam;
import ink.garry.rd.agent.ws.client.team.TeamRunParam;
import ink.garry.rd.agent.ws.client.team.TeamSaveParam;
import ink.garry.rd.agent.ws.domain.team.RunWorkspaceMount;
import ink.garry.rd.agent.ws.domain.team.TeamGraphEngine;
import ink.garry.rd.agent.ws.domain.team.gateway.TeamNumGateway;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.FlowNode;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.GraphRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.LeaderRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.NodeRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamDefinition;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamWorker;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.WorkerRuntime;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRepository;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRepository.TeamRecord;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRunRepository;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRunRepository.RunRecord;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.UserContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Agent Team 的保存、发布和运行。
 * <p>
 * 每次运行在 OSS 上分配一块目录，后续每个 Agent 会话都挂这同一块。调度本身不执行模型，只调用已有的 Agent 运行入口。
 */
@Service
public class TeamApplicationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_STEPS = 40;

    @Resource
    private AgentTeamRepository agentTeamRepository;
    @Resource
    private AgentTeamRunRepository agentTeamRunRepository;
    @Resource
    private TeamNumGateway teamNumGateway;
    @Resource
    private TeamAgentInvoker teamAgentInvoker;

    private Executor executor = Executors.newFixedThreadPool(2);

    /** 测试里改成当前线程，避免异步断言。 */
    void useCallerThread() {
        this.executor = Runnable::run;
    }

    /** 当前空间的团队和运行。 */
    public AgentTeamBucketVO bucket(String workspaceNum) {
        requireWorkspace(workspaceNum);
        AgentTeamBucketVO bucket = new AgentTeamBucketVO();
        bucket.setTeams(agentTeamRepository.list(workspaceNum).stream().map(TeamApplicationService::toTeam).toList());
        bucket.setRuns(agentTeamRunRepository.list(workspaceNum).stream().map(TeamApplicationService::toRun).toList());
        return bucket;
    }

    /** 团队详情。 */
    public AgentTeamVO detail(String workspaceNum, String num) {
        return toTeam(requireTeam(workspaceNum, num));
    }

    /** 运行详情。 */
    public AgentTeamRunVO runDetail(String workspaceNum, String num) {
        return toRun(requireRun(workspaceNum, num));
    }

    /** 保存草稿。num 为空则新建。 */
    public AgentTeamVO save(String workspaceNum, TeamSaveParam param, String operatorId) {
        requireWorkspace(workspaceNum);
        parse(param.getDefinitionJson());
        if (param.getNum() == null || param.getNum().isBlank()) {
            String num = teamNumGateway.nextTeamNum();
            TeamRecord record = new TeamRecord(num, workspaceNum, param.getName().trim(), blank(param.getDescription()),
                    param.getMode(), "DRAFT", param.getDefinitionJson(), operatorId, LocalDateTime.now());
            agentTeamRepository.insert(record, operatorId);
            return toTeam(requireTeam(workspaceNum, num));
        }
        TeamRecord existing = requireTeam(workspaceNum, param.getNum());
        TeamRecord next = new TeamRecord(existing.num(), workspaceNum, param.getName().trim(), blank(param.getDescription()),
                param.getMode(), "DRAFT", param.getDefinitionJson(), operatorId, LocalDateTime.now());
        agentTeamRepository.update(next, operatorId);
        return toTeam(requireTeam(workspaceNum, existing.num()));
    }

    /** 校验通过后标记已发布。 */
    public AgentTeamVO publish(String workspaceNum, String num, String operatorId) {
        TeamRecord existing = requireTeam(workspaceNum, num);
        TeamDefinition definition = parse(existing.definitionJson());
        definition.setMode(existing.mode());
        List<String> errors = TeamGraphEngine.validate(existing.name(), definition);
        if (!errors.isEmpty()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), String.join("；", errors));
        }
        TeamRecord next = new TeamRecord(existing.num(), workspaceNum, existing.name(), existing.description(),
                existing.mode(), "PUBLISHED", existing.definitionJson(), operatorId, LocalDateTime.now());
        agentTeamRepository.update(next, operatorId);
        return toTeam(requireTeam(workspaceNum, num));
    }

    /** 复制一份草稿。 */
    public AgentTeamVO duplicate(String workspaceNum, String num, String operatorId) {
        TeamRecord existing = requireTeam(workspaceNum, num);
        String copyNum = teamNumGateway.nextTeamNum();
        TeamRecord copy = new TeamRecord(copyNum, workspaceNum, existing.name() + " 副本", existing.description(),
                existing.mode(), "DRAFT", existing.definitionJson(), operatorId, LocalDateTime.now());
        agentTeamRepository.insert(copy, operatorId);
        return toTeam(requireTeam(workspaceNum, copyNum));
    }

    /** 软删除。 */
    public void delete(String workspaceNum, String num, String operatorId) {
        requireTeam(workspaceNum, num);
        agentTeamRepository.softDelete(workspaceNum, num, operatorId);
    }

    /**
     * 启动运行：分配 OSS 目录并在后台往下走。接口立刻返回。
     */
    public AgentTeamRunVO start(String workspaceNum, TeamRunParam param, String operatorId) {
        TeamRecord team = requireTeam(workspaceNum, param.getTeamNum());
        TeamDefinition definition = parse(team.definitionJson());
        definition.setMode(team.mode());
        String runNum = teamNumGateway.nextRunNum();
        String subPath = RunWorkspaceMount.subPath(workspaceNum, runNum);
        String runtimeJson;
        String status;
        if ("leader".equals(team.mode())) {
            LeaderRuntime leader = TeamGraphEngine.startLeader(definition);
            runtimeJson = JSON.toJSONString(leader);
            status = "succeeded".equals(leader.getPhase()) ? "succeeded" : "running";
        } else {
            GraphRuntime graph = TeamGraphEngine.startGraph(definition);
            runtimeJson = JSON.toJSONString(graph);
            status = graph.getStatus() == null ? "running" : graph.getStatus();
        }
        RunRecord record = new RunRecord(runNum, team.num(), workspaceNum, team.name(), team.mode(), param.getInput().trim(),
                status, subPath, team.definitionJson(), runtimeJson, operatorId, LocalDateTime.now());
        agentTeamRunRepository.insert(record, operatorId);
        if ("running".equals(status)) {
            executor.execute(() -> drive(workspaceNum, runNum, operatorId));
        }
        return toRun(requireRun(workspaceNum, runNum));
    }

    /** 闸门通过，然后继续调度。 */
    public AgentTeamRunVO approve(String workspaceNum, TeamGateParam param, String operatorId) {
        return gate(workspaceNum, param, operatorId, true);
    }

    /** 闸门打回，然后继续调度。 */
    public AgentTeamRunVO reject(String workspaceNum, TeamGateParam param, String operatorId) {
        return gate(workspaceNum, param, operatorId, false);
    }

    /** 取消在途运行。 */
    public AgentTeamRunVO cancel(String workspaceNum, String runNum, String operatorId) {
        RunRecord run = requireRun(workspaceNum, runNum);
        if (!"succeeded".equals(run.status()) && !"failed".equals(run.status()) && !"cancelled".equals(run.status())) {
            String runtime = run.runtimeJson();
            if ("leader".equals(run.mode())) {
                LeaderRuntime leader = JSON.parseObject(runtime, LeaderRuntime.class);
                leader.setPhase("cancelled");
                runtime = JSON.toJSONString(leader);
            }
            agentTeamRunRepository.updateRuntime(runNum, "cancelled", runtime, operatorId);
        }
        return toRun(requireRun(workspaceNum, runNum));
    }

    private AgentTeamRunVO gate(String workspaceNum, TeamGateParam param, String operatorId, boolean approve) {
        RunRecord run = requireRun(workspaceNum, param.getRunNum());
        if (!"waiting".equals(run.status())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "这次运行不在等待审批");
        }
        TeamDefinition definition = JSON.parseObject(run.snapshotJson(), TeamDefinition.class);
        GraphRuntime runtime = JSON.parseObject(run.runtimeJson(), GraphRuntime.class);
        GraphRuntime next = approve
                ? TeamGraphEngine.approveGate(definition, runtime, param.getNodeId(), param.getComment())
                : TeamGraphEngine.rejectGate(definition, runtime, param.getNodeId(), param.getComment());
        agentTeamRunRepository.updateRuntime(run.num(), next.getStatus(), JSON.toJSONString(next), operatorId);
        if ("running".equals(next.getStatus())) {
            executor.execute(() -> drive(workspaceNum, run.num(), operatorId));
        }
        return toRun(requireRun(workspaceNum, run.num()));
    }

    private void drive(String workspaceNum, String runNum, String operatorId) {
        WorkspaceContextHolder.set(WorkspaceContext.builder().workspaceNum(workspaceNum).member(true).build());
        UserContextHolder.set(UserContext.builder().userId(operatorId).build());
        try {
            for (int step = 0; step < MAX_STEPS; step++) {
                RunRecord run = requireRun(workspaceNum, runNum);
                if (!"running".equals(run.status())) {
                    return;
                }
                if ("leader".equals(run.mode())) {
                    if (!driveLeader(run, operatorId)) {
                        return;
                    }
                } else if (!driveGraph(run, operatorId)) {
                    return;
                }
            }
        } finally {
            WorkspaceContextHolder.clear();
            UserContextHolder.clear();
        }
    }

    private boolean driveGraph(RunRecord run, String operatorId) {
        TeamDefinition definition = JSON.parseObject(run.snapshotJson(), TeamDefinition.class);
        GraphRuntime runtime = JSON.parseObject(run.runtimeJson(), GraphRuntime.class);
        FlowNode active = null;
        if (definition.getNodes() != null && runtime.getNodes() != null) {
            for (FlowNode node : definition.getNodes()) {
                NodeRuntime state = runtime.getNodes().get(node.getId());
                if (state != null && "running".equals(state.getStatus())) {
                    active = node;
                    break;
                }
            }
        }
        if (active == null) {
            return false;
        }
        if (active.getAgentNum() == null || active.getAgentNum().isBlank()) {
            runtime = TeamGraphEngine.failNode(definition, runtime, active.getId(), "未绑定 Agent");
            agentTeamRunRepository.updateRuntime(run.num(), runtime.getStatus(), JSON.toJSONString(runtime), operatorId);
            return "running".equals(runtime.getStatus());
        }
        try {
            TeamAgentInvoker.Result result = teamAgentInvoker.invoke(
                    active.getAgentNum(), active.getVersion(), promptFor(active, run, runtime),
                    run.num(), run.ossSubPath(), operatorId);
            runtime = TeamGraphEngine.succeedNode(definition, runtime, active.getId(), result.output(), result.sessionNum());
        } catch (RuntimeException ex) {
            runtime = TeamGraphEngine.failNode(definition, runtime, active.getId(), ex.getMessage());
        }
        agentTeamRunRepository.updateRuntime(run.num(), runtime.getStatus(), JSON.toJSONString(runtime), operatorId);
        return "running".equals(runtime.getStatus());
    }

    private boolean driveLeader(RunRecord run, String operatorId) {
        TeamDefinition definition = JSON.parseObject(run.snapshotJson(), TeamDefinition.class);
        LeaderRuntime runtime = JSON.parseObject(run.runtimeJson(), LeaderRuntime.class);
        if ("assigning".equals(runtime.getPhase())) {
            if (definition.getLeader() == null || definition.getLeader().getAgentNum() == null
                    || definition.getLeader().getAgentNum().isBlank()) {
                runtime.setPhase("cancelled");
                runtime.setSummary("未绑定 Leader");
                agentTeamRunRepository.updateRuntime(run.num(), "failed", JSON.toJSONString(runtime), operatorId);
                return false;
            }
            try {
                String prompt = shareNote(definition.getLeader().getInstruction(), run);
                TeamAgentInvoker.Result result = teamAgentInvoker.invoke(
                        definition.getLeader().getAgentNum(), null, prompt, run.num(), run.ossSubPath(), operatorId);
                runtime.setLeaderSessionNum(result.sessionNum());
                runtime.setSummary(result.output());
                TeamGraphEngine.assignLeader(definition, runtime, run.inputText());
                agentTeamRunRepository.updateRuntime(run.num(), "running", JSON.toJSONString(runtime), operatorId);
                return true;
            } catch (RuntimeException ex) {
                runtime.setPhase("cancelled");
                runtime.setSummary(ex.getMessage());
                agentTeamRunRepository.updateRuntime(run.num(), "failed", JSON.toJSONString(runtime), operatorId);
                return false;
            }
        }
        if ("working".equals(runtime.getPhase()) && definition.getWorkers() != null) {
            for (TeamWorker worker : definition.getWorkers()) {
                WorkerRuntime state = runtime.getWorkers().get(worker.getId());
                if (state == null || !"working".equals(state.getStatus()) || (state.getOutput() != null && !state.getOutput().isBlank())) {
                    continue;
                }
                try {
                    TeamAgentInvoker.Result result = teamAgentInvoker.invoke(
                            worker.getAgentNum(), null, shareNote(state.getTask(), run),
                            run.num(), run.ossSubPath(), operatorId);
                    state.setSessionNum(result.sessionNum());
                    state.setOutput(result.output());
                } catch (RuntimeException ex) {
                    runtime.setPhase("cancelled");
                    runtime.setSummary(ex.getMessage());
                    agentTeamRunRepository.updateRuntime(run.num(), "failed", JSON.toJSONString(runtime), operatorId);
                    return false;
                }
            }
            TeamGraphEngine.finishWorkers(definition, runtime);
            agentTeamRunRepository.updateRuntime(run.num(), "succeeded", JSON.toJSONString(runtime), operatorId);
        }
        return false;
    }

    private static String promptFor(FlowNode node, RunRecord run, GraphRuntime runtime) {
        String template = node.getPromptTemplate() == null || node.getPromptTemplate().isBlank()
                ? "{{run.input}}" : node.getPromptTemplate();
        String text = template.replace("{{run.input}}", run.inputText() == null ? "" : run.inputText());
        if (runtime.getNodes() != null) {
            for (var entry : runtime.getNodes().entrySet()) {
                String output = entry.getValue().getOutput() == null ? "" : entry.getValue().getOutput();
                text = text.replace("{{nodes." + entry.getKey() + ".output}}", output);
            }
        }
        return shareNote(text, run);
    }

    private static String shareNote(String body, RunRecord run) {
        String text = body == null ? "" : body;
        return text + "\n\n共享工作目录已挂载到容器工作目录根，OSS 前缀是 " + run.ossSubPath()
                + "。请把需要交给下一个 Agent 的文件写在这个目录里。";
    }

    private TeamRecord requireTeam(String workspaceNum, String num) {
        requireWorkspace(workspaceNum);
        return agentTeamRepository.find(workspaceNum, num)
                .orElseThrow(() -> new BusinessException(BizCode.INVALID_PARAM.getCode(), "团队不存在"));
    }

    private RunRecord requireRun(String workspaceNum, String num) {
        requireWorkspace(workspaceNum);
        return agentTeamRunRepository.find(workspaceNum, num)
                .orElseThrow(() -> new BusinessException(BizCode.INVALID_PARAM.getCode(), "运行不存在"));
    }

    private static void requireWorkspace(String workspaceNum) {
        if (workspaceNum == null || workspaceNum.isBlank()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "缺少工作空间");
        }
    }

    private static TeamDefinition parse(String json) {
        try {
            TeamDefinition definition = JSON.parseObject(json, TeamDefinition.class);
            if (definition == null) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "定义不能为空");
            }
            return definition;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "定义不是合法 JSON");
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static AgentTeamVO toTeam(TeamRecord record) {
        AgentTeamVO vo = new AgentTeamVO();
        vo.setNum(record.num());
        vo.setName(record.name());
        vo.setDescription(record.description());
        vo.setMode(record.mode());
        vo.setStatus(record.status());
        vo.setDefinitionJson(record.definitionJson());
        vo.setUpdatedAt(record.updateTime() == null ? null : TIME.format(record.updateTime()));
        return vo;
    }

    private static AgentTeamRunVO toRun(RunRecord record) {
        AgentTeamRunVO vo = new AgentTeamRunVO();
        vo.setNum(record.num());
        vo.setTeamNum(record.teamNum());
        vo.setTeamName(record.teamName());
        vo.setMode(record.mode());
        vo.setInput(record.inputText());
        vo.setStatus(record.status());
        vo.setOssSubPath(record.ossSubPath());
        vo.setSnapshotJson(record.snapshotJson());
        vo.setRuntimeJson(record.runtimeJson());
        vo.setCreatedAt(record.createTime() == null ? null : TIME.format(record.createTime()));
        return vo;
    }
}
