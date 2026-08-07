package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 元信息 DTO — application 层 {@code AgentQueryService.findAgentByNum} 出参。
 * <p>
 * 用途:供 application 内部用例(非 Controller 直接出参)以及部分 application → application
 * 跨服务调用使用;Controller 出参请用 {@code client.agent.AgentDetailVO}。
 * <p>
 * <b>放在 client 而非 facade</b>:本 DTO 仅被 application / adapter 层消费,不被 infra 消费;
 * 与 {@code facade.agent.AgentInvokeDTO}(被 {@code ConfigAgentBuilder}@infra 消费)的定位区别在此。
 * 详见 {@code docs/CODING-CONVENTIONS.md §3.1}。
 * <p>
 * <b>字段策略</b>:
 * <ul>
 *   <li>枚举字段以 String 形式承载 {@code name()},避免 client 层反向依赖 domain 枚举;</li>
 *   <li>{@link ConfigSnapshot} / {@link A2aSource} 以静态内部类形式提供强类型出参,
 *       由 application 层 fastjson2 反序列化后填充;调用方无需再次解析 JSON;</li>
 *   <li>{@code modelApiKey} 等敏感信息不在本 DTO 做脱敏,脱敏由 adapter 层 VO 转换时执行;</li>
 *   <li>不携带 {@code deleted} 逻辑删除位,DTO 仅暴露存活行。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDTO {

    /** 自增主键 */
    private Long id;

    /** 业务编号 AGT...;跨聚合引用 ID,永不为空 */
    private String num;

    /** Agent 名称(显示用) */
    private String name;

    /** Agent 描述,可空 */
    private String description;

    /** 创建方式 CONFIG / A2A;取自 {@code CreationMode.name()} */
    private String creationMode;

    /** 行为类型 NORMAL / SUPERVISOR / ROUTER;取自 {@code AgentType.name()} */
    private String agentType;

    /** 负责人 userId */
    private String ownerUserId;

    /** 生命周期状态 DRAFT_ONLY / PUBLISHED / OFFLINE / PENDING_SYNC;取自 {@code AgentStatus.name()} */
    private String status;

    /** 当前在线版本号(仅 CONFIG;A2A 为 null) */
    private String currentVersionNum;

    /**
     * v3.0:当前在线版本 ConfigSnapshot 镜像(仅 CONFIG;A2A 为 null)。
     * <p>
     * 由 application 层从 {@code agent.config_snapshot} JSON 反序列化得到;
     * {@code modelApiKey} 等敏感字段不在本对象做脱敏,由调用方在出参 VO 转换时处理。
     */
    private ConfigSnapshot configSnapshot;

    /** 是否空白沙盒 Agent;1=是 / 0=否 */
    private Integer sandbox;

    /**
     * A2A 来源信息(仅 A2A;CONFIG 为 null)。
     * <p>
     * 由 application 层从 {@code agent.a2a_source} JSON 反序列化得到。
     */
    private A2aSource a2aSource;

    /** A2A 幂等键 = nacosGroup@@nacosService(仅 A2A) */
    private String nacosServiceKey;

    /** 创建人 userId */
    private String createNo;

    /** 更新人 userId */
    private String updateNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * Agent 配置快照(client 层强类型镜像;仅 CONFIG 模式)。
     * <p>
     * 字段与 {@code domain.agent.valueobject.ConfigSnapshot} 一一对应;枚举字段以 String
     * 形式承载({@code AgentType.name()} / {@code ShortTermMemoryStrategy.name()} 等),
     * 避免 client 反向依赖 domain。
     * <p>
     * 反序列化由 application 层使用 fastjson2 完成;调用方拿到的本对象已是强类型,无需再次解析。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfigSnapshot {

        /** Agent 名称(v3.0 起纳入版本快照) */
        private String name;

        /** Agent 描述(v3.0 起纳入版本快照) */
        private String description;

        /** 行为类型 NORMAL / SUPERVISOR / ROUTER;取自 {@code AgentType.name()} */
        private String agentType;

        /** 系统提示词,作用于全部对话 */
        private String systemPrompt;

        /** 用户提示词模板,可含占位符 */
        private String userPrompt;

        /** 关联模型业务编号 num（v4.0：模型管理引用，替代原 model/modelApiKey/modelBaseUrl 手填） */
        private String modelId;

        /** 采样温度 0.0~2.0 */
        private Double temperature;

        /** 是否启用 Plan 模式；本期仅保存和回显 */
        private Boolean enablePlan;

        /** 最大迭代轮次（ReAct 循环次数），默认 10 */
        private Integer maxIters;

        /** 挂载的 Skill 业务编号列表（多选） */
        private List<String> skillNums;

        /** 挂载的工具业务编号列表（v4.0：原 mcpNums 重命名；含 MCP / FunctionCall，多选） */
        private List<String> toolNums;

        /** 挂载 Skill 的版本引用；新写入优先使用 */
        private List<SkillRef> skillRefs;

        /** 挂载工具的版本引用；工具具备版本后按 versionNum 解析 */
        private List<ToolRef> toolRefs;

        /** 关联沙箱引用标识（v4.0 新增：沙箱管理引用，单选可空） */
        private String sandboxRef;

        /** 子 Agent 业务编号列表(仅 SUPERVISOR / ROUTER) */
        private List<String> childAgentNums;

        /** 记忆配置 */
        private MemoryConfig memoryConfig;

        /** 每秒最大调用次数 */
        private Integer qps;

        /** 每日预算(次数 / token,按运营协议口径) */
        private Integer dailyBudget;

        /** Skill 版本引用。 */
        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SkillRef {
            private String skillNum;
            private String versionNum;
        }

        /** 工具版本引用。 */
        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ToolRef {
            private String toolNum;
            private String versionNum;
        }

        /**
         * Agent 记忆配置(v2.5 重构:策略枚举 + 短期窗口 N)。
         */
        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class MemoryConfig {

            /** 短期记忆策略 NONE / LAST_N_TURNS / FULL_SESSION;取自 {@code ShortTermMemoryStrategy.name()} */
            private String shortTermStrategy;

            /** 短期记忆窗口大小:仅 {@code LAST_N_TURNS} 时生效 */
            private Integer shortTermN;

            /** 长期记忆策略 NONE / ENABLED;取自 {@code LongTermMemoryStrategy.name()} */
            private String longTermStrategy;
        }
    }

    /**
     * A2A Agent 来源信息(client 层强类型镜像;仅 A2A 模式)。
     * <p>
     * 字段与 {@code domain.agent.valueobject.A2aSourceInfo} 一一对应;
     * {@code lastSyncEventType} 以 String 形式承载枚举 {@code name()},避免反向依赖 domain。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class A2aSource {

        /** Nacos group,例如 DEFAULT_GROUP */
        private String nacosGroup;

        /** Nacos 服务名,例如 a2a-agents@@flight-bot */
        private String nacosService;

        /** 远端实例 IP */
        private String instanceIp;

        /** 远端实例端口 */
        private Integer instancePort;

        /** A2A invoke endpoint 路径,例如 /a2a/invoke */
        private String endpointPath;

        /** Agent Card 中的远端版本号;缺失时显示 - */
        private String remoteVersion;

        /** Agent Card 中的远端 skills 列表(只读,平台无法挂载) */
        private List<RemoteSkill> remoteSkills;

        /** Agent Card 中的远端 MCP 工具列表(v2.3 新增,只读,平台无法挂载) */
        private List<RemoteMcp> remoteMcps;

        /** 完整 Agent Card JSON 原文,前端 Manifest Tab 展示 */
        private String agentCardJson;

        /** 最近同步时间 */
        private LocalDateTime lastSyncedAt;

        /** 最近同步事件来源;取自 {@code SyncEventType.name()} */
        private String lastSyncEventType;

        /** A2A 远端能力 skill 项(只读,平台无法挂载) */
        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class RemoteSkill {

            /** Skill 名称 */
            private String name;

            /** Skill 描述 */
            private String description;
        }

        /** A2A 远端 MCP 工具项(v2.3 新增,只读,平台无法挂载) */
        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class RemoteMcp {

            /** MCP 名称 */
            private String name;

            /** MCP 描述 */
            private String description;

            /** MCP server URL(可选,Agent Card 中可能不暴露) */
            private String serverUrl;
        }
    }
}
