# rd-agent-be-application 合规审计 — 2026-05-26

## 摘要

- **总文件数**：27 个 Java 文件（13 个 CommandService、5 个 QueryService、3 个 Runner、其他辅助类）
- **总违规数**：18 处
- **P0 硬约束违规**：1 处（QueryService 错加 `@Transactional`，破坏读写分离事务边界）
- **P1 强约束违规**：11 处（3 处类级 JavaDoc 缺失 + 8 处偏离规范，包括 Agent 子包结构、子领域命名约束、`AgentCommandService.@Transactional` 缺 `rollbackFor`、QueryService 注入 Repository 等）
- **P2 一致性问题**：6 处（包结构 / 命名 / 辅助类位置）

---

## 模块全景

### application 目录树（深 3 层）

```
rd-agent-be-application/src/main/java/ink/garry/rd/agent/ws/application/
├── agent/                                          # Agent 领域（规范 §5.4 应有 5 子目录）
│   ├── AgentCommandService.java                    ✓ 类级 JavaDoc / ⚠️ 8 处 @Transactional 缺 rollbackFor
│   ├── AgentQueryService.java                      ✓ 类级 JavaDoc / ⚠️ 注入 A2aSyncHistoryRepository（应改 Gateway）
│   ├── AgentInvokeService.java                     ✓
│   └── strategy/                                   ⚠️ §5.4 5 子目录里无 strategy（属偏离，但 §5.4 应取代）
│       ├── AgentRunner.java                        ✓
│       ├── AgentRunnerRegistry.java                ✓
│       ├── A2aAgentRunner.java                     ✓
│       └── ConfigAgentRunner.java                  ✓（依赖 infra.agent.* 类，按 §5.1 允许）
│
├── auth/                                           # auth 领域（DDD 弱：无 domain 聚合）
│   ├── AuthProperties.java                         ✓ @ConfigurationProperties
│   └── （原外部登录 Service / UserUpsert / OAuth 相关类已删除）
│
├── debugconsole/                                   # 调试台领域（已知例外：无 domain 层）
│   └── DebugInvokeStreamService.java               ❌ 缺类级 JavaDoc / ⚠️ 命名非 *CommandService
│
├── evaluation/                                     # 评测领域
│   ├── EvalCommandService.java                     ✓
│   ├── EvalQueryService.java                       ✓
│   ├── EvalSeedCommandService.java                 ✓
│   ├── EvalWorker.java                             ✓ @Component / @Async（命名非 Service，但职能为 Worker，可接受）
│   ├── casegen/
│   │   ├── CaseGenerator.java                      ✓ 接口
│   │   └── SeedCaseGenerator.java                  ✓ @Service 实现
│   └── judge/
│       ├── JudgeService.java                       ✓ 接口
│       └── KeywordJudgeServiceImpl.java            ✓ @Service 实现
│
├── session/                                        # 会话领域
│   ├── SessionCommandService.java                  ❌ 缺类级 JavaDoc / ⚠️ 所有 public 方法均缺 JavaDoc
│   └── SessionQueryService.java                    ❌ 缺类级 JavaDoc / ⚠️ 所有 public 方法均缺 JavaDoc
│
└── skill/                                          # 技能领域
    ├── SkillCommandService.java                    ✓
    ├── SkillQueryService.java                      ✓
    ├── SkillSyncCommandService.java                ✓ TODO 占位骨架
    └── SkillVersionCommandService.java             ✓
```

### 各领域文件统计

