package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A2A 来源信息查询 DTO（对外详情查询用；与 {@code A2aSourceVO} 字段一一对应）。
 * <p>
 * 由 AgentQueryService 从 {@code a2a_source} JSON 列反序列化后装配；adapter 层经
 * assembler 转为 {@code A2aSourceVO} 出参。
 */
@Data
public class A2aSourceViewDTO {

    /** Nacos 分组 */
    private String nacosGroup;
    /** Nacos 服务名（= NacosAgent 名称） */
    private String nacosService;
    /** 实例 IP */
    private String instanceIp;
    /** 实例端口 */
    private Integer instancePort;
    /** A2A endpoint 路径 */
    private String endpointPath;
    /** 远端版本号 */
    private String remoteVersion;
    /** 远端 Agent Card 声明的 Skills */
    private List<RemoteSkill> remoteSkills;
    /** 远端 Agent Card 声明的 MCP 接入 */
    private List<RemoteMcp> remoteMcps;
    /** 远端 Agent Card 原文 JSON */
    private String agentCardJson;
    /** 最近一次同步时间 */
    private LocalDateTime lastSyncedAt;
    /** 最近一次同步事件来源 */
    private String lastSyncEventType;

    /** 远端 Skill 项 */
    @Data
    public static class RemoteSkill {
        /** Skill 名称 */
        private String name;
        /** Skill 描述 */
        private String description;
    }

    /** 远端 MCP 项 */
    @Data
    public static class RemoteMcp {
        /** MCP 名称 */
        private String name;
        /** MCP 描述 */
        private String description;
        /** MCP server URL */
        private String serverUrl;
    }
}
