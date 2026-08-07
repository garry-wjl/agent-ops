# Design Docs — 索引

> 这个目录存放 rd-agent-fe 的所有设计决策档案。
> [DESIGN.md](../DESIGN.md) 是入口,本文件是详细索引。

---

## 核心信念

- [core-beliefs.md](core-beliefs.md) — agent-first 操作原则(本项目特化版)

## 决策档案

> 命名约定:`<编号>-<决策标题>.md`,编号从 001 开始递增。

| 编号 | 标题                                                                    | 状态                               | 日期       |
| ---- | ----------------------------------------------------------------------- | ---------------------------------- | ---------- |
| 001  | <!-- TODO --> 调试台 UI 形态从"playground 双栏"改为"ChatGPT 风格对话流" | accepted (源 PRD §4.2 v0.2)        | 2026-05-09 |
| 002  | <!-- TODO --> i18n 仅 zh-CN,en-US 留空待 v0.2                           | accepted (源 PRD §5.5)             | 2026-05-09 |
| 003  | <!-- TODO --> 流式输出用轮询(1.5s)而非 SSE/WebSocket                    | accepted (源 SphereDebug,PRD §5.1) | 2026-05-09 |
| 004  | <!-- TODO --> 与工分平台不共享 GT OAuth session                         | accepted (源 PRD §9)               | 2026-05-09 |

> 已在 PRD 决议但尚未抽出独立 ADR 文件的决策,优先级 P1,见 [exec-plans/](../exec-plans/) 下抽 ADR 任务。

## 模块设计

> 大型模块的内部设计文档目录。建议为每个模块建子目录,例如 `console/overview.md`、`console/streaming-state-machine.md`。

| 模块                                        | 设计文档                                               |
| ------------------------------------------- | ------------------------------------------------------ |
| 全局布局(NavSidebar / TopBar / Breadcrumb)  | <!-- TODO: design-docs/layout/overview.md(切片 1) -->  |
| 调试台(Console)                             | <!-- TODO: design-docs/console/overview.md(切片 1) --> |
| 流式状态机(Run / Step)                      | <!-- TODO: design-docs/stream/overview.md(切片 1) -->  |
| 智能体管理                                  | <!-- TODO: design-docs/agents/overview.md(切片 2) -->  |
| 提示词管理(列表 + 编辑器 + 版本历史 + diff) | <!-- TODO: design-docs/prompts/overview.md(切片 3) --> |
| GT OAuth 登录流程                           | <!-- TODO: design-docs/auth/overview.md(切片 1) -->    |

---

## 写设计文档的格式

### 决策档案(ADR)

```markdown
# <编号> — <决策标题>

**日期:** YYYY-MM-DD
**状态:** proposed / accepted / superseded by NNN
**决策者:**

## 背景

## 选项

## 决策

## 后果
```

### 模块设计

```markdown
# <模块名> — Design Overview

## 1. 目标与边界

## 2. 数据模型(types)

## 3. 接口设计(对应 be 哪些 API)

## 4. UI 结构(对应 Figma 哪个 node)

## 5. 关键流程

## 6. 与其他模块的关系

## 7. 已知风险
```

---

> 当一个文档不再准确反映代码时:(a) 立即更新 (b) 标记 superseded (c) 删掉。**不要保留陈旧文档** —— 对 agent 来说"陈旧文档"比"没文档"更糟。
