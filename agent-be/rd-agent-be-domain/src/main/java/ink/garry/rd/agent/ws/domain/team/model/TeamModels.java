package ink.garry.rd.agent.ws.domain.team.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排定义与一次运行的内存模型。字段名与前端画布 JSON 一致。
 */
public final class TeamModels {

    private TeamModels() {
    }

    /** 团队定义：编排图，或 Leader 与 Worker。 */
    @Data
    public static class TeamDefinition {
        private String mode;
        private List<FlowNode> nodes = new ArrayList<>();
        private List<FlowEdge> edges = new ArrayList<>();
        private TeamLeader leader = new TeamLeader();
        private List<TeamWorker> workers = new ArrayList<>();
    }

    /** 画布节点。 */
    @Data
    public static class FlowNode {
        private String id;
        private String kind;
        private String name;
        /** 画布坐标，保存定义时原样带回。 */
        private double x;
        private double y;
        private String agentNum;
        private String agentName;
        private String version;
        private String promptTemplate;
        private int maxAttempts = 1;
        private String rejectTo;
        private int maxReworks;
        private String gatePrompt;
    }

    /** 成功依赖边。打回不走边，记在节点的 rejectTo 上。 */
    @Data
    public static class FlowEdge {
        private String id;
        private String source;
        private String target;
    }

    /** Leader。 */
    @Data
    public static class TeamLeader {
        private String agentNum;
        private String agentName;
        private String instruction;
    }

    /** Worker。 */
    @Data
    public static class TeamWorker {
        private String id;
        private String agentNum;
        private String agentName;
        private String duty;
    }

    /** 节点在一次运行中的状态。 */
    @Data
    public static class NodeRuntime {
        private String status = "pending";
        private String output = "";
        private String error = "";
        private int attempt;
        private int reworkCount;
        private String sessionNum;
    }

    /** 编排运行时。 */
    @Data
    public static class GraphRuntime {
        private String status = "running";
        private Map<String, NodeRuntime> nodes = new LinkedHashMap<>();
    }

    /** Worker 在 Leader 模式中的状态。 */
    @Data
    public static class WorkerRuntime {
        private String status = "pending";
        private String task = "";
        private String output = "";
        private String sessionNum;
    }

    /** Leader 模式运行时。 */
    @Data
    public static class LeaderRuntime {
        private String phase = "assigning";
        private String summary = "";
        private String leaderSessionNum;
        private Map<String, WorkerRuntime> workers = new LinkedHashMap<>();
    }
}