| 领域 | CommandService | QueryService | 其他 | 子目录 | 合计 | 备注 |
|---|---|---|---|---|---|---|
| `agent` | AgentCommandService、AgentInvokeService | AgentQueryService | strategy/ 4 个 | strategy | 7 | 命名为 InvokeService，超出 §5.3 模板（属 §5.4 编排） |
| `auth` | AuthProperties（其余外部登录 / UserUpsert 相关已删除） | — | AuthProperties | — | 1 | 历史登录编排类已拆除 |
| `debugconsole` | DebugInvokeStreamService（命名偏离） | — | — | — | 1 | 已知无 domain 层例外 |
| `evaluation` | EvalCommandService、EvalSeedCommandService | EvalQueryService | EvalWorker、CaseGenerator/Judge 接口和实现 | casegen、judge | 8 | 多 Worker / Service 二级拆分（合规） |
| `session` | SessionCommandService | SessionQueryService | — | — | 2 | 严格双 Service 模板 |
| `skill` | SkillCommandService、SkillSyncCommandService、SkillVersionCommandService | SkillQueryService | — | — | 4 | 多 Command 拆分（合规） |
| **合计** | **8** | **5** | **14** | **5** | **27** | |

---

## P0 违规（必须修）

### ❌ 1. QueryService 错加 `@Transactional` —— 破坏读写分离事务边界（§5.5 规则 3）

**位置**：`debugconsole/DebugInvokeStreamService.java:61` 与 `:145`

```java
@Transactional
protected Session prepareSession(DebugInvokeRequest req, String operatorId, String traceId) { ... }

@Transactional
protected void saveResult(Session session, DebugInvokeRequest req, InvocationState state,
                          InvocationStatus status, String operatorId) { ... }
```

**违规说明**：
- 规范 §5.5 规则 3 明确："**QueryService 应无 @Transactional**；CommandService 加 `@Transactional(rollbackFor = Exception.class)`"。
- 虽然 `DebugInvokeStreamService` 名称是 Stream，但当前职责合并了读写（创建会话 + 保存 invocation）。`prepareSession` 是写操作（追加用户消息），`saveResult` 也是写操作（保存 assistant 消息 + InvocationTrace）。这两个 `@Transactional` 加在 `protected` 方法上**不会生效**（Spring AOP 默认只代理 public 方法，且本类内部自调用更不会走代理）。
- 同时整套调用走 Reactor `Flux`（响应式编排），事务最早会在 `doOnError` / `doOnComplete` 触发，与主线程事务上下文已不在同一栈，`@Transactional` 实际形同虚设。

**修复方案**：
1. 把 `prepareSession` / `saveResult` 提到 `SessionCommandService` 上、改为 `public`，由 `SessionCommandService` 统一持有 `@Transactional(rollbackFor = Exception.class)`；
2. 或把流程拆为：先调 SessionCommandService 在响应式链外完成 prepare → 在 Flux 末尾再调 SessionCommandService 的 finalize 方法保存结果；
3. 删除当前 `DebugInvokeStreamService` 中错位的 `@Transactional` 注解，避免误导后续维护者。

---

## P1 违规（强烈建议修）

### ⚠️ 1. `AgentCommandService` 所有 `@Transactional` 缺 `rollbackFor`（§5.5 规则 3）

**位置**：`agent/AgentCommandService.java` 第 83 / 118 / 161 / 190 / 216 / 268 / 282 / 305 行（共 8 处）

```java
@Transactional
public String create(AgentCreateParam param, String operatorId) { ... }
```

**违规说明**：规范 §5.5 规则 3 要求 CommandService 方法上加 `@Transactional(rollbackFor = Exception.class)`。Spring 默认事务规则只在 `RuntimeException` / `Error` 时回滚；漏写 `rollbackFor = Exception.class` 会导致 `IOException`、checked Exception 抛出时事务**不回滚**，造成数据不一致。

**修复方案**：将该文件 8 处 `@Transactional` 全部改为 `@Transactional(rollbackFor = Exception.class)`。`SessionCommandService` 同样存在 4 处（第 27 / 32 / 62 / 68 行），需一并修复。

---

### ⚠️ 2. QueryService 直接注入 domain Repository（§5.5 规则 2）

**位置**：`agent/AgentQueryService.java:70`

```java
/** v2.6：A2A 同步历史仓储 */
private final A2aSyncHistoryRepository a2aSyncHistoryRepository;
```

```java
List<A2aSyncHistory> rows = a2aSyncHistoryRepository.listByAgentNum(agentNum, safeLimit);
```

