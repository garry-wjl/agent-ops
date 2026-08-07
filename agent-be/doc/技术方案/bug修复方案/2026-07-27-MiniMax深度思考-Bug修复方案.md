# Bug 修复方案

> 项目：garry Agent 管理平台（AgentOps）
> 日期：2026-07-27
> 修复人：garry.wang1@garry.com
> 分支：`hotfix_20260727_minimax-think-tag`（仅后端 rd-agent-be）

---

## Bug：MiniMax 模型深度思考在调试台不展示

| 字段 | 值 |
|------|-----|
| 模块 | Agent 运行时 - 事件流（AgentRunnerService） |
| BUG 分类 | 适配缺陷 |
| 严重程度 | 严重 B（功能实质不可用） |
| 复现概率 | Always |

### 现象

配置 MiniMax 模型时，Agent 的"深度思考/推理过程"没有显示在调试台的折叠面板中。推理内容和最终答案混在一起作为正文文本展示（如 `<mm:think>The user is just saying hello</mm:think>你好！……`），没有独立折叠面板。

### 原因分析

**结论：后端 `AgentRunnerService` 事件流缺少对 `mm:think` 标签的解析。**

AgentScope 2.0 与后端/前端的交互协议中有两类 ContentBlock：

| 类型 | JS type | Java class | 前端展示 |
| --- | --- | --- | --- |
| 推理过程 | `thinking` | `ThinkingBlock` | 深度思考折叠面板 |
| 正文文本 | `text` | `TextBlock` | Markdown 正文 |

AgentScope 2.0 的 `DeepSeekFormatter` / `OpenAIChatFormatter` 只在标准 `reasoning_content` 字段有值时输出 `ThinkingBlock`。而 **MiniMax 的深度思考不走标准 `reasoning_content` 字段**，而是以自定标签 `<mm:think>...</mm:think>` 包裹后放进 `content` 字段。

结果：AgentScope SDK 把推理内容当成 `TextBlock` 下推，后端 `SegmentAccumulator` 走 `appendText()` 而非 `appendThinking()`，前端收到 `type: "text"` 而非 `type: "thinking"` —— 推理文本混入正文，不触发折叠面板。

**确认其他模型情况：**
- **DeepSeek R1**：标准 `reasoning_content` 字段，`DeepSeekFormatter` 正确处理，✅ 正常
- **OpenAI o1/o3**：标准 `reasoning_content` 字段，✅ 正常
- **Claude（Anthropic）**：独立协议 + formatter，有独立 `thinking` block，✅ 正常
- **Gemini**：独立 formatter，✅ 正常
- **GLM-4（智谱）**：标准 `reasoning_content` 字段，✅ 大概率正常
- **MiniMax M3 / abab**：`<mm:think>` 标签嵌在 `content` 字段，❌ 已确认

### 修改方案

| 端 | 改动内容 |
|----|---------|
| **后端** | 在 `AgentRunnerService.runAgent()` 的 Flux 链起始处加 `map(transformEvent)` 变换，拦截 REASONING 事件，检测 TextBlock 中的 `<mm:think>...</mm:think>` 标签，拆分为 ThinkingBlock + TextBlock。开闭标签可能跨 chunk 帧，使用 `AtomicBoolean` 标记跨 Event 状态。 |

**核心变化：**
- `AgentRunnerService` 新增 `static Event transformEvent(Event, AtomicBoolean)` 方法
- `SegmentAccumulator` **不改**
- 前端 **不改**

**处理规则：**

```
不在 think 标签内收到 TextBlock:
  ├─ 不含 <mm:think>     → 保持 TextBlock 不变
  └─ 含 <mm:think>       → 拆为 text_before + ThinkingBlock(标签内文本) + text_after
                            若仅开标签无闭合 → 标记 inThinkTag

在 think 标签内收到 TextBlock:
  ├─ 含 </mm:think>       → 拆为 ThinkingBlock(标签内文本) + text_after → 清标记
  └─ 不含闭合             → 整段转为 ThinkingBlock
```

**渲染效果（前端原样展示 segments 数组，不再混入标签）：**

```json
segments = [
  { kind: "thinking", text: "The user is just saying hello" },
  { kind: "text", text: "你好！👋 很高兴见到你～……" }
]
```

### 影响范围

- 仅后端 `rd-agent-be`，单文件 `AgentRunnerService.java`（application 层），无接口 / DB / 前端改动。
- 对 `ThinkingBlock` 或纯 text（无标签）的 Event 无任何影响（透出）。
- 行为变化：MiniMax 推理从"混入正文"变为"独立 thinking 段"，前端折叠面板正常触发。

### 验证

- 新增单元测试 `AgentRunnerServiceTransformEventTest`（7 用例）：覆盖 mm:think 标签拆分（含前缀/前缀+后缀/跨帧/纯 text/现有 ThinkingBlock/非 REASONING 事件透出/null 防御），全部通过。
- application 模块 `mvn test` 全量绿（60 例，无回归）。

---

## 附：修复规范（命名与分支）

- 文档：`技术方案/bug修复方案/2026-07-27-MiniMax深度思考-Bug修复方案.md`
- 分支：`hotfix_20260727_minimax-think-tag`
- 提交：`fix: 后端统一解析 MiniMax <mm:think> 标签，深度思考正常展示`