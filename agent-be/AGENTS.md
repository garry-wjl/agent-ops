# rd-agent-be — Agent 入口地图

> Agent Sphere 管理后台 Control Plane 后端 — 鉴权 / Session / Skill / Agent / Evaluation / 透传 sphere。
> 技术栈:Java 17 + Spring Boot 3.5,六层 Maven 多模块(facade / client / domain / infra / application / adapter)。
> 此文件是 agent 的**目录**,不是百科全书。想知道 X,请按下方 Lookup Table 跳转。
> 落码规范见 [CLAUDE.md](CLAUDE.md);架构契约见 [ARCHITECTURE.md](ARCHITECTURE.md)。

---

## 1. 项目一句话定位

Agent Sphere 管理后台的 Control Plane 后端,承担**会话层(Session)+ 调度层(权限/限流)+ 数据管理层(Skill 版本/Agent 注册/Evaluation/审计)**三大职责。前端 (rd-agent-fe) 通过本服务访问业务能力,业务侧 (rd-points-platform) 通过本服务的 `/admin/api/v1/internal/*` 接口拉取 active prompt;**本服务自身不执行 agent**,所有 agent 调用都透传到 rd-points-sphere(Data Plane)。

<!-- 详细职责矩阵见 PRD-MVP §2.2 -->

## 2. 顶层结构

```
rd-agent-be/
├── AGENTS.md                       ← 你在这里(业务入口地图)
├── CLAUDE.md                       ← 六层落码规范(代码模板 + 违规速查)
├── ARCHITECTURE.md                 ← 架构契约(依赖方向 + 强制手段)
├── docs/                           ← 知识库(system of record)
├── pom.xml                         ← 父 POM(版本统一管理)
├── rd-agent-be-facade/             ← 契约层(DomainEntity / Result / 事件 DTO)
├── rd-agent-be-client/             ← 数据契约层(Param / DTO / VO)
├── rd-agent-be-domain/             ← 领域层(聚合根 / Repository / Factory / Gateway 接口)
├── rd-agent-be-infra/              ← 基础设施层(仓储/网关实现 / Entity / Mapper / 外部 HTTP)
├── rd-agent-be-application/        ← 应用层(用例编排 / @Transactional 事务边界)
└── rd-agent-be-adapter/            ← 入站适配层(Controller / listener / config / 启动类)
```

业务领域(跨层同名子包):`auth` / `session` / `skill` / `agent` / `evaluation` / `debugconsole`。
依赖方向硬约束:`adapter → application → {client, domain, infra}` ; `infra → domain → facade` 。详见 [ARCHITECTURE.md §3](ARCHITECTURE.md)。

## 3. 核心约束(不可违反)

