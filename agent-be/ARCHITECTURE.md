# rd-agent-be — Architecture

> 顶层架构、领域划分、依赖规则。
> 这份文件描述**什么允许做、什么不允许做**，**不**描述"怎么实现"。
> 实现规范见 [CLAUDE.md](CLAUDE.md)（六层落码模板与代码示例）。
> 业务入口地图见 [AGENTS.md](AGENTS.md)。

---

## 1. 一图看懂架构

本服务是 Agent Sphere 管理后台的 **Control Plane**，与 rd-points-sphere（Data Plane）分离部署。架构模式参照 Dify console-api/plugin-daemon、LangGraph Server/Workers、Kubernetes API Server/kubelet。

```
┌─────────────────────────────────────────────────────────────────┐
│  rd-agent-fe   /  rd-points-platform  （调用方）                 │
└─────────────┬─────────────────────────────────┬─────────────────┘
              │ Admin API                       │ Internal API
              │ (JWT)                           │ (X-Service-Token)
              ▼                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  rd-agent-be (Control Plane) — 六层 Maven 多模块                 │
│                                                                  │
│   adapter   ◄── HTTP / MQ 入站；启动类 AgentBeApplication        │
│      │                                                           │
│      ▼                                                           │
│   application  ── 用例编排 + 事务边界（@Transactional）          │
│      │                                                           │
│      ├──► client   ── 对外契约（Param / VO / DTO）               │
│      ├──► domain   ── 聚合根 / 仓储接口 / Gateway 接口           │
│      └──► infra    ── 仓储/网关实现 / Entity / Mapper / 外部 HTTP│
│                          │                                       │
│                          └──► domain ──► facade（基础类型与契约） │
│                                                                  │
│  PostgreSQL (schema: rd_agent)                                   │
│   tables: users / roles / sessions / skills / agents /           │
│           evaluations / audit_log …                              │
│  Redis (key prefix: rd_agent:)                                   │
└─────────────────────────────┬───────────────────────────────────┘
                              │ 内网 REST（无服务间鉴权，K8s NetworkPolicy 防护）
                              ▼
                   ┌──────────────────┐
                   │ rd-points-sphere │  (Data Plane，无状态，本服务只透传)
                   └──────────────────┘
```

## 2. 层与领域

### 2.1 六层 Maven 模块（横向）

| 模块 | 定位 | 一句话职责 |
|---|---|---|
| `rd-agent-be-facade` | 契约层（业务无关） | `DomainEntity` / `Result` / `DomainEventDTO` / `DomainEventPublisher` |
| `rd-agent-be-client` | 数据契约层 | 对外 Param / DTO / VO（Result 在 facade） |
| `rd-agent-be-domain` | 领域层 | 聚合根、值对象、Factory / Repository / Gateway **接口** |
| `rd-agent-be-infra` | 基础设施层 | 仓储 / 网关实现、Entity + Mapper、外部 HTTP / SDK |
| `rd-agent-be-application` | 应用层 | 用例编排、`@Transactional` 事务边界 |
| `rd-agent-be-adapter` | 入站适配层 | Controller / listener / 全局 config / 启动类 |

详细代码模板见 [CLAUDE.md](CLAUDE.md) 第 1–6 节。

### 2.2 业务领域（纵向，跨层包名一致）

每个领域在多个层中以**同名子包**出现：

| 领域 | 用途 | 出现的层 |
|---|---|---|
| `auth` | GT OAuth + JWT 鉴权、UserContext | infra / application / adapter |
| `session` | 会话流（Thread）元数据，1:N runs | domain / infra / application / adapter |
| `skill` | Skill / Prompt 版本与激活、diff | domain / infra / application / adapter |
| `agent` | Agent 注册、A2A 协议、Nacos 订阅 | domain / infra / application / adapter |
| `evaluation` | 评测集 / 评测任务 | domain / infra / application / adapter |
| `debugconsole` | 调试台只读 API | application / adapter（无 domain） |

> 跨领域共享：`domain/common`（事件常量）、`infra/common`（外部 HTTP client / 事件 publisher 实现 / 通用工具 / 常量）。

## 3. 依赖方向（不可违反）

### 3.1 模块级

```
adapter ──► application ──► { client, domain, infra }
                                              │
                                              ▼
infra ──► domain ──► facade        ◄────── client（仅依赖通用库与 facade 通用类型）
```

| 关系 | 允许？ |
|---|---|
| `adapter → application` / `adapter → client` / `adapter → facade(Result)` | ✅ |
| `application → { client, domain, infra }` | ✅ |
| `infra → domain → facade` | ✅ |
| `client → facade(通用类型)` | ✅ |
| `domain → 任何 infra/application/adapter/client 业务类型` | ❌ |
| `adapter → domain` / `adapter → infra` 业务类型 | ❌ |
| `client → domain` / `client → infra` | ❌ |
| `facade → 其他任何模块` | ❌ |

### 3.2 同层跨领域

`session` / `skill` / `agent` / `evaluation` 在同一层内**横向独立**，跨领域调用必须走显式接口：
- 跨领域读 → 通过对方 `domain/<x>/gateway/` 接口（实现在 infra）。
- 跨领域写 → 通过 `application` 编排，**禁止**在 domain 实体内直接 `new` 或注入对方聚合的仓储。