**违规说明**：
- 规范 §5.5 规则 2："QueryService **注入 infra Mapper 或 domain Repository** 做只读查询"。表面看注入 Repository 是合法的；
- **但** `Repository` 接口按 §3.5 硬约束只能有 `save / findByNum / deleteByNum` 三方法。当前 `A2aSyncHistoryRepository` 含 `listByAgentNum`（这已经在 domain 审计报告中标为 P0 违规）。QueryService 这边等于强化了那条 P0 违规：要么 domain 把 `listByAgentNum` 移到 `gateway/A2aSyncHistoryReadGateway`，application 这里改注入 Gateway；要么沿用既有违规。
- **建议路径**：在 domain `agent/gateway/` 下新增 `A2aSyncHistoryReadGateway`（含 `listByAgentNum`），AgentQueryService 改注入 Gateway，与本仓库 `AgentReadGateway` / `AgentVersionReadGateway` 命名一致。

---

### ⚠️ 3. 3 处类级 JavaDoc 缺失（§0.4 / §5 项目硬约束）

| 文件 | 类型 | 影响 |
|---|---|---|
| `session/SessionCommandService.java` | CommandService | 写用例核心入口，JavaDoc 缺失 |
| `session/SessionQueryService.java` | QueryService | 读用例核心入口 |
| `debugconsole/DebugInvokeStreamService.java` | StreamService | 调试台编排核心 |

且这 3 个文件的 **public/protected 方法 100% 缺方法级 JavaDoc**：
- `SessionCommandService`：5 个方法（`createSession ×2 / rename / delete / toVO`）均无 JavaDoc。
- `SessionQueryService`：3 个方法（`pageList / detail / listMessages`）均无 JavaDoc。
- `DebugInvokeStreamService`：3 个方法（`invoke / prepareSession / saveResult`）均无 JavaDoc。

**违规说明**：rd-agent-be 项目硬约束（§0.4）要求所有类 / 属性 / 方法都必须有 JavaDoc。

---

### ⚠️ 4. `auth` 与 `debugconsole` 领域命名偏离 §5.3

| 文件 | 当前命名 | §5.3 期望 |
|---|---|---|
| （已删除）外部登录 / UserUpsert 相关服务 | — | 已随 SSO 拆除，本项不再适用 |
| `debugconsole/DebugInvokeStreamService.java` | `DebugInvokeStreamService` | `DebugCommandService` / `DebugStreamCommandService` |

**违规说明**：规范 §5.3 要求每个领域只有 `*CommandService.java` + `*QueryService.java`。当前 auth 与 debugconsole 命名以业务动词为主，未带 `Command/Query` 后缀，不便后续按类名理解事务边界。

**修复方案（建议方向）**：
- 短期：保留现有命名（兼顾 git 历史），但在类级 JavaDoc 中补一行"承担 CommandService 职责"；
- 长期：在下个 milestone 把命名收敛到规范（`AuthCommandService` / `DebugCommandService`）。

---

### ⚠️ 5. `auth/impl/` 子目录偏离 §5.2 / §5.3

**位置**：`auth/impl/ConfigDrivenUserUpsertService.java`

**违规说明**：规范 §5.2 给出的 application 子包是 `<领域名>/`（直接放 *CommandService.java / *QueryService.java），未授权 `impl/`。当前 `auth/impl/` 是接口实现拆分模式，与 §5.3 模板不一致。

**修复方案**：把 `ConfigDrivenUserUpsertService.java` 移到 `auth/` 下与 `UserUpsertService` 接口同包，或把 `UserUpsertService` / `UpsertedUser` 接口本身迁到 facade（业务无关 DTO）；移除 `impl/` 子目录。

---

### ⚠️ 6. `auth/UpsertedUser` 位置偏离（应放 client 或 facade）

**位置**：`auth/UpsertedUser.java`

**违规说明**：`UpsertedUser` 是数据载体（DTO），用 `@Data + @Builder`。按规范 §2 应在 `client` 层，或若需 facade 层共享则在 `facade/` 下。当前放在 application 内部，相当于 application 自定 DTO。

