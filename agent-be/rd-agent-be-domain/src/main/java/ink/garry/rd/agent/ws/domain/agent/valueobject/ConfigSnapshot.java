package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Agent 配置快照值对象（不可变）。
 * <p>
 * <b>仅 CONFIG 模式 Agent 写入此值对象</b>；A2A Agent 不参与版本化，永远不会
 * 持久化 ConfigSnapshot。由 facade 层 AgentVersion 序列化为 JSON 落盘。
 * <p>
 * 字段约束：
 * <ul>
 *   <li>agentType：S2 内固定 NORMAL；监督者 / 路由 S3 开放</li>
 *   <li>SUPERVISOR / ROUTER 时 childAgentNums 必须非空，且每个子 Agent 必为 CONFIG + NORMAL</li>
 * </ul>
 * <p>
 * <b>v4.0 资产化改造（Agent 配置优化）</b>：
 * <ul>
 *   <li><b>删除</b> {@code model} / {@code modelApiKey} / {@code modelBaseUrl} 三个手填字段；
 *       <b>新增</b> {@link #modelId}（模型管理业务编号 num 引用）。运行时按 modelId 经
 *       {@code ModelCredentialResolver} 解析模型管理记录取 modelId/baseUrl/apiKey 装配 LLM，
 *       Agent 不再自带模型凭证快照。</li>
 *   <li><b>新增</b> {@link #sandboxRef}（沙箱管理引用，单选可空）。</li>
 *   <li>原 {@code mcpNums} <b>重命名为</b> {@link #toolNums}（工具引用，含 MCP / FunctionCall，多选）。</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigSnapshot {

    /**
     * Agent 名称（v3.0 起纳入版本快照）。
     * <p>
     * 草稿编辑时由前端 step1.name 写入；发布事务内同步到 {@code agent.name} 主表字段，
     * 让列表 / 详情头展示与版本一致。
     */
    private String name;

    /** Agent 描述（v3.0 起纳入版本快照），同 {@link #name} 同步规则 */
    private String description;

    /** 行为类型 NORMAL / SUPERVISOR / ROUTER */
    private AgentType agentType;

    /** 系统提示词，作用于全部对话 */
    private String systemPrompt;
    /** 用户提示词模板，可含占位符 */
    private String userPrompt;
    /**
     * 关联模型的业务编号 num（v4.0：模型管理引用，替代原 model/modelApiKey/modelBaseUrl 手填）。
     * <p>
     * 存模型管理的 num（如 {@code MDL...}），全局唯一稳定；运行时按本字段经
     * {@code ModelCredentialResolver} 解析模型管理记录取 modelId / baseUrl / apiKey(解密) 装配 LLM。
     * 发布时校验对应模型为 ENABLED；运行时再校验兜底。
     */
    private String modelId;
    /** 采样温度 0.0~2.0 */
    private Double temperature;
    /**
     * 是否启用 Plan 模式；本期仅持久化和展示，运行时不消费
     */
    private Boolean enablePlan;

    /** 最大迭代轮次（ReAct 循环次数），默认 10 */
    private Integer maxIters;
    /** 挂载的 Skill 业务编号列表（多选） */
    private List<String> skillNums;
    /** 挂载的工具业务编号列表（v4.0：原 mcpNums 重命名；含 MCP / FunctionCall，多选） */
    private List<String> toolNums;
    /** 挂载的 Skill 版本引用列表；新写入优先使用 */
    private List<SkillRef> skillRefs;
    /** 挂载的工具版本引用列表；工具具备版本能力后按 versionNum 解析 */
    private List<ToolRef> toolRefs;
    /** 关联沙箱的引用标识（v4.0 新增：沙箱管理引用，单选可空） */
    private String sandboxRef;
    /** 子 Agent 业务编号列表（仅 SUPERVISOR / ROUTER 使用） */
    private List<String> childAgentNums;
    /** 记忆配置（v2.5 重构：策略枚举 + 短期窗口 N） */
    private MemoryConfig memoryConfig;

    /** 每秒最大调用次数 */
    private Integer qps;
    /** 每日预算（次数 / token，按运营协议口径） */
    private Integer dailyBudget;
}
