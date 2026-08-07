# Design 总章

> 这份文件是设计文档的总目录。具体设计放在 [`design-docs/`](design-docs/) 下。

---

## 设计原则

> 完整的 agent-first 操作原则见 [design-docs/core-beliefs.md](design-docs/core-beliefs.md)。这里只列与 rd-agent-fe 强相关的设计原则。

- **设计还原优先**:Figma 是真相,任何偏离都需要 PR 中说明理由
- **扁平化风格**:无内部分隔线、无卡片阴影、纯白底(已在 Figma 落地)
- **调试台不带业务字段**:仅承载通用 sphere 调用过程,禁止出现 Jira / 工分等业务概念(PRD §4.2)
- **状态分层**:服务端状态走 React Query / SWR(待选);UI 状态走组件本地;跨页面状态(用户 / 当前 thread / 流式 run)走 store
- **Parse, don't validate**:services 边界处用 zod / yup 解析后端响应到强类型,业务层假设已合法
- **小心引入抽象**:3 个具体例子之后再考虑抽象组件,"看起来可能会有"不是抽象的理由

## 重要决策档案

> 每条决策都应该在 [`design-docs/`](design-docs/) 下有独立文件。本表是索引。

| 编号 | 决策                                                      | 状态                                      | 文档                        |
| ---- | --------------------------------------------------------- | ----------------------------------------- | --------------------------- |
| 001  | 调试台 UI 形态从"playground 双栏"改为"ChatGPT 风格对话流" | accepted (PRD §4.2 v0.2 修订)             | <!-- TODO: 抽出独立 ADR --> |
| 002  | i18n 仅 zh-CN(en-US 留空,v0.2 补)                         | accepted (PRD §5.5)                       | <!-- TODO -->               |
| 003  | 流式输出用轮询(1.5s)而非 SSE/WebSocket                    | accepted (沿用 SphereDebug 现状,PRD §5.1) | <!-- TODO -->               |
| 004  | 与工分平台不共享 GT OAuth session(各自登录一次)           | accepted (PRD §9)                         | <!-- TODO -->               |

## 模块设计文档

> 大型模块的内部设计独立成文。

| 模块                                         | 设计文档                                               | 状态 |
| -------------------------------------------- | ------------------------------------------------------ | ---- |
| 调试台(Console)                              | <!-- TODO: design-docs/console/overview.md(切片 1) --> | TODO |
| 智能体管理                                   | <!-- TODO: design-docs/agents/overview.md(切片 2) -->  | TODO |
| 提示词管理(列表 + 编辑器 + 版本历史 + diff)  | <!-- TODO: design-docs/prompts/overview.md(切片 3) --> | TODO |
| 全局布局(NavSidebar / UserMenu / Breadcrumb) | <!-- TODO: design-docs/layout/overview.md(切片 1) -->  | TODO |
| 流式状态机(Run / Step)                       | <!-- TODO: design-docs/stream/overview.md(切片 1) -->  | TODO |

## 模板

新建设计决策档案时,建议格式:

```markdown
# <编号> — <决策标题>

**日期:** YYYY-MM-DD
**状态:** proposed / accepted / superseded by NNN
**决策者:** <人或团队>

## 背景

## 选项

## 决策

## 后果
```

---

> 推荐用法参考 [matklad: ARCHITECTURE.md](https://matklad.github.io/2021/02/06/ARCHITECTURE.md.html) 与 [Architecture Decision Records](https://adr.github.io/)。
