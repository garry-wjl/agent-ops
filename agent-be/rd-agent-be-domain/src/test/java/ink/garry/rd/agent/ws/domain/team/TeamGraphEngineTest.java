package ink.garry.rd.agent.ws.domain.team;

import ink.garry.rd.agent.ws.domain.team.model.TeamModels.FlowEdge;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.FlowNode;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.GraphRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.LeaderRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamDefinition;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamLeader;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamWorker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamGraphEngineTest {

    @Test
    void validate_rejectsCycleAndNonAncestorTemplate() {
        TeamDefinition definition = graph();
        definition.getEdges().add(edge("e-cycle", "deliver", "plan"));
        definition.getNodes().get(0).setPromptTemplate("{{nodes.missing.output}}");
        List<String> errors = TeamGraphEngine.validate("交付", definition);
        assertTrue(errors.stream().anyMatch(item -> item.contains("成环")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("不存在")));
        TeamDefinition plain = graph();
        plain.getNodes().get(0).setPromptTemplate("{{nodes.deliver.output}}");
        assertTrue(TeamGraphEngine.validate("交付", plain).stream().anyMatch(item -> item.contains("上游")));
    }

    @Test
    void graph_walksToGateThenRejectResetsDownstream() {
        TeamDefinition definition = graph();
        GraphRuntime runtime = TeamGraphEngine.startGraph(definition);
        assertEquals("running", runtime.getNodes().get("plan").getStatus());
        runtime = TeamGraphEngine.succeedNode(definition, runtime, "plan", "方案", "SES1");
        assertEquals("running", runtime.getNodes().get("build").getStatus());
        runtime = TeamGraphEngine.succeedNode(definition, runtime, "build", "实现", "SES2");
        assertEquals("waiting", runtime.getStatus());
        assertEquals("waiting_approval", runtime.getNodes().get("accept").getStatus());
        runtime = TeamGraphEngine.rejectGate(definition, runtime, "accept", "请返工");
        assertEquals("running", runtime.getNodes().get("build").getStatus());
        assertEquals("pending", runtime.getNodes().get("accept").getStatus());
        assertEquals(1, runtime.getNodes().get("build").getReworkCount());
    }

    @Test
    void failNode_retriesThenFails() {
        TeamDefinition definition = graph();
        definition.getNodes().get(1).setMaxAttempts(2);
        definition.getNodes().get(1).setRejectTo("");
        GraphRuntime runtime = TeamGraphEngine.startGraph(definition);
        runtime = TeamGraphEngine.succeedNode(definition, runtime, "plan", "方案", null);
        runtime = TeamGraphEngine.failNode(definition, runtime, "build", "一次失败");
        assertEquals("running", runtime.getStatus());
        assertEquals(1, runtime.getNodes().get("build").getAttempt());
        runtime = TeamGraphEngine.failNode(definition, runtime, "build", "再次失败");
        assertEquals("failed", runtime.getStatus());
    }

    @Test
    void leader_assignThenFinish() {
        TeamDefinition definition = new TeamDefinition();
        definition.setMode("leader");
        TeamLeader leader = new TeamLeader();
        leader.setAgentNum("AGT-L");
        leader.setInstruction("拆分");
        definition.setLeader(leader);
        TeamWorker worker = new TeamWorker();
        worker.setId("w1");
        worker.setAgentNum("AGT-W");
        worker.setAgentName("资料");
        worker.setDuty("收集资料");
        definition.setWorkers(List.of(worker));
        assertTrue(TeamGraphEngine.validate("小组", definition).isEmpty());
        LeaderRuntime runtime = TeamGraphEngine.startLeader(definition);
        assertEquals("assigning", runtime.getPhase());
        runtime = TeamGraphEngine.assignLeader(definition, runtime, "调研");
        assertEquals("working", runtime.getWorkers().get("w1").getStatus());
        runtime = TeamGraphEngine.finishWorkers(definition, runtime);
        assertEquals("succeeded", runtime.getPhase());
        assertTrue(runtime.getSummary().contains("收集资料"));
    }

    private static TeamDefinition graph() {
        TeamDefinition definition = new TeamDefinition();
        definition.setMode("orchestration");
        definition.setNodes(new ArrayList<>(List.of(node("plan", "agent"), node("build", "agent"), node("deliver", "agent"), gate())));
        definition.setEdges(new ArrayList<>(List.of(edge("e1", "plan", "build"), edge("e2", "build", "accept"), edge("e3", "accept", "deliver"))));
        return definition;
    }

    private static FlowNode node(String id, String kind) {
        FlowNode node = new FlowNode();
        node.setId(id);
        node.setKind(kind);
        node.setName(id);
        node.setAgentNum("AGT-" + id);
        node.setPromptTemplate("{{run.input}}");
        node.setMaxAttempts(1);
        return node;
    }

    private static FlowNode gate() {
        FlowNode node = node("accept", "gate");
        node.setGatePrompt("确认");
        node.setRejectTo("build");
        node.setMaxReworks(2);
        node.setAgentNum("");
        return node;
    }

    private static FlowEdge edge(String id, String source, String target) {
        FlowEdge edge = new FlowEdge();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }
}