**修复方案**：迁至 `rd-agent-be-client/.../client/auth/UpsertedUserDTO.java`。

---

### ⚠️ 7. `application/agent/` 子目录不符 §5.4 Agent 领域五子目录

**当前**：`agent/strategy/`（4 个 Runner 文件）
**§5.4 规范**：`agent/config/` / `agent/hook/` / `agent/interceptor/` / `agent/service/` / `agent/tool/`

**违规说明**：规范 §5.4 明确 Agent 领域应有 5 个子目录。当前 `application/agent/` 实际只有 `strategy/` 一个子目录 + 3 个 Service。这反映**当前项目的 Agent 编排策略与 §5.4 模板形态不同**：本项目使用「Runner 策略派发 + Spring AI Alibaba 黑盒」，没有显式的 hook / interceptor / tool 抽象层。

**修复方案**：
- 选项 A（接受偏离）：在 CLAUDE.md 或 ARCHITECTURE.md 注明"本项目 Agent 编排走 Runner 策略，不采用 §5.4 五子目录"；
- 选项 B（向规范靠拢）：把 `strategy/` 改名为 `service/`，未来 hook/interceptor/tool 出现时按 §5.4 分别拆出。

---

### ⚠️ 8. application 对 infra 内部类的直接耦合（§5.1 边界）

**位置**：
- `agent/strategy/ConfigAgentRunner.java` 注入 `infra.agent.builder.ConfigAgentBuilder` / `infra.agent.cache.AgentInstanceCache`；
- （已删除）原外部登录服务曾注入 `AuthAuditHelper` / 外部认证配置 / `OAuthStateStore`。

**违规说明**：
- 规范 §5.1 允许 application 依赖 infra，但建议**通过 domain Gateway 接口**间接依赖，避免 application 直接知晓 infra 内部包结构。
- 当前两处直接 import infra 的具体类（非 Gateway 接口），耦合较紧；将来 infra 重构（包名变化、类拆分）时 application 会被迫改动。
- 这一条按规范严格度只是 P2 偏离（§5.1 字面允许），但鉴于 §5.5 的 OOP 原则与 §0.4「在现有基础上扩展」精神，建议视为 P1 改善项。

**修复方案**：
- `ConfigAgentBuilder` / `AgentInstanceCache` 应在 domain `agent/gateway/` 下定义抽象接口（如 `AgentInstanceCacheGateway`），infra 实现；
- `AuthAuditHelper` 若仍保留，可把抽象上提到 facade；`OAuthStateStore` 已删除。

---

## P2 一致性问题

### 1. `EvalWorker` 命名不符 §5.3 双 Service 模板

`evaluation/EvalWorker.java` 是 `@Component @Async` 标注的异步 Worker，承载评测异步执行。规范 §5.3 只列了 `*CommandService` / `*QueryService`。`Worker` 后缀虽符合行业惯例，但严格按规范应拆为 `EvalCommandService.runManual / runAuto` 内部委派或命名为 `EvalAsyncService`。

---

### 2. `casegen/` 与 `judge/` 二级子包符合规范但缺索引说明

`evaluation/casegen/`、`evaluation/judge/` 是 evaluation 领域内的「策略接口 + 实现」拆分。规范 §5.3 没有显式禁止，但也未授权领域内二级子包。建议在 `evaluation/` 包级 `package-info.java` 中说明这两个子包的存在与目的。

---

### 3. （已删除）原外部登录服务曾内嵌 `AuthPropertiesConfig`

```java
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
static class AuthPropertiesConfig {
}
```

把 `@Configuration` 类嵌到 Service 内不规范；应单独拎到 `auth/AuthConfig.java` 或 adapter 的 `config/` 中（启用 properties 一般在 adapter 全局 config）。

---

### 4. 子领域内多 CommandService 拆分（属合理偏离）

