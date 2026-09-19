# AgentScope HarnessAgent 改造技术方案

> 日期：2026-09-12  
> 状态：方案评审（未开工）  
> 范围：`agent-be` CONFIG 运行时（A2A 路径不在本期替换范围内）  
> 依据：与产品/研发对齐结论——用 AgentScope `HarnessAgent` 替换进程内 `ReActAgent`；OpenSandbox 作为 Harness 沙箱后端；Skill 仍由平台维护并在创建 Agent 时勾选绑定。

---

## 1. 背景与目标

### 1.1 现状

- CONFIG 模式在 `AgentRunnerFactory` 内拼装 **AgentScope `ReActAgent`**，经 `AgentRunnerService.agent.stream(...)` 输出 Event SSE。
- 工具：FC / MCP / 内置工具 + **`SandboxTool`（OpenSandbox 以 Toolkit 工具形态暴露）**。
- Skill：平台 DB 维护 → Agent `ConfigSnapshot.skillRefs` 勾选 → 运行时手搓 `SkillBox.registerSkill(...)`。
- 依赖：`agentscope-spring-boot-starter:2.0.0`（**不含** `agentscope-harness`）。

### 1.2 目标

将 CONFIG 执行核从裸 `ReActAgent` 升级为 AgentScope **`HarnessAgent`**（`ReAct` 循环不变，叠加 workspace / 沙箱 filesystem / 记忆与 compaction 等工程能力），并满足：

1. **OpenSandbox 作为 Harness 沙箱环境**（不是 `SandboxTool`）。
2. **Skill 仍由平台维护**，创建/编辑 Agent 时勾选绑定；不改为仅依赖 workspace 文件 Skill。
3. **调试台 / Open API / 评测** 在过渡期尽量保持现有 Event SSE 可用；协议升级可单列阶段。

### 1.3 非目标（本期不做或另案）

- 不替换 A2A（`A2aAgent`）路径。
- 不强制落地 SUPERVISOR / ROUTER 多 Agent 图（与 Harness Subagent 对齐另议）。
- 不把执行面迁出到 `rd-points-sphere`（架构文档与代码不一致问题另议）。
- 不默认开启 Harness 全量能力（memory / compaction / plan mode HITL / subagent 等按阶段打开）。

---

## 2. 概念对齐

| 说法 | 含义 |
|------|------|
| React / ReAct Agent | AgentScope `io.agentscope.core.ReActAgent`（推理→工具→回复循环） |
| Harness Agent | AgentScope `io.agentscope.harness.agent.HarnessAgent`，薄封装 ReAct + 工程能力 |
| 平台 Skill（曾称「DB Skill」） | 管理后台维护、落库、版本化，创建 Agent 时写入 `skillRefs` 的 Skill |
| Workspace Skill | Harness 默认 `workspace/skills/` 文件型 Skill（本期默认关闭，避免双轨） |
| OpenSandbox 当工具 | 现状：`SandboxTool` 注册进 Toolkit |
| OpenSandbox 当沙箱 | 目标：实现 Harness `Sandbox` / `SandboxClient` / `SandboxFilesystemSpec`，`.filesystem(...)` 接入 |

