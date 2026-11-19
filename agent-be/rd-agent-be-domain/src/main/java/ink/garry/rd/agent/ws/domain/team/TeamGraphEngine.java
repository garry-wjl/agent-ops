package ink.garry.rd.agent.ws.domain.team;

import ink.garry.rd.agent.ws.domain.team.model.TeamModels.FlowEdge;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.FlowNode;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.GraphRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.LeaderRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.NodeRuntime;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamDefinition;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.TeamWorker;
import ink.garry.rd.agent.ws.domain.team.model.TeamModels.WorkerRuntime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编排运行的纯状态机：依赖就绪后才能开始，失败可重试，打回重跑目标及其下游。
 * <p>
 * 不调用模型，也不碰 OSS。应用层按返回的状态决定下一轮调用哪个 Agent。
 */
public final class TeamGraphEngine {

    private static final Pattern NODE_REF = Pattern.compile("\\{\\{\\s*nodes\\.([A-Za-z0-9_-]+)\\.output\\s*}}");

    private TeamGraphEngine() {
    }

    /**
     * 发布前校验。打回边不计入成环。
     *
     * @param name       团队名称
     * @param definition 定义
     * @return 错误列表，空表示可以通过
     */
    public static List<String> validate(String name, TeamDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add("请填写名称");
        }
        if (definition == null) {
            errors.add("缺少定义");
            return errors;
        }
        if ("leader".equals(definition.getMode())) {
            validateLeader(definition, errors);
            return errors;
        }
        List<FlowNode> nodes = definition.getNodes() == null ? List.of() : definition.getNodes();
        if (nodes.isEmpty()) {
            errors.add("至少添加一个节点");
            return errors;
        }
        if (hasSuccessCycle(definition)) {
            errors.add("成功依赖不能成环");
        }
        Map<String, FlowNode> byId = index(nodes);
        for (FlowNode node : nodes) {
            if ("gate".equals(node.getKind())) {
                if (node.getGatePrompt() == null || node.getGatePrompt().isBlank()) {
                    errors.add("请填写闸门「" + label(node) + "」要确认的内容");
                }
            } else if (node.getAgentNum() == null || node.getAgentNum().isBlank()) {
                errors.add("请为「" + label(node) + "」选择 Agent");
            }
            if (node.getRejectTo() != null && !node.getRejectTo().isBlank()) {
                if (node.getRejectTo().equals(node.getId()) || !byId.containsKey(node.getRejectTo())) {
                    errors.add("「" + label(node) + "」的打回目标不存在");
                }
            }
            Matcher matcher = NODE_REF.matcher(node.getPromptTemplate() == null ? "" : node.getPromptTemplate());
            Set<String> ancestors = upstream(definition, node.getId());
            while (matcher.find()) {
                String ref = matcher.group(1);
                if (!byId.containsKey(ref)) {
                    errors.add("「" + label(node) + "」引用了不存在的节点 " + ref);
                } else if (!ancestors.contains(ref)) {
                    errors.add("「" + label(node) + "」只能引用上游节点 " + ref + " 的输出");
                }
            }
        }
        return errors;
    }

    /** 没有入边的节点先进入执行或等待审批。 */
    public static GraphRuntime startGraph(TeamDefinition definition) {
        GraphRuntime runtime = new GraphRuntime();
        if (definition.getNodes() != null) {
            for (FlowNode node : definition.getNodes()) {
                runtime.getNodes().put(node.getId(), new NodeRuntime());
            }
        }
        return finish(definition, runtime);
    }

    /**
     * 把正在执行的节点标成成功，并放开下游。
     */
    public static GraphRuntime succeedNode(TeamDefinition definition, GraphRuntime runtime, String nodeId, String output, String sessionNum) {
        NodeRuntime current = runtime.getNodes().get(nodeId);
        if (current == null) {
            return runtime;
        }
        if (!"running".equals(current.getStatus()) && !"waiting_approval".equals(current.getStatus())) {
            return runtime;
        }
        current.setStatus("succeeded");
        current.setOutput(output == null ? "" : output);
        current.setError("");
        current.setSessionNum(sessionNum);
        return finish(definition, runtime);
    }

    /**
     * 失败先重试；重试耗尽后按打回目标回退，否则整次运行失败。
     */
    public static GraphRuntime failNode(TeamDefinition definition, GraphRuntime runtime, String nodeId, String error) {
        FlowNode node = find(definition, nodeId);
        NodeRuntime current = runtime.getNodes().get(nodeId);
        if (node == null || current == null || !"running".equals(current.getStatus())) {
            return runtime;
        }
        int attempt = current.getAttempt() + 1;
        current.setAttempt(attempt);
        current.setError(error == null ? "" : error);
        int maxAttempts = node.getMaxAttempts() <= 0 ? 1 : node.getMaxAttempts();
        if (attempt < maxAttempts) {
            current.setStatus("running");
            runtime.setStatus("running");
            return runtime;
        }
        if (node.getRejectTo() != null && !node.getRejectTo().isBlank()
                && current.getReworkCount() < node.getMaxReworks()) {
            return rework(definition, runtime, nodeId, node.getRejectTo(), error);
        }
        current.setStatus("failed");
        runtime.setStatus("failed");
        return runtime;
    }

    /** 闸门通过。 */
    public static GraphRuntime approveGate(TeamDefinition definition, GraphRuntime runtime, String nodeId, String comment) {
        return succeedNode(definition, runtime, nodeId, comment == null || comment.isBlank() ? "已通过" : comment, null);
    }

    /** 闸门打回。 */
    public static GraphRuntime rejectGate(TeamDefinition definition, GraphRuntime runtime, String nodeId, String comment) {
        FlowNode node = find(definition, nodeId);
        NodeRuntime current = runtime.getNodes().get(nodeId);
        if (node == null || current == null || !"waiting_approval".equals(current.getStatus())
                || node.getRejectTo() == null || node.getRejectTo().isBlank()) {
            return runtime;
        }
        return rework(definition, runtime, nodeId, node.getRejectTo(), comment);
    }

    /** Leader 模式初始为待分派。 */
    public static LeaderRuntime startLeader(TeamDefinition definition) {
        LeaderRuntime runtime = new LeaderRuntime();
        if (definition.getWorkers() != null) {
            for (TeamWorker worker : definition.getWorkers()) {
                runtime.getWorkers().put(worker.getId(), new WorkerRuntime());
            }
        }
        runtime.setPhase(definition.getWorkers() == null || definition.getWorkers().isEmpty() ? "succeeded" : "assigning");
        return runtime;
    }

    /** Leader 已分派，Worker 进入执行。 */
    public static LeaderRuntime assignLeader(TeamDefinition definition, LeaderRuntime runtime, String input) {
        if (definition.getWorkers() != null) {
            for (TeamWorker worker : definition.getWorkers()) {
                WorkerRuntime state = runtime.getWorkers().computeIfAbsent(worker.getId(), id -> new WorkerRuntime());
                String duty = worker.getDuty() == null || worker.getDuty().isBlank() ? "处理任务" : worker.getDuty();
                state.setStatus("working");
                state.setTask(duty + "：" + (input == null ? "" : input));
                state.setOutput("");
            }
        }
        runtime.setPhase(definition.getWorkers() == null || definition.getWorkers().isEmpty() ? "succeeded" : "working");
        return runtime;
    }

    /** 全部 Worker 完成后汇总。 */
    public static LeaderRuntime finishWorkers(TeamDefinition definition, LeaderRuntime runtime) {
        List<String> lines = new ArrayList<>();
        if (definition.getWorkers() != null) {
            for (TeamWorker worker : definition.getWorkers()) {
                WorkerRuntime state = runtime.getWorkers().computeIfAbsent(worker.getId(), id -> new WorkerRuntime());
                String name = worker.getAgentName() == null || worker.getAgentName().isBlank() ? "Worker" : worker.getAgentName();
                String duty = worker.getDuty() == null || worker.getDuty().isBlank() ? "任务" : worker.getDuty();
                String output = state.getOutput() == null || state.getOutput().isBlank()
                        ? name + " 已完成「" + duty + "」" : state.getOutput();
                state.setStatus("succeeded");
                state.setOutput(output);
                lines.add(output);
            }
        }
        runtime.setPhase("succeeded");
        runtime.setSummary(lines.isEmpty() ? "没有 Worker，Leader 直接结束。" : String.join("\n", lines));
        return runtime;
    }

    private static void validateLeader(TeamDefinition definition, List<String> errors) {
        if (definition.getLeader() == null || definition.getLeader().getAgentNum() == null
                || definition.getLeader().getAgentNum().isBlank()) {
            errors.add("请选择 Leader");
        }
        if (definition.getLeader() == null || definition.getLeader().getInstruction() == null
                || definition.getLeader().getInstruction().isBlank()) {
            errors.add("请填写 Leader 的分工说明");
        }
        List<TeamWorker> workers = definition.getWorkers() == null ? List.of() : definition.getWorkers();
        if (workers.isEmpty()) {
            errors.add("至少添加一个 Worker");
        }
        Set<String> seen = new HashSet<>();
        if (definition.getLeader() != null && definition.getLeader().getAgentNum() != null) {
            seen.add(definition.getLeader().getAgentNum());
        }
        for (TeamWorker worker : workers) {
            if (worker.getAgentNum() == null || worker.getAgentNum().isBlank()) {
                String duty = worker.getDuty() == null || worker.getDuty().isBlank() ? "未命名" : worker.getDuty().trim();
                errors.add("请为 Worker「" + duty + "」选择 Agent");
                continue;
            }
            if (worker.getDuty() == null || worker.getDuty().isBlank()) {
                errors.add("请填写 Worker「" + worker.getAgentNum() + "」的职责");
            }
            if (!seen.add(worker.getAgentNum())) {
                errors.add("Leader 和 Worker 不能重复绑定同一个 Agent");
            }
        }
    }

    private static GraphRuntime rework(TeamDefinition definition, GraphRuntime runtime, String fromId, String targetId, String comment) {
        FlowNode source = find(definition, fromId);
        NodeRuntime current = runtime.getNodes().get(fromId);
        NodeRuntime target = runtime.getNodes().get(targetId);
        if (source == null || current == null || target == null) {
            return runtime;
        }
        int nextCount = target.getReworkCount() + 1;
        if (nextCount > source.getMaxReworks()) {
            current.setStatus("failed");
            current.setError(comment == null || comment.isBlank() ? "打回次数已用完" : comment);
            runtime.setStatus("failed");
            return runtime;
        }
        Set<String> resetIds = new HashSet<>();
        resetIds.add(targetId);
        resetIds.addAll(descendants(definition, targetId));
        resetIds.add(fromId);
        for (String id : resetIds) {
            NodeRuntime prev = runtime.getNodes().computeIfAbsent(id, key -> new NodeRuntime());
            int kept = id.equals(targetId) ? nextCount : prev.getReworkCount();
            prev.setStatus("pending");
            prev.setError("");
            prev.setAttempt(0);
            prev.setReworkCount(kept);
        }
        return finish(definition, runtime);
    }

    private static GraphRuntime finish(TeamDefinition definition, GraphRuntime runtime) {
        promote(definition, runtime);
        runtime.setStatus(summarize(definition, runtime));
        return runtime;
    }

    private static void promote(TeamDefinition definition, GraphRuntime runtime) {
        if (definition.getNodes() == null) {
            return;
        }
        for (FlowNode node : definition.getNodes()) {
            NodeRuntime current = runtime.getNodes().get(node.getId());
            if (current == null || !"pending".equals(current.getStatus())) {
                continue;
            }
            boolean ready = incoming(definition, node.getId()).stream()
                    .allMatch(id -> {
                        NodeRuntime upstream = runtime.getNodes().get(id);
                        return upstream != null && "succeeded".equals(upstream.getStatus());
                    });
            if (!ready) {
                continue;
            }
            current.setStatus("gate".equals(node.getKind()) ? "waiting_approval" : "running");
            current.setError("");
        }
    }

    private static String summarize(TeamDefinition definition, GraphRuntime runtime) {
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            return "succeeded";
        }
        boolean failed = false;
        boolean waiting = false;
        boolean allSucceeded = true;
        for (FlowNode node : definition.getNodes()) {
            NodeRuntime current = runtime.getNodes().get(node.getId());
            String status = current == null ? "pending" : current.getStatus();
            if ("failed".equals(status)) {
                failed = true;
            }
            if ("waiting_approval".equals(status)) {
                waiting = true;
            }
            if (!"succeeded".equals(status)) {
                allSucceeded = false;
            }
        }
        if (failed) {
            return "failed";
        }
        if (waiting) {
            return "waiting";
        }
        if (allSucceeded) {
            return "succeeded";
        }
        return "running";
    }

    private static List<String> incoming(TeamDefinition definition, String id) {
        List<String> sources = new ArrayList<>();
        if (definition.getEdges() == null) {
            return sources;
        }
        for (FlowEdge edge : definition.getEdges()) {
            if (id.equals(edge.getTarget())) {
                sources.add(edge.getSource());
            }
        }
        return sources;
    }

    private static Set<String> descendants(TeamDefinition definition, String root) {
        Map<String, List<String>> adj = new HashMap<>();
        if (definition.getEdges() != null) {
            for (FlowEdge edge : definition.getEdges()) {
                adj.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
            }
        }
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>(adj.getOrDefault(root, List.of()));
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (id == null || !seen.add(id)) {
                continue;
            }
            stack.addAll(adj.getOrDefault(id, List.of()));
        }
        return seen;
    }

    private static Set<String> upstream(TeamDefinition definition, String id) {
        Map<String, List<String>> reverse = new HashMap<>();
        if (definition.getEdges() != null) {
            for (FlowEdge edge : definition.getEdges()) {
                reverse.computeIfAbsent(edge.getTarget(), key -> new ArrayList<>()).add(edge.getSource());
            }
        }
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>(reverse.getOrDefault(id, List.of()));
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current == null || !seen.add(current)) {
                continue;
            }
            stack.addAll(reverse.getOrDefault(current, List.of()));
        }
        return seen;
    }

    private static boolean hasSuccessCycle(TeamDefinition definition) {
        Map<String, List<String>> adj = new HashMap<>();
        if (definition.getEdges() != null) {
            for (FlowEdge edge : definition.getEdges()) {
                adj.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        if (definition.getNodes() == null) {
            return false;
        }
        for (FlowNode node : definition.getNodes()) {
            if (visit(node.getId(), adj, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String id, Map<String, List<String>> adj, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        for (String next : adj.getOrDefault(id, List.of())) {
            if (visit(next, adj, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }

    private static Map<String, FlowNode> index(List<FlowNode> nodes) {
        Map<String, FlowNode> byId = new HashMap<>();
        for (FlowNode node : nodes) {
            byId.put(node.getId(), node);
        }
        return byId;
    }

    private static FlowNode find(TeamDefinition definition, String id) {
        if (definition.getNodes() == null) {
            return null;
        }
        for (FlowNode node : definition.getNodes()) {
            if (id.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    private static String label(FlowNode node) {
        return node.getName() == null || node.getName().isBlank() ? node.getId() : node.getName();
    }
}
