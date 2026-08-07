# Design 总章

> 这份文件是设计文档的总目录。具体设计放在 [`design-docs/`](design-docs/) 下。

---

## 设计原则

> 完整的 agent-first 操作原则见 [design-docs/core-beliefs.md](design-docs/core-beliefs.md)。这里只列与 rd-agent-be 强相关的设计原则。

- **Parse, don't validate**:在系统边界(HTTP / sphere REST 响应)处把数据解析成强类型,业务层假设数据已合法
- **Control Plane / Data Plane 严格分离**:本服务不感知 agent 执行细节,所有 sphere 业务都通过 `SphereProvider` 透传
- **Provider 模式强制**:横切关注点(sphere / 审计 / 鉴权上下文)必须通过显式接口进入 biz 层(详见 [ARCHITECTURE.md §6](../ARCHITECTURE.md))
- **降级优先于一致性**:`/admin/api/v1/internal/prompts/active` 的设计目标是"be 全宕时 platform 业务不中断"(见 [docs/RELIABILITY.md](RELIABILITY.md))
- **小心引入抽象**:3 个具体例子之后再考虑抽象,"看起来可能会有"不是抽象的理由

## 重要决策档案

> 每条决策都应该在 [`design-docs/`](design-docs/) 下有独立文件。本表是索引。

| 编号 | 决策 | 状态 | 文档 |
|------|------|------|------|
| 001 | 复用 PG 集群 + 独立 schema `rd_agent` | accepted (PRD §9) | <!-- TODO: 抽出独立 ADR 文件 --> |
| 002 | Prompt 变量占位符语法采用单花括号 `{var}`(Python str.format 兼容) | accepted (PRD §9) | <!-- TODO: 抽出独立 ADR 文件,补充转义/嵌套/未提供变量行为 --> |
| 003 | 服务间无鉴权,靠 K8s NetworkPolicy 防护 | accepted (PRD §2.1, memory) | <!-- TODO: 抽出独立 ADR 文件 --> |

## 模块设计文档

> 大型模块的内部设计独立成文。

| 模块 | 设计文档 | 状态 |
|------|---------|------|
| auth (GT OAuth + JWT) | <!-- TODO: design-docs/auth/overview.md --> | TODO |
| thread | <!-- TODO: design-docs/thread/overview.md --> | TODO |
| prompt (版本 / 激活 / diff / 审计) | <!-- TODO: design-docs/prompt/overview.md --> | TODO |
| sphere-client | <!-- TODO: design-docs/sphere-client/overview.md --> | TODO |
| audit | <!-- TODO: design-docs/audit/overview.md --> | TODO |

## 模板

新建设计决策档案时,建议格式:

```markdown
# <编号> — <决策标题>

**日期:** YYYY-MM-DD
**状态:** proposed / accepted / superseded by NNN
**决策者:** <人或团队>

## 背景

<是什么问题、为什么必须现在做决策>

## 选项

<列出考虑过的所有选项,包括"什么都不做">

## 决策

<最终选哪个、为什么>

## 后果

<带来什么好处、有什么代价、需要回头检查什么>
```

---

> 推荐用法参考 [matklad: ARCHITECTURE.md](https://matklad.github.io/2021/02/06/ARCHITECTURE.md.html) 与 [Architecture Decision Records](https://adr.github.io/)。