`skill` 领域有 3 个 CommandService（`SkillCommandService` / `SkillVersionCommandService` / `SkillSyncCommandService`）。规范 §5.3 字面是「只有 `*CommandService` 单数」，但实际复杂领域常按子聚合 / 子用例拆。本项目这种拆分**更利于可读性**，建议在 ARCHITECTURE.md 中显式补一条「单领域允许按子聚合 / 子用例拆多个 CommandService」。

---

### 5. `AgentInvokeService` 命名不符 §5.3 但承担运行时调用

`agent/AgentInvokeService.java` 既非 `*CommandService` 也非 `*QueryService`。其职责是「按 Agent 类型派发到 Runner」，更接近一个流式编排服务，按 §5.4 应在 `agent/service/` 下命名为 `AgentInvokeService`。当前位置在 `agent/` 根目录，与规范偏离。

---

### 6. `EvalWorker` 与 `EvalCommandService` 形成自调用闭环

`EvalCommandService.createManual` / `createAuto` 调 `EvalWorker.runManual` / `runAuto`，而 `EvalWorker.runAuto` 标 `@Async`。同进程内通过 Spring Bean 调度 `@Async` 方法是规范用法，但 `EvalCommandService.createAuto` 在 `@Transactional` 内调 `EvalWorker.runAuto` 可能导致事务尚未提交时 worker 已读不到 evaluation 行（注释 §65 已提示）。

**建议**：把 `EvalWorker.runAuto` 调用放到 `@TransactionalEventListener(phase = AFTER_COMMIT)` 中，确保事务真提交后再异步触发。

---

## 合规亮点

1. **依赖方向 100% 合规**：grep 全模块 27 个文件，零 `import ink.garry.rd.agent.ws.adapter.*` / 零 `HttpServletRequest` / 零 `SecurityContextHolder` / 零 Servlet API 注入。
2. **operatorId 参数化传递**：所有 Service 均通过方法参数接收 `operatorId`，无 Servlet 上下文耦合。
3. **24/27 类级 JavaDoc 完整**（仅 SessionCommandService / SessionQueryService / DebugInvokeStreamService 三处缺失）。
4. **CommandService 普遍带 `@Transactional`**：除 `SessionCommandService` 与 `AgentCommandService` 缺 `rollbackFor` 外，其余 12 处 `@Transactional` 注解都规范。
5. **QueryService 普遍无 @Transactional**：`AgentQueryService` / `EvalQueryService` / `SkillQueryService` / `SessionQueryService` 全部正确未加事务。
6. **Factory 模式贯彻**：写用例严格走 `xxxFactory.create / createByNum` → 调聚合根方法；application 内无 if/else 领域规则违例。
7. **Repository 直接注入仅 1 处**：`AgentQueryService` 注入 `A2aSyncHistoryRepository`（虽然这是被 domain 那侧违规所迫，但属可控）。
8. **Runner Strategy 注册表合理**：`AgentRunnerRegistry` 用 `@PostConstruct` 做完整性校验，缺失启动失败 —— 防御性编程到位。
9. **`pom.xml` 依赖精简**：application 模块只声明 client / domain / infra / spring-boot-starter / lombok 五项，未引入 spring-web / webflux（依赖透传，但显式声明缺失实际通过 infra 透传 ←- 一种风格倾向）。
10. **多 CommandService 拆分清晰**：skill / evaluation 领域复杂用例按子聚合 / 异步 worker 拆分，可读性强。

---

## 修复优先级与建议

### 第 1 阶段（P0 — 立即修复）

预计工时：3-4 小时

- [ ] 重构 `DebugInvokeStreamService`：删除 `protected` 方法上的 `@Transactional`；把写操作迁到 `SessionCommandService` 公共方法并补 `@Transactional(rollbackFor = Exception.class)`；调用方改为 → SessionCommandService.prepareDebugSession + → SessionCommandService.appendDebugResult。

---

### 第 2 阶段（P1 — 本周修复）

预计工时：8-12 小时

