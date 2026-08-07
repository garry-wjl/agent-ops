# Product Specs — 索引

> 这个目录存放 rd-agent-be 视角下的产品规格(主要是后端模块的功能拆解)。
> 完整 Agent Sphere 管理后台的 PRD 在 [`../../docs/agent-sphere/PRD-MVP.md`](../../../docs/agent-sphere/PRD-MVP.md),本目录**不复制**该 PRD,只放后端模块拆解。

---

## 当前规格

| 规格 | 模块 | 状态 | 责任产品 | 最近更新 |
|------|------|------|---------|---------|
| <!-- TODO --> | thread | draft | quincy.qi | 待补 |
| <!-- TODO --> | prompt(版本/激活/diff/回滚) | draft | quincy.qi | 待补 |
| <!-- TODO --> | 智能体管理(后端 API) | draft | quincy.qi | 待补 |
| <!-- TODO --> | 调试台(后端 API) | draft | quincy.qi | 待补 |
| <!-- TODO --> | 审计日志 | draft | quincy.qi | 待补 |
| <!-- TODO --> | Internal API(场景 D) | draft | quincy.qi | 待补 |

## 模板

新建规格时建议放到 `_template/` 子目录下做一份精简模板(如 `_template/PRD模板-精简版.md`),团队成员复制后修改。

模板应包含:
- 背景与目标(指向源 PRD 哪一节)
- 后端 API 列表(method + path + 鉴权要求)
- 数据模型(表 / 字段 / 索引)
- 验收标准
- 不做的事 + 原因
- 度量指标

## 命名约定

- `<feature-name>.spec.md`:单一功能
- `<module-name>.spec.md`:整个模块的需求集合
- `_template/`:模板目录(下划线前缀避免和实际 spec 混淆)

---

> **理念**:spec 不是合同,是对齐工具。当代码和 spec 出现差异时,优先更新 spec 而不是反向推断。
> **本仓库的 spec 偏后端**:fe 视角的规格在 [`../../rd-agent-fe/docs/product-specs/`](../../../rd-agent-fe/docs/product-specs/),源头 PRD 在 [`../../../docs/agent-sphere/PRD-MVP.md`](../../../docs/agent-sphere/PRD-MVP.md)。
