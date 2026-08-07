# Core Beliefs — Agent-first 操作原则(rd-agent-be 版本)

> 这份文件是 rd-agent-be 特有的"agent 工作原则"。它影响 agent 在做技术决策、写代码、做 review 时的偏好。

---

## 通用原则(源自 OpenAI Harness Engineering)

1. **Repository = System of Record**:仓库是唯一真相源,Slack/钉钉/口头约定对 agent 不可见
2. **AGENTS.md 是地图,不是说明书**:入口文件越短越好(本仓库限 ≤ 120 行)
3. **Agent legibility first**:为 agent 可读性优化(命名、目录、文档结构)
4. **Enforce invariants, not implementations**:用代码强制边界(ArchUnit / Spotless),不微观管理实现
5. **Garbage collection > big refactor**:持续小修小补,不堆债再大爆发
6. **进度持久化**:复杂任务必须留下 plan 在 `docs/exec-plans/active/`
7. **写代码前先输出方案**:除非用户明确说"直接写"或单行 typo
8. **遇坏味道立即修**:不复制现有 anti-pattern,发现规范缺口反馈到 docs

## 本项目特有的原则

- **Control Plane 严守边界**:本服务**不执行 agent 业务**,所有 sphere 行为都是透传或拉取协议;biz 层禁止直接 import HttpClient,必须走 `SphereProvider`
- **降级路径优先于功能完整**:`/admin/api/v1/internal/prompts/active` 的设计目标是"be 全宕时 platform 业务不中断" — 任何"看起来更优雅但破坏降级"的改动都要被否决
- **Internal API 是合同**:`/admin/api/v1/internal/*` 改动等同于改 platform 代码;只增不改不删,break 必须先在 [`../rd-agent-ws/docs/CONVENTIONS.md`](../../../rd-agent-ws/docs/CONVENTIONS.md) 通告
- **Prompt 是数据,不是配置**:不放 Apollo,不写文件,只在 PG;版本不可变(append-only)
- **审计是不变量,不是 feature**:任何写操作必须落审计;遗漏审计的 PR 直接 reject,不能"下次补"
- **不复制 PRD**:PRD 永远在 `../docs/agent-sphere/PRD-MVP.md`,本仓库只引用不复制
- **拷自 platform 不等于直接复制**:技术决策(authn / 异常体系 / 日志格式)对齐 platform,但**业务模型禁止盲目对齐**(thread / prompt 是本服务独有的领域)

## 与 platform 拷贝架构的注意事项

> rd-agent-be 拷自 rd-points-platform 架构,但要避免以下陷阱:

- 不要把 platform 的 `biz/task` / `biz/event` / `biz/dashboard` 等业务模块复制过来 — 它们和 agent sphere 无关
- 不要复制 platform 的 user / role 数据(故意做产品边界隔离,见 [PRODUCT_SENSE.md §3](../PRODUCT_SENSE.md))
- 可以复制:auth(GT OAuth + JWT)、common(异常体系 / 响应格式)、infra(Apollo / Redis / SkyWalking 接入)、CI 模板、K8s YAML 模板

---

> 任何 PR review 中反复出现的"我们一般会怎样做"都应该沉淀到本文件,否则 agent 下次还会犯同样的错。