- [ ] `AgentCommandService` 8 处 `@Transactional` 补 `rollbackFor = Exception.class`；
- [ ] `SessionCommandService` 4 处 `@Transactional` 补 `rollbackFor = Exception.class`；
- [ ] 补齐 3 处类级 JavaDoc（`SessionCommandService` / `SessionQueryService` / `DebugInvokeStreamService`）；
- [ ] 补齐 11 处 public/protected 方法 JavaDoc（上述 3 个文件全量）；
- [ ] `AgentQueryService` 改注入 `A2aSyncHistoryReadGateway`（需 domain 先建该 Gateway）；
- [ ] `EvalCommandService.createAuto` 改用 `@TransactionalEventListener(AFTER_COMMIT)` 触发 `EvalWorker.runAuto`。

---

### 第 3 阶段（P2 / 偏离收敛 — 下个迭代）

预计工时：5-8 小时

- [ ] 把 `auth/impl/` 子目录展平到 `auth/`；
- [ ] 把 `UpsertedUser` 迁到 `client/auth/UpsertedUserDTO`；
- [x] （已不适用）原外部登录服务及内嵌 `AuthPropertiesConfig` 已拆除；
- [ ] 决定 `agent/strategy/` 是否改名为 `agent/service/`，并在 ARCHITECTURE.md 注明本项目对 §5.4 Agent 五子目录的实际取舍；
- [ ] 在 `evaluation/package-info.java` 中说明 `casegen/` 与 `judge/` 二级子包的目的；
- [ ] 在 ARCHITECTURE.md 补一条「单领域允许按子聚合 / 子用例拆多个 CommandService」的设计决策。

---

## 检查清单

| 检查项 | 规范 | 结果 | 状态 |
|---|---|---|---|
| 不引用 adapter | §0.2 / §5.1 | ✓ 0 处 | ✓ |
| 不注入 HttpServletRequest / Servlet API | §5.5 规则 4 | ✓ 0 处 | ✓ |
| 不注入 SecurityContextHolder | §5.5 规则 4 | ✓ 0 处 | ✓ |
| 依赖仅 client / domain / infra（含 facade 间接） | §5.1 | ✓ | ✓ |
| 包结构（agent/auth/debugconsole/evaluation/session/skill 6 个领域） | §5.2 | ✓ 6 领域齐 | ✓ |
| Agent 领域 5 子目录（config/hook/interceptor/service/tool） | §5.4 | ⚠️ 实为 strategy/ | ⚠️ P2 |
| CommandService 加 `@Transactional(rollbackFor=Exception.class)` | §5.5 规则 3 | ⚠️ 12/22 处缺 rollbackFor | ⚠️ P1 |
| QueryService 无 `@Transactional` | §5.5 规则 3 | ⚠️ DebugInvokeStreamService 有错位 @Transactional | ❌ P0 |
| 写用例：通过 Factory 加载实体 → 调领域方法 | §5.5 规则 1 | ✓ 100% 走 Factory | ✓ |
| 读用例：注入 infra Mapper 或 domain Repository | §5.5 规则 2 | ✓（注入 ReadGateway 居多，合规） | ✓ |
| application 内无 if/else 领域规则 | §5.5 规则 1 | ✓ 仅幂等校验 / 路由分支 | ✓ |
| 不在 controller / listener 写业务 | §6.6 | N/A（不审 adapter） | — |
| 类级 JavaDoc 完整 | §0.4 / §5 | ⚠️ 3/27 缺失 | ⚠️ P1 |
| 方法级 JavaDoc 完整 | §0.4 / §5 | ⚠️ 11+ 处缺失（集中在 3 个文件） | ⚠️ P1 |
| 包名一致性（`ink.garry.rd.agent.ws.application.*`） | §0.4 | ✓ 100% | ✓ |
| 领域命名与 domain 一致 | §0.4 | ✓（debugconsole 例外可接受） | ✓ |

---

## 总体评分