官方参考：[Harness Architecture](https://java.agentscope.io/v2/en/docs/harness/architecture.html)、[Sandbox](https://java.agentscope.io/v2/en/docs/harness/sandbox.html)。

---

## 3. 总体架构

```
创建/编辑 Agent（FE）
  ├─ 勾选平台 Skill → ConfigSnapshot.skillRefs
  └─ 绑定沙箱资产 → ConfigSnapshot.sandboxRef
        │
        ▼
AgentRunnerFactory（CONFIG）
  ├─ Model / Toolkit（FC·MCP·附件等业务工具；不再注册 SandboxTool）
  ├─ skillRepository(平台 Adapter) + skillFilter(仅 skillRefs)
  ├─ filesystem(OpenSandboxFilesystemSpec)  ← 新增适配层
  ├─ workspace / stateStore / RuntimeContext(userId, sessionId)
  └─ HarnessAgent.builder()...build()
        │
        ▼
AgentRunnerService
  └─ stream / streamEvents + 现有落库 / SSE
        │
        ▼
FE useInvokeStream（过渡期协议不变）
```

**原则：**

- 能力分层：Harness 管「长跑工程面」；平台继续管「资产与勾选」。
- 单一执行面：OpenSandbox 只走 Harness filesystem，去掉 Toolkit 侧 `SandboxTool`，避免双沙箱。
- 单一 Skill 来源：平台 Repository；关闭默认 workspace skills。

---

## 4. 分阶段实施

### 阶段 0 — 依赖与类型收口

| 项 | 说明 |
|----|------|
| Maven | 增加 `io.agentscope:agentscope-harness:${agentscope.version}`（当前 2.0.0；是否升 2.0.1+ 单独评估） |
| 返回类型 | `AgentRunnerFactory` / `AgentRunnerService` 从 `AgentBase` 收到 `Agent`（或薄封装），因 `HarnessAgent implements Agent` 而非 `AgentBase` |
| A2A | 仍返回 `A2aAgent`，统一落在 `Agent` 抽象上 |

**验收：** 编译通过；A2A 冒烟不回归。

### 阶段 1 — 最小 Harness 换壳（可先不含 OpenSandbox SPI）

目的：验证 Harness 可替换 ReAct，调试台链路不断。

- CONFIG 分支改为 `HarnessAgent.builder()`，保留现有 model / toolkit（**暂可**仍挂 `SandboxTool` 作为过渡，或与阶段 2 合并）/ maxIters / permission / sysPrompt 拼装。
- Skill：改为 `.skillRepository(平台 Adapter)` + 按 `skillRefs` 过滤；`disableDefaultWorkspaceSkills`。
- 调用侧显式 `RuntimeContext.builder().userId(...).sessionId(sessionNum).build()`。
- 流式：过渡继续 `stream(msg, ctx)` → `Flux<Event>`，FE 不动。
- 关闭或暂不配置：Harness Docker filesystem、默认 memory/compaction 激进策略（按需 `disable*`）。

**验收：** 调试台多轮对话、工具调用、Skill 加载、评测冒烟通过。

### 阶段 2 — OpenSandbox 作为 Harness 沙箱（核心增量）

目标：**去掉 `SandboxTool`**，OpenSandbox 成为 `.filesystem(...)` 后端。

#### 2.1 适配层（新建）

建议落在 `rd-agent-be-infra`（或 application 编排 + infra 实现）：

| 组件 | 职责 |
|------|------|
| `OpenSandboxClient` implements `SandboxClient` | create / resume / delete；state 序列化 |
| `OpenSandboxHarnessSandbox` implements `Sandbox` | start / stop / shutdown；**exec**；persist / hydrate workspace |
| `OpenSandboxFilesystemSpec` extends `SandboxFilesystemSpec` | 声明隔离域、snapshot、镜像/实例来源 |
| （可选）`SandboxFileTransfer` | 大文件上下传，避免纯 base64-over-exec |

复用现有：`SandboxRunner` / 平台沙箱资产（`sandboxRef` → instanceId）/ Redis 会话映射思路。

隔离建议：首期 **`IsolationScope.SESSION`**，与现有「会话复用容器」一致；再评估 USER 级共享。

#### 2.2 进阶：已供给实例

若容器由平台异步供给、运行时只 `connect`：可用官方 **`SandboxContext.externalSandbox`**，把已连接实例放入当次 `RuntimeContext`，由框架 `stop`、业务自行决定 `shutdown`。

#### 2.3 镜像契约（硬门槛）

Harness 文件工具依赖沙箱内 POSIX 工具链（`sh`、`sed`、`grep`、`find`、GNU `stat -c`、`tar`、`base64`、`python3` 等）。须对现网镜像（如 `opensandbox/code-interpreter`）做 **conformance 检查**；不满足则换镜像或裁剪工具面。

#### 2.4 产品行为变化

| 之前 | 之后 |
|------|------|
| 模型调 `execute_command` / `execute_python` | 模型使用 Harness 内置 `execute` / `read_file` / `write_file` 等 |
| 系统提示词手写「沙箱运行环境」长文 | 由 Harness workspace 投影 + sandbox prompt 承担一部分；平台提示词需回归精简 |

**验收：** 绑定沙箱的 Agent 能读写文件、执行命令；会话恢复/TTL 行为符合预期；无 `SandboxTool` 注册。

### 阶段 3 — 平台 Skill 装配定型

产品语义不变：

1. 后台维护 Skill（含版本与资源）。  
2. 创建/编辑 Agent 勾选 → `skillRefs`。  
3. 运行时仅装载勾选项。

技术要点：

- `.skillRepository(AgentScopeSkillRepositoryAdapter)`（已实现 `AgentSkillRepository`）。
- **过滤**：`SkillFilter` 或包装 Repository，只暴露当前快照 `skillRefs`（含版本策略：跟随当前 / 钉版本，与现逻辑对齐）。
- `disableDefaultWorkspaceSkills`（及必要时 `disableDynamicSkills`），避免与平台 Skill 双轨。
- 原「Skill 资源上传到 OpenSandbox」逻辑：改为在 Harness workspace 投影或沙箱 start 钩子中同步（与阶段 2 联调）。

**验收：** 只勾选的 Skill 对模型可见；未勾选不可用；版本钉扎行为与现网一致。

### 阶段 4（可选）— 协议与增强能力

- `stream` → `streamEvents`（`AgentEvent`）；FE `useInvokeStream` / 类型 / 测试同步。
- 按需开启：memory、compaction、Plan Mode（与现有 `enableTaskList` 语义区分）、Subagent、分布式 `stateStore` / snapshot。
- AgentScope 小版本升级（含 Harness 修复）。

---

## 5. 关键改动面清单

| 层级 | 文件/模块（示意） | 变更 |
|------|-------------------|------|
| POM | parent / infra | 引入 `agentscope-harness` |
| application | `AgentRunnerFactory` | CONFIG → `HarnessAgent`；Skill/Sandbox 装配 |
| application | `AgentRunnerService` | `Agent` + `RuntimeContext`；流式 API |
| application | `SandboxTool` | 阶段 2 后退役（或仅兼容开关） |
| infra | 新增 OpenSandbox ↔ Harness SPI | 沙箱适配 |
| infra | `AgentScopeSkillRepositoryAdapter` | 过滤/版本策略微调 |
| domain/client | `ConfigSnapshot` 等 | 首期可不加字段；若需引擎开关再加 `runtimeEngine` |
| FE | 编辑页 / 调试台 | 阶段 1–3 可不改；阶段 4 视 Event 协议 |
| 配置 | `application-*.yml` | workspace 根路径、snapshot、隔离域、禁用开关 |
| 测试 | factory / runner / 沙箱契约 / 流式 | 单测 + 调试台/Open 冒烟 |

---

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| 双沙箱 / 双 Skill | 阶段 2/3 强制单一来源；CI 断言无 `SandboxTool`、无默认 workspace skills |
| 镜像不满足 Harness 契约 | 阶段 2 开工前做 conformance；不通过则阻塞上线 |
| `AgentBase` → `Agent` 波及面 | 统一抽象 + 编译期清零；A2A 回归 |
| 多副本状态落本地盘 | 生产必须可共享 `AgentStateStore` + 非 Noop snapshot |
| sysPrompt 变「胖」 | 回归替换变量 / 沙箱说明 / 工具清单；与 Harness 注入去重 |
| 废弃 `stream` API | 过渡保留；阶段 4 升级 `streamEvents` |
| 工作量低估 | 阶段 1 与阶段 2 可拆 PR；OpenSandbox SPI 单独估时 |

---

## 7. 工作量直觉（仅供排期）

| 阶段 | 量级 |
|------|------|
| 0 + 1 换壳 + 平台 Skill 过滤 | ~1–3 人日 |
| 3 Skill 定型与资源同步联调 | ~1–2 人日（可与 1 合并） |
| 2 OpenSandbox → Harness SPI | ~数人日（含契约测试、快照/会话） |
| 4 协议升级与增强能力 | 按周、按开关单项估 |

---

## 8. 已确认决策（评审锁定）

1. **替换对象**：AgentScope `HarnessAgent`，不是 OpenAI Harness Engineering，也不是换前端框架。  
2. **OpenSandbox**：作为 **Harness 沙箱后端**（SPI 适配），**不再**作为长期 Toolkit 工具。  
3. **Skill**：继续 **平台维护 + 创建 Agent 勾选**；Harness 侧用 `skillRepository` + 过滤；关闭默认 workspace skills。  
4. **推进方式**：分阶段；先换壳与 Skill，再上 OpenSandbox SPI；FE 协议默认后置。  
5. **A2A / sphere 迁出**：不在本期。

---

## 9. 待开工前再确认的细节（不阻塞方案认同）

1. 阶段 1 是否允许短暂保留 `SandboxTool`，还是与阶段 2 捆绑上线。  
2. 隔离域：SESSION 是否为生产默认。  
3. 快照：Noop / 本地 / OSS / Redis 选哪种。  
4. 是否增加 `runtimeEngine=REACT|HARNESS` 双轨开关做灰度。  
5. AgentScope 是否顺带升级到含 Harness 修复的小版本。

---

## 10. 建议验收清单（阶段 1–3 合集）

- [ ] CONFIG Agent 使用 `HarnessAgent` 构建成功  
- [ ] 调试台多轮：thinking / tool / text 交错正常  
- [ ] 仅勾选的平台 Skill 可用；未勾选不可见  
- [ ] 绑定沙箱后文件读写与命令执行走 Harness filesystem → OpenSandbox  
- [ ] 进程内无 `SandboxTool` 注册（阶段 2 完成后）  
- [ ] Open / 评测主路径不回归  
- [ ] A2A Agent 仍可用  

---

**文档结束。** 评审通过后再落码；建议先开「阶段 0+1」PR，OpenSandbox SPI 独立「阶段 2」PR。