## 4. 边界与例外

- **debugconsole 缺 domain 层** — 仅做只读查询/调试，不构成领域聚合；application 直接读 infra Mapper 转 VO。
- **auth 缺 domain 层** — UserContext 与 token 解析属于基础设施关注点，归 infra/auth + application/auth。
- 上述例外**不**作为新功能默认模式；新增聚合若涉及状态/规则，**必须**走六层完整路径（含 domain）。
- 其他任何破例必须在此登记，附**申请理由 + 负责人**。

## 5. 强制手段（机械检查）

> 不变量必须由代码强制，不能只靠 review。

| 规则 | 强制方式 | 配置位置 |
|---|---|---|
| 模块依赖方向（六层） | ArchUnit 测试 | `rd-agent-be-adapter/src/test/java/.../ArchitectureTest.java` <!-- TODO: 架构优化分支落地 --> |
| domain 不引入 MyBatis / Spring / Redis 注解 | ArchUnit `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.mybatis..", "org.springframework..", "org.redisson..")` | 同上 |
| Repository 接口仅 3 方法（`save` / `findByNum` / `deleteByNum`） | ArchUnit `methods().that().areDeclaredInClassesThat().resideInAPackage("..domain..repository..")` 名称白名单 | 同上 |
| RepositoryImpl 仅注入本聚合 Mapper | ArchUnit 字段类型限制 | 同上 |
| 业务领域包下禁出现 `query/` / `dto/` / `service/` | ArchUnit 包结构检查 | 同上 |
| 启动类位于 adapter 包根 | ArchUnit `classes().that().areAnnotatedWith(@SpringBootApplication).should().resideInAPackage("..adapter")` | 同上 |
| 类 / 方法 / 属性 JavaDoc 完整性 | Checkstyle `JavadocType` / `JavadocMethod` / `JavadocVariable` | `pom.xml` <!-- TODO: 架构优化分支落地 --> |
| 命名规范（camelCase / snake_case） | Spotless | `pom.xml` <!-- TODO --> |
| API 响应格式统一（`Result<T>`） | 全局 `@RestControllerAdvice` + AOP | `adapter/config/GlobalExceptionHandler` |
| Internal API ≠ Admin API | URL 前缀 `/admin/api/v1/internal/*` 单独 SecurityFilter | `adapter/security/*` |

## 6. 横切关注点（Provider / Gateway）

> 横切关注点必须通过显式接口进入业务层，禁止业务层直接依赖具体实现。在六层结构下，这些接口的"摆放位置"是：

| 关注点 | 接口位置 | 实现位置 | 调用方 |
|---|---|---|---|
| 调用 sphere（透传） | `domain/agent/gateway/SphereGateway`（或聚合内 `gateway/`） | `infra/agent/gateway/SphereGatewayImpl` + `infra/common/client/SphereHttpClient` | application 编排或 domain 内 |
| 写审计日志 | `facade/domain/DomainEventPublisher` → `infra/common/event/CommonDomainEventPublisher` → adapter `@EventListener` 持久化 | infra | domain 实体六步顺序第 6 步 |
| 当前用户上下文 | `infra/auth/UserContextHolder`（静态访问器）或 `adapter/config/BaseController#getCurrentUserId()` | infra/auth + adapter/config | adapter Controller 取出后传给 application |
| 配置（YAML） | Spring `@Value` / `ConfigurationProperties` 注入 `application*.yml` | adapter resources | 不用 Apollo；敏感项用环境变量覆盖 |
| 外部 HTTP 调用 | `infra/common/client/<X>Client` 接口 | `infra/common/client/<X>ClientImpl` | infra 各 gateway 实现 |

**禁止**：
- 业务层（domain / application）直接 `new HttpClient` / `RestTemplate` / `OkHttpClient`。
- domain 内访问 `SecurityContextHolder` / `HttpServletRequest`。
- adapter 跳过 application 直接调 infra。

## 7. 演进与例外申请

- 修改本文件之前必须经过 review。
- 新增「例外」必须在第 4 节登记，并附上申请理由和负责人。
- 当强制手段（lint / test）和文档冲突时，**强制手段优先** —— 文档要立即更新。
- 落码模板若与本文件冲突（如 [CLAUDE.md](CLAUDE.md) 中的代码示例）：**本文件优先**，CLAUDE.md 须立即更新模板。

---

## 参考

- [CLAUDE.md](CLAUDE.md) — 六层落码规范（代码模板、违规速查表）
- [AGENTS.md](AGENTS.md) — Agent 入口地图（业务导航 Lookup Table）
- [PRD-MVP §2 产品边界](docs/agent-sphere/PRD-MVP.md#2-产品边界) — Control Plane / Data Plane 责任表
- [matklad: ARCHITECTURE.md](https://matklad.github.io/2021/02/06/ARCHITECTURE.md.html) — 本文件写法源自这篇文章
- [Parse, don't validate](https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/) — 边界处解析数据形状
- 业界对照：Dify console-api · LangGraph Server · Kubernetes API Server
