package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 Agent 请求参数。
 * <p>
 * 仅 CONFIG（配置模式）走此入口；A2A 模式由后台订阅 Nacos 自动同步产生，
 * 不通过 REST 创建。后端会强制 creationMode = CONFIG，前端无需传该字段。
 */
@Data
public class AgentCreateParam {

    /** Agent 显示名（同 owner 下唯一） */
    @NotBlank(message = "name 不能为空")
    private String name;

    /** Agent 描述，用于列表展示与挂载下拉提示 */
    private String description;

    /** 业务标签（可空，CONFIG / A2A 共用） */
    private List<String> tags;

    /** NORMAL / SUPERVISOR / ROUTER（监督者 / 路由 S3 才开放，M2 内固定 NORMAL） */
    @NotBlank(message = "agentType 不能为空")
    private String agentType;

    /** 配置模式 - 关联模型业务编号 num（v4.0：模型管理引用，替代原 model/modelApiKey/modelBaseUrl 手填） */
    private String modelId;

    /** 配置模式 - 系统提示词，作用于全部对话 */
    private String systemPrompt;

    /** 配置模式 - 用户提示词模板，可含占位符 */
    private String userPrompt;

    /** 配置模式 - 采样温度，0.0~2.0 */
    private Double temperature;

    /** 是否启用 Plan 模式；本期仅保存和回显 */
    private Boolean enablePlan;

    /** 最大迭代轮次（ReAct 循环次数），默认 10 */
    private Integer maxIters;

    /** 配置模式 - 挂载 Skill 业务编号列表（多选） */
    private List<String> skillNums;

    /** v4.0 - 挂载工具业务编号列表（原 mcpNums 重命名；含 MCP / FunctionCall，多选） */
    private List<String> toolNums;

    /** 挂载 Skill 的版本引用；新写入优先使用 */
    private List<SkillRefParam> skillRefs;

    /** 挂载工具的版本引用；工具具备版本后按 versionNum 解析 */
    private List<ToolRefParam> toolRefs;

    /** v4.0 - 关联沙箱引用标识（沙箱管理引用，单选可空） */
    private String sandboxRef;

    /** 配置模式 - 子 Agent 业务编号列表（监督者/路由强制选） */
    private List<String> childAgentNums;

    /** 配置模式 - 记忆配置 { shortTermEnabled, longTermEnabled } */
    private Map<String, Object> memoryConfig;

    /** 限流 - 每秒最大调用次数 */
    private Integer qps;

    /** 限流 - 每日预算（元） */
    private Integer dailyBudget;
}