1. **Control Plane 角色边界**:本服务**不执行 agent**,所有 sphere 相关业务都通过透传或拉取协议转给 sphere → [ARCHITECTURE.md](ARCHITECTURE.md)
2. **六层依赖方向**:严格按 `adapter → application → {client, domain, infra}` ; `infra → domain → facade`,**禁止**反向或跨层越界 → [ARCHITECTURE.md §3](ARCHITECTURE.md) / [CLAUDE.md §0.2](CLAUDE.md)
3. **数据隔离**:独立 PG schema `rd_agent`,与 platform schema 物理隔离 → [PRD-MVP §9 (rd-agent-be 数据库决议)](../docs/agent-sphere/PRD-MVP.md#9-风险与待决问题)
4. **降级保证**:`/admin/api/v1/internal/prompts/active` 接口的设计必须保证 platform 在 be 全宕时**业务不中断**(走代码默认 prompt) → [docs/RELIABILITY.md](docs/RELIABILITY.md)
5. **审计完整性**:所有写操作必须落审计日志(字段定义见 PRD-MVP §5.4 + workspace CONVENTIONS §6)
6. **单元测试强制**:任何业务代码新增或修改必须同步编写/更新单元测试,测试通过后才算任务完成
7. **质量闭环**:每次写代码后必须执行验证命令(见第 6 节),失败自我修正
8. **注释完整**:所有类、属性、方法都必须有 JavaDoc(中文,聚焦语义/约束) → [docs/CODING-CONVENTIONS.md §1](docs/CODING-CONVENTIONS.md#1-注释规范)
9. **分支安全：禁止直接修改环境分支**:绝对不允许直接在 `master`、`test`、`stag`、`prod` 等环境分支上修改代码。所有代码变更必须先在 `feature-*`、`hotfix-*` 等开发分支上完成，然后根据用户的明确指令再合并到目标环境分支。

## 4. 项目 Skill / Slash Command(按场景调用)

(暂无项目级命令)

落码时按场景调用全局 skills:
- 实现新功能 → `impl-from-technical-solution`(按方案落地,自动分发到 impl-{facade,client,domain,infra,application,adapter}-module)
- 引入三方包 → `impl-third-party-sdk`(查 7.4 清单,根 pom + 模块 pom)
- 单层重构 → 直接调对应层 `impl-<layer>-module`

## 5. Lookup Table — 想知道 X,去看 Y

| 想了解 | 去看 |
|-------|------|
| **六层落码规范 / 代码模板 / 违规速查** | [CLAUDE.md](CLAUDE.md) |
| **顶层架构 / 分层规则 / 依赖方向 / 强制手段** | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **合规审计报告（架构优化分支）** | [docs/质量检查/](docs/质量检查/) |
| 编码规范(注释 / 命名 / 风格 / 工具库选型) | [docs/CODING-CONVENTIONS.md](docs/CODING-CONVENTIONS.md) |
| 设计总章与设计决策档案 | [docs/DESIGN.md](docs/DESIGN.md) → [docs/design-docs/](docs/design-docs/) |
| 产品规格 / 业务需求 | [docs/product-specs/](docs/product-specs/)(原始 PRD 在 `../docs/agent-sphere/PRD-MVP.md`) |
| 当前进行中的执行计划 | [docs/PLANS.md](docs/PLANS.md) → [docs/exec-plans/](docs/exec-plans/) |
| 数据库 schema(自动生成) | [docs/generated/](docs/generated/) |
| 质量评分卡 | [docs/QUALITY_SCORE.md](docs/QUALITY_SCORE.md) |
| 可靠性 / SLO / 错误处理 / 降级矩阵 | [docs/RELIABILITY.md](docs/RELIABILITY.md) |
| 安全约束(GT OAuth / RBAC / 审计 / NetworkPolicy) | [docs/SECURITY.md](docs/SECURITY.md) |
| 产品品味与取舍偏好 | [docs/PRODUCT_SENSE.md](docs/PRODUCT_SENSE.md) |
| 部署原则 | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| 前端对接约定 | [docs/FRONTEND.md](docs/FRONTEND.md) |
| 外部依赖文档(LLM 友好版) | [docs/references/](docs/references/) |
| 业界对照(可借鉴的 Control Plane 设计) | [docs/references/](docs/references/) — Dify console-api / LangGraph Server / K8s API Server |

## 6. 验证闭环

```bash
mvn -DskipTests clean package          # 编译 + 装配六层
mvn test                               # 单测(含 jacoco 覆盖率报告);必须全绿
# mvn spotless:check                   # 风格(TODO: 架构优化分支落地后启用)
# mvn -pl rd-agent-be-adapter spring-boot:run   # 本地起服务
```

**必须**做到:写完代码 → 执行上述命令 → 全绿才算完成。失败自我修正,不要把脏代码留给下一轮。

ArchUnit 守卫规则清单见 [ARCHITECTURE.md §5](ARCHITECTURE.md)。

## 7. 元规则(Agent 工作准则)

- **写代码前必须先输出实现方案**(除非用户明确说"直接写"或单行 typo 修复)
- **Repository = System of Record**:知识必须 in-repo、versioned,Slack/钉钉/口头约定**对 agent 不可见**,遇到时立即沉淀进 `docs/`
- **进度持久化**:复杂任务必须在 `docs/exec-plans/active/` 创建一份 plan,进度同步更新
- **遇到坏味道立即修**:不要复制现有的 anti-pattern;发现 lint/规范缺口,反馈到 `docs/` 而不是绕开
- **PRD 是只读源**:本仓库不复制 PRD,所有产品需求引用 `../docs/agent-sphere/PRD-MVP.md`
- **变更必带测试**:任何业务代码新增或修改必须同步编写/更新单元测试,`mvn test` 全绿才算完成;测试缺位的不算完成

## 8. 额外说明

- 本服务依赖外部基础设施:SkyWalking(链路)、Prometheus、Sentry；配置一律 `application*.yml`（无 Apollo）。SSO/GCAC 已移除
- 与 rd-points-platform **共用 PG 集群**(独立 schema `rd_agent`)与 **Redis 集群**(独立 key 前缀)— 见 memory 中相关条目
- K8s 上必须配置 NetworkPolicy 限制只允许特定 pod 访问 sphere(见 memory:Sphere 部署必须配 NetworkPolicy)

---

**这份文件应保持在 ~120 行以内。** 任何具体规则的扩展都应放进 `docs/` 对应文件,本文件只更新指针。

> 本骨架由 [`harness-init`](https://openai.com/index/harness-engineering/) skill 于 2026-05-11 生成。
