package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A2A Agent 来源信息值对象（仅 A2A 模式 Agent 持有）。
 * <p>
 * 由 Nacos 订阅 / 同步任务全字段覆盖写入；任何字段都以 Nacos 为唯一权威源，
 * 平台无任何编辑入口。详见技术方案 v2.0 §10.3。
 * <p>
 * 持久化为 {@code agent.a2a_source} JSON 列；幂等键
 * {@code agent.nacos_service_key = nacosGroup + "@@" + nacosService}
 * 单独冗余成 VARCHAR 列以便建唯一索引。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class A2aSourceInfo {

    /** Nacos group，例如 DEFAULT_GROUP */
    private String nacosGroup;
    /** Nacos 服务名，例如 a2a-agents@@flight-bot */
    private String nacosService;
    /** 远端实例 IP */
    private String instanceIp;
    /** 远端实例端口 */
    private Integer instancePort;
    /** A2A invoke endpoint 路径，例如 /a2a/invoke */
    private String endpointPath;
    /** Agent Card 中的远端版本号；缺失时显示 - */
    private String remoteVersion;
    /** Agent Card 中的远端 skills 列表（只读，平台无法挂载） */
    private List<RemoteSkill> remoteSkills;
    /** Agent Card 中的远端 MCP 工具列表（v2.3 新增，只读，平台无法挂载） */
    private List<RemoteMcp> remoteMcps;
    /** 完整 Agent Card JSON 原文，前端 Manifest Tab 展示 */
    private String agentCardJson;
    /** 最近同步时间 */
    private LocalDateTime lastSyncedAt;
    /** 最近同步事件来源 */
    private SyncEventType lastSyncEventType;

    /**
     * 计算 Nacos 幂等键 {@code group@@service}；同一个键唯一对应一个 A2A Agent 行。
     *
     * @return 幂等键字符串；group / service 任一为空时返回 null
     */
    public String resolveServiceKey() {
        if (nacosGroup == null || nacosService == null) {
            return null;
        }
        return nacosGroup + "@@" + nacosService;
    }

    /** A2A 远端能力 skill 项（只读，平台无法挂载） */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RemoteSkill {
        /** Skill 名称 */
        private String name;
        /** Skill 描述 */
        private String description;
    }

    /** A2A 远端 MCP 工具项（v2.3 新增，只读，平台无法挂载） */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RemoteMcp {
        /** MCP 名称 */
        private String name;
        /** MCP 描述 */
        private String description;
        /** MCP server URL（可选，Agent Card 中可能不暴露） */
        private String serverUrl;
    }
}
