package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent 版本对比 VO(基于 RFC 6902 JSON Patch)。
 * <p>
 * 比较同一 Agent 的两个版本的 configSnapshot 差异;以 JSON Patch 操作序列表示。
 * 单个补丁项 {@link JsonPatchOp} 作为静态内部类避免类爆炸。
 */
@Data
public class AgentVersionDiffVO {
    /** Agent 业务编号 */
    private String agentNum;
    /** 对比起点版本号 */
    private String versionA;
    /** 对比目标版本号 */
    private String versionB;
    /** 差异补丁序列 */
    private List<JsonPatchOp> patches;

    /** 单个 JSON Patch 操作项(被嵌套,定义为静态内部类) */
    @Data
    public static class JsonPatchOp {
        /** 操作类型: add / remove / replace / move / copy / test */
        private String op;
        /** JSON 路径(RFC 6901) */
        private String path;
        /** 旧值(replace/remove 时填) */
        private Object oldValue;
        /** 新值(add/replace 时填) */
        private Object newValue;
    }
}
