package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A2A Agent 来源信息 VO（仅 A2A 模式详情页填充）。
 * <p>
 * 与 domain 层 {@code A2aSourceInfo} 字段一一对应，前端用于渲染「A2A 来源信息卡片」+
 * 顶部只读 Banner（来源服务名 / endpoint / 最近同步时间 / 同步事件来源）。
 */
@Data
public class A2aSourceVO {
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
    /** Agent Card 中的远端版本号 */
    private String remoteVersion;
    /** Agent Card 中的远端 skills 列表（只读） */
    private List<RemoteSkill> remoteSkills;
    /** Agent Card 中的远端 MCP 工具列表（v2.3 新增，只读） */
    private List<RemoteMcp> remoteMcps;
    /** 完整 Agent Card JSON 原文（前端 Manifest Tab 展示） */
    private String agentCardJson;
    /** 最近同步时间 */
    private LocalDateTime lastSyncedAt;
    /** 最近同步事件来源 INSTANCE_ADDED / INSTANCE_CHANGED / INSTANCE_REMOVED / POLLING_RECONCILE / MANUAL_RESYNC */
    private String lastSyncEventType;

    /** A2A 远端能力 skill 项（只读，平台无法挂载） */
    @Data
    public static class RemoteSkill {
        /** Skill 名称 */
        private String name;
        /** Skill 描述 */
        private String description;
    }

    /** A2A 远端 MCP 工具项（v2.3 新增，只读，平台无法挂载） */
    @Data
    public static class RemoteMcp {
        /** MCP 名称 */
        private String name;
        /** MCP 描述 */
        private String description;
        /** MCP server URL（可选） */
        private String serverUrl;
    }
}