| 维度 | 评分 | 说明 |
|---|---|---|
| **P0 硬约束** | 90/100 | 仅 1 处 QueryService 错位 @Transactional（实际不生效，但违规明确） |
| **P1 强约束** | 75/100 | `@Transactional` 缺 rollbackFor 12 处 + JavaDoc 缺失 3 文件 + 直接注入 Repository 1 处 |
| **P2 一致性** | 80/100 | Agent 子目录偏离 / auth impl 目录 / UpsertedUser 位置 / 命名偏离 4 处 |
| **依赖方向纯净度** | 100/100 | 零 adapter / 零 Servlet 注入，全模块最高分 |
| **事务边界正确性** | 78/100 | CommandService 普遍带事务但 12 处缺 rollbackFor；DebugInvokeStreamService 错位 |
| **代码组织合理性** | 82/100 | skill / evaluation 多 Service 拆分合理；auth/impl 结构偏离 |
| **JavaDoc 完整度** | 78/100 | 24/27 类级合规，但 3 个核心文件全量缺失 |
| **整体合规** | **82/100** | **可接受，需立即修复 1 处 P0 + 本周补齐 12 处 @Transactional + 3 处 JavaDoc** |

---

## 关键建议

1. **立即修复 P0**：`DebugInvokeStreamService` 中 `protected` 方法的 `@Transactional` 必须移除或重构 —— 当前状态既违规又无效。
2. **批量补 rollbackFor**：`AgentCommandService` 8 处 + `SessionCommandService` 4 处统一改为 `@Transactional(rollbackFor = Exception.class)`，纯文本替换即可。
3. **JavaDoc 集中补齐**：3 个未覆盖文件（Session 双 Service + DebugInvokeStreamService）的类级 + public/protected 方法级 JavaDoc 一次性补完，约 14 处。
4. **A2aSyncHistory 联动 domain 修复**：与 domain 审计中 `A2aSyncHistoryRepository` 三方法约束违规联动 —— 在 domain 拆出 `A2aSyncHistoryReadGateway` 后 application 这边自然合规。
5. **Agent 子目录战略决策**：`agent/strategy/` vs §5.4 五子目录的偏离不是 bug，而是项目实际编排范式与规范模板差异。建议在 ARCHITECTURE.md 中显式记录决策，避免后续维护者按 §5.4 强拆。
6. **登录服务与 ConfigAgentRunner 的 infra 耦合**：外部登录服务 / `OAuthStateStore` 已删除；剩余可将 `AuthAuditHelper` / `ConfigAgentBuilder` / `AgentInstanceCache` 抽象上 domain Gateway。

---

## 附录：建议补的 DomainEventConstant / Gateway 增量

为修复 P1 第 2 项（A2aSyncHistoryRepository 违规）需在 domain 侧新增：

```java
// rd-agent-be-domain/.../domain/agent/gateway/A2aSyncHistoryReadGateway.java
package ink.garry.rd.agent.ws.domain.agent.gateway;

import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import java.util.List;

/** A2A 同步历史读模型网关：按 agentNum 列出最近 N 条同步记录。 */
public interface A2aSyncHistoryReadGateway {
    /**
     * 列出指定 Agent 的最近同步历史。
     * @param agentNum Agent 业务编号
     * @param limit 最大返回条数
     * @return 按 syncedAt DESC 排序的历史列表
     */
    List<A2aSyncHistory> listByAgentNum(String agentNum, int limit);

    /** 清理仅保留最近 N 条之外的旧记录。 */
    void purgeOldest(String agentNum, int keepCount);
}
```

application 侧改为：

```java
// AgentQueryService.java
- private final A2aSyncHistoryRepository a2aSyncHistoryRepository;
+ private final A2aSyncHistoryReadGateway a2aSyncHistoryReadGateway;
- List<A2aSyncHistory> rows = a2aSyncHistoryRepository.listByAgentNum(agentNum, safeLimit);
+ List<A2aSyncHistory> rows = a2aSyncHistoryReadGateway.listByAgentNum(agentNum, safeLimit);
```

---

> **审计完成日期**：2026-05-26
> **审计依据**：rd-agent-be `CLAUDE.md` §5（application 层 Spec）+ §0.2（依赖方向）+ §9（违规速查）
> **下次复审建议**：P0 修复后 1 周 + 完成第 2 阶段 P1 后 1 个月。
