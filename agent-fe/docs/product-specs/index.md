# Product Specs — 索引

> 这个目录存放 rd-agent-fe 视角下的产品规格(主要是页面 / 交互的拆解)。
> 完整 Agent Sphere 管理后台的 PRD 在 [`../../docs/agent-sphere/PRD-MVP.md`](../../../docs/agent-sphere/PRD-MVP.md),本目录**不复制**该 PRD,只放页面 / 交互拆解。

---

## 当前规格

| 规格          | 模块                                       | 状态  | 责任产品  | 最近更新 |
| ------------- | ------------------------------------------ | ----- | --------- | -------- |
| <!-- TODO --> | 全局布局(NavSidebar / TopBar / Breadcrumb) | draft | quincy.qi | 待补     |
| <!-- TODO --> | 调试台(Console)— ChatGPT 风格对话流        | draft | quincy.qi | 待补     |
| <!-- TODO --> | 智能体管理 — 列表页                        | draft | quincy.qi | 待补     |
| <!-- TODO --> | 智能体管理 — 详情页                        | draft | quincy.qi | 待补     |
| <!-- TODO --> | 提示词管理 — 列表页                        | draft | quincy.qi | 待补     |
| <!-- TODO --> | 提示词管理 — 编辑器                        | draft | quincy.qi | 待补     |
| <!-- TODO --> | 提示词管理 — 版本历史 + diff               | draft | quincy.qi | 待补     |
| <!-- TODO --> | 登录页 / GT OAuth callback                 | draft | quincy.qi | 待补     |
| <!-- TODO --> | 全局错误态(403 / 404 / 5xx)                | draft | quincy.qi | 待补     |

> 上述规格均对应 PRD-MVP §4.x 各小节 + Figma `hoQIHy1FcQdfE49oDsiSV9` 的具体节点。

## 模板

新建规格时建议放到 `_template/` 子目录下做一份精简模板(如 `_template/PRD模板-精简版.md`),团队成员复制后修改。

模板应包含:

- 背景与目标(指向源 PRD 哪一节)
- 用户故事(US-N)
- UI 结构(对应 Figma 节点 id)
- 交互细节(状态机、按钮行为、键盘快捷键)
- API 契约(调 be 哪些接口)
- 验收标准(浏览器跑通 + 截图对齐 Figma)
- 不做的事 + 原因
- 度量指标

## 命名约定

- `<page-or-feature-name>.spec.md`:单一页面或功能
- `<module-name>.spec.md`:整个模块的需求集合
- `_template/`:模板目录(下划线前缀避免和实际 spec 混淆)

---

> **理念**:spec 不是合同,是对齐工具。当代码和 spec 出现差异时,优先更新 spec 而不是反向推断。
> **本仓库的 spec 偏前端**:be 视角的规格在 [`../../rd-agent-be/docs/product-specs/`](../../../rd-agent-be/docs/product-specs/),源头 PRD 在 [`../../../docs/agent-sphere/PRD-MVP.md`](../../../docs/agent-sphere/PRD-MVP.md)。
