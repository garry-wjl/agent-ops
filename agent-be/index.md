# FunctionCall 工具注册给 Agent 执行 —— 设计方案

> 适用范围：rd-agent-be 运行时（agentrunner）将「工具管理」沉淀的 FunctionCall 工具，注册进 agentscope `Toolkit`，让 Agent 在会话中自主调用并执行。
> 状态：设计方案（plan-only），尚未落码。
> 关联代码：`rd-agent-be-application/.../application/tool/factory/ToolRunnerFactory.java`、`.../application/agentrunner/factory/AgentRunnerFactory.java`、`.../application/agentrunner/tool/SandboxTool.java`。

---

## 1. 背景与目标

`ToolRunnerFactory.buildSchemas(toolNum)` 已能把 FunctionCall（`ToolType.FUNCTION_CALL`、`CreationMode.MANUAL`）工具的端点元数据翻译为一组 agentscope `io.agentscope.core.model.ToolSchema`（即 LLM function-calling 的工具描述）。

目标：在此基础上，让 **Agent 真正执行** 这些工具——即模型决定调用某工具时，运行时在进程内发起对应的 HTTP API 调用并把结果回喂给模型，全程自主、无需人工介入。

---

## 2. 关键认知：裸 `ToolSchema` 不会被执行

这是最容易踩的坑，必须先讲清楚。

如果直接 `toolkit.registerSchema(toolSchema)`：

- agentscope 会把它包成一个 `SchemaOnlyTool`，且 `externalTool = true`；
- 当 LLM 调用该工具时，`SchemaOnlyTool.callAsync(...)` 抛出 `ToolSuspendException`；
- 框架捕获后**挂起**本次运行（`GenerateReason.TOOL_SUSPENDED`），把 tool-call 交还给**调用方**；
- **Agent 不会发起 HTTP 调用**，需要你自己的外层代码执行后再 resume。

这是「外部 / 客户端执行（external execution）」模式，**不是**「让 Agent 执行」。

> 结论：要让 Agent 自主执行，必须提供一个**同时带 schema 与执行逻辑**的工具——就像 `SandboxTool` 那样，只是因为 FunctionCall 的参数是运行时才从 DB 得知的动态结构，不能用 `@Tool` 注解式，得用编程式构建。

---

## 3. 两条路径对比

| 维度 | 路径 A：`registerSchema` | 路径 B：可执行 `AgentTool` ✅ 推荐 |
|---|---|---|
| 谁发起 HTTP 调用 | 外层调用方（挂起后 resume） | Agent 进程内自主发起 |
| 是否适配 FunctionCall | 仅当执行须放在 runner 之外（如下沉到 sphere）才用 | 适配，正是本需求要的 |
| 已构建的 `ToolSchema` 怎么用 | 传给 `registerSchema(...)` | 作为工具的 `inputSchema` 内嵌 |
| 运行时表现 | 挂起 → 外部执行 → 回喂 | 一步到位，模型直接拿到结果 |

---

## 4. 推荐方案（路径 B）

### 4.1 新增可执行工具类型 `FunctionCallTool`

- 位置：`application/agentrunner/tool/`，与 `SandboxTool` 并列。
- 继承 `io.agentscope.core.tool.ToolBase`（编程式构建）：
  - 通过 `ToolBase.builder().name(fnName).description(desc).inputSchema(params).concurrencySafe(true)` 构造；
  - 其中 `params` 即 `ToolRunnerFactory` 已生成的 JSON-Schema `Map`（`{type:object, properties, required}`）。
- 额外持有**执行绑定**：`baseUrl` + 单个 `ApiEndpoint`（method、path 模板、query/path 参数名、header 默认值）。
- 实现 `callAsync(ToolCallParam param)`：
  1. 读取模型填充的实参：`param.getInput()`；
  2. 用 path 参数值替换 path 模板中的 `{name}` 占位；
  3. 拼接 query 参数（模型未给的字段，用 `defaultValue` 兜底）；
  4. 按 `ApiHeader` 默认值设置请求头；
  5. 在 `Schedulers.boundedElastic()` 上发起 HTTP 调用（阻塞调用不能压在 reactor 事件循环线程上，与 `SandboxTool` 同一线程模型）；
  6. 响应体 → `ToolResultBlock.text(...)`；异常 → `ToolResultBlock.error(...)`。

### 4.2 遵守六层架构：HTTP 调用下沉 infra

原始 HTTP 调用属于 **infra**，不能写在 application 层。

- `FunctionCallTool`（application/agentrunner）只负责**装配请求参数 + 渲染结果**；
- 真正的 HTTP 请求委托给 infra 的端口——新增一个 `FunctionCallInvoker`（或扩展现有 `ToolGateway`）的实现，由 infra 提供 WebClient/HTTP 客户端；
- 这与 `SandboxTool` 把执行委托给 `SandboxRunner` 是同一套路。

### 4.3 改造 `ToolRunnerFactory`

- 新增 `List<AgentTool> buildTools(String toolNum)`：每个端点产出一个 `FunctionCallTool`（复用现有 `buildParameters` / `functionName` 辅助方法）；
- 注入 infra 的 HTTP 端口，构建工具时传入；
- 若仍需路径 A，可保留 `buildSchemas`；二者共享同一套参数 schema 构建逻辑。

### 4.4 接线进 `AgentRunnerFactory.build(...)`

在 CONFIG 分支、注册 SandboxTool / 技能之后，遍历挂载的工具：

```text
for toolNum in configSnapshot.getToolNums():
    按工具类型分流：
      FUNCTION_CALL → toolRunnerFactory.buildTools(toolNum)
                       .forEach(toolkit::registerAgentTool)
      MCP           → 走 MCP 接入路径（toolkit.registerMcpClient，本期不展开）
```

- 向 `AgentRunnerFactory` 注入 `ToolRunnerFactory`；
- 注意：`ConfigSnapshot.toolNums` 同时混有 MCP 与 FunctionCall 两类，**必须按 `ToolType` 分流**（或让 `buildTools` 对非 FC 类型直接跳过/抛错）。

---

## 5. 注意事项与边界

- **鉴权 / 密钥**：目标 API 若需凭证，应在运行时解析（参照模型侧 `ModelCredentialResolver`），不要把密钥写进 schema 或硬编码进 header。
- **仅覆盖 `MANUAL`**：`OPENAPI_SPEC` 形态的工具，端点参数明细未落在聚合里（仅有 `EndpointMeta` 摘要），需先接 OpenAPI 解析器才能恢复逐参数 schema——与当前 `buildSchemas` 的边界一致。
- **函数名唯一性**：同一 `Toolkit` 内 function name 须唯一，已由 `ToolRunnerFactory.functionName(...)` 的清洗逻辑保证。
- **上下文体积**：一个工具多端点 = 多个函数进入 system prompt，端点很多时注意 prompt 体积，本期可暂不优化。

---

## 6. 落地步骤清单（建议顺序）

1. infra：定义并实现 `FunctionCallInvoker`（HTTP 调用端口）。
2. application/agentrunner/tool：新增 `FunctionCallTool extends ToolBase`，委托 infra 端口执行。
3. application/tool/factory：`ToolRunnerFactory` 增加 `buildTools(toolNum)`，复用现有 schema 构建逻辑。
4. application/agentrunner/factory：`AgentRunnerFactory` 注入 `ToolRunnerFactory`，在 CONFIG 分支按类型分流注册。
5. 验证：`mvn -DskipTests clean package` + 单测；本地起服务跑一个挂了 FunctionCall 工具的 Agent，确认模型可调用并拿到真实 HTTP 结果。
