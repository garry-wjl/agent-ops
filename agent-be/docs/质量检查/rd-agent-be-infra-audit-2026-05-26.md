# rd-agent-be-infra 合规审计 — 2026-05-26

> **§4.7 反转决议（2026-05-28）**
>
> 本次审计 §4.7 项原结论为「FactoryImpl 不得注入 DomainEventPublisher，应由 application 层通过 wire helper 装配」，对应 P1 修复项把 7 个 FactoryImpl 的 Publisher 注入全部移除，并要求 5 个 CommandService + 2 个 Worker 各自维护一份 `wire(Object entity)` helper 来补齐。
>
> **撤销原因**：
> 1. **重复样板**：8 处 application 类各自重复一份 `wire(Object)` helper（行为完全一致），违反 DRY；
> 2. **易漏极易裂**：从 factory 拿到聚合后必须记得调 `wire()`，否则 `xxx.save()` 触发的事件发布要么 NPE（Agent / Skill / Evaluation），要么静默丢失（Session / Message 有 null 兜底）—— 心智成本远高于把 Publisher 留在 factory 一并装配；
> 3. **依赖方向并未受益**：infra 已经间接依赖 facade（domain → facade），将 `DomainEventPublisher` 注入 FactoryImpl 不违反任何分层约束。
>
> **现行规则（替代 §4.7）**：FactoryImpl 装配聚合的全部基础设施依赖（Repository / Gateway / Publisher 等），工厂返回即「完整装配的聚合」；application / worker 不持有 `DomainEventPublisher`，不写 `wire(Object)` helper。
>
> 受影响范围：7 个 FactoryImpl（Session/Agent/AgentVersion/Skill/SkillVersion/Evaluation/InvocationTrace）+ 7 个 application 类（SessionCommandService / AgentCommandService / SkillVersionCommandService / EvalCommandService / EvalSeedCommandService / EvalWorker / DebugInvokeStreamService）；SkillDraft / EvalSeed / EvaluationCase 因聚合不实现 `PublisherAware` 不受影响。
>
> 本审计报告其余 P0 / P1 / P2 结论保持有效。

---

## 摘要
- **总文件数**：99 个 Java 文件
- **总违规数**：7 处
- **P0 违规数**：1 处（Repository 接口超规）
- **P1 违规数**：5 处（Factory 注入 Publisher、@Transactional 在 infra）
- **P2 一致性问题**：1 处（common 缺子包）

---

## 模块全景

### infra 目录树（深 3 层）
```
rd-agent-be-infra/src/main/java/ink/garry/rd/agent/ws/infra/
├── common/（缺 constant / exception 子包）
│   ├── client/（良好：按外部系统分类，如 llm）
│   │   └── llm/ (impl / param / dto)
│   ├── event/（✓ CommonDomainEventPublisher）
│   └── util/（✓ Snowflake 等）
├── agent/（违规：包含 builder / cache / nacos）
│   ├── entity/ (✓ AgentEntity 3 个)
│   ├── mapper/ (✓ 3 个)
│   ├── repository/ (✓ 3 个 Impl)
│   ├── factory/ (✓ 3 个 Impl)
│   ├── gateway/ (✓ 5 个 Impl)
│   ├── builder/ ⚠️ 越权
│   ├── cache/ ⚠️ 越权
│   └── nacos/ ⚠️ 越权
├── auth/（特例：无 domain）
│   ├── token / audit（主要配置类；原外部登录 / OAuth state 已删除）
│   └── AuthInfraConfig
├── skill/（✓ 标准 5 子目录）
│   ├── entity/ (3 个)
│   ├── mapper/ (3 个)
│   ├── repository/ (3 个 Impl)
│   ├── factory/ (3 个 Impl)
│   └── gateway/ (4 个 Impl)
├── session/（✓ 标准 5 子目录）
│   ├── entity / mapper / repository / factory / gateway
│   └── 规范度最高
└── evaluation/（✓ 标准 5 子目录）
    ├── entity / mapper / repository / factory / gateway
    └── 规范度最高
```

### 聚合统计

| 聚合 | Entity | Mapper | Repository | Factory | Gateway | 状态 |
|---|---|---|---|---|---|---|
| agent | 3 | 3 | 3 | 3 | 5 | ⚠️ 越权子目录 |
| skill | 3 | 3 | 3 | 3 | 4 | ✓ |
| session | 1 | 1 | 1 | 1 | 1 | ✓ |
| evaluation | 1 | 1 | 1 | 1 | 1 | ✓ |
| auth | - | - | - | - | - | 特例（基础设施） |
| common | - | - | - | - | - | ⚠️ 缺子包 |

---

## P0 违规（必须修）

### 1. AgentRepository 接口超规范（多 1 个方法）
**位置**：`rd-agent-be-domain/src/main/java/ink/garry/rd/agent/ws/domain/agent/repository/AgentRepository.java:1-43`

**违规规则**：CLAUDE.md §3.5（Repository 硬约束：仅 3 方法）

**现状**：
```java
public interface AgentRepository {
    void save(Agent aggregate);
    Agent findByNum(String num);
    Agent findByNacosServiceKey(String nacosServiceKey);  // ← 第 4 个方法，违规
    void deleteByNum(String num);
}
```

**问题**：`findByNacosServiceKey(String)` 是额外查询方法，应当移至 `domain/agent/gateway/` 或 `AgentReadGateway`。

**应改成**：
```java
public interface AgentRepository {
    void save(Agent aggregate);
    Agent findByNum(String num);
    void deleteByNum(String num);
}
```
移 `findByNacosServiceKey` 至 `AgentReadGateway` 或新建 `AgentA2aGateway`。

---

## P1 违规（强烈建议修）

### 2. AgentFactoryImpl 注入 DomainEventPublisher
**位置**：`infra/agent/factory/AgentFactoryImpl.java:20-29`

**违规规则**：CLAUDE.md §4.7（FactoryImpl 仅注入 Repository）

**现状**：
```java
@Component
@RequiredArgsConstructor
public class AgentFactoryImpl implements AgentFactory {
    private final AgentRepository agentRepository;
    private final AgentNumGateway agentNumGateway;
    private final DomainEventPublisher domainEventPublisher;  // ← 违规
```

**问题**：Factory 用于对象装配，Publisher 应在 application 层或由 domain 实体内部发布。

**应改成**：仅注入 Repository；Publisher 由调用方（application）负责在获得实体后装配或在 domain 方法内调用。

---

### 3. SkillFactoryImpl 注入 DomainEventPublisher
**位置**：`infra/skill/factory/SkillFactoryImpl.java:22-27`

**违规规则**：同上

**现状**：
```java
private final SkillRepository skillRepository;
private final SkillNumGateway skillNumGateway;
private final DomainEventPublisher domainEventPublisher;  // ← 违规
```

**应改**：移除该注入。

---

### 4. SkillVersionReadGatewayImpl 方法上有 @Transactional
**位置**：`infra/skill/gateway/SkillVersionReadGatewayImpl.java` (方法级)

**违规规则**：CLAUDE.md §4.6（事务声明在 application，不在 infra）

**应改**：移除 `@Transactional`；事务由 application 层的 Service 方法声明。

---

### 5. AgentVersionReadGatewayImpl 方法上有 @Transactional
**位置**：`infra/agent/gateway/AgentVersionReadGatewayImpl.java` (方法级)

**违规规则**：同上

**应改**：移除 `@Transactional`。

---

## P2 一致性问题

### 6. common 子包缺少 constant 和 exception 子目录
**位置**：`infra/common/`

**违规规则**：CLAUDE.md §4.2（common 应包含 constant / event / exception / util / client）

**现状**：
```
infra/common/
├── client/     ✓
├── event/      ✓
├── util/       ✓
├── constant/   ✗ 缺失
└── exception/  ✗ 缺失
```

**建议**：
1. 新建 `infra/common/constant/` 用于跨领域常量（如 `DeleteFlagConstant`，目前若有分散在各聚合）
2. 新建 `infra/common/exception/` 用于基础设施异常（如 `LockException`）
3. 若已存在于其他位置，迁移至 common

---

### 7. agent 聚合包包结构非标准（越权子目录）
**位置**：`infra/agent/`

**违规规则**：CLAUDE.md §4.2（业务聚合包下仅允许 entity / mapper / repository / factory / gateway 5 个子目录）

**现状**：
```
infra/agent/
├── builder/    ⚠️ 越权（Agent 组装逻辑应在 application）
├── cache/      ⚠️ 越权（缓存策略应在 application/gateway）
├── nacos/      ⚠️ 越权（Nacos 集成属于基础设施细节）
├── entity/     ✓
├── mapper/     ✓
├── repository/ ✓
├── factory/    ✓
└── gateway/    ✓
```

**应改**：
- `builder/` 内容（OpenAiChatModelFactory / ConfigAgentBuilder）→ 移至 `infra/common/builder/` 或 `application/agent/config/`
- `cache/` 内容（AgentInstanceCache）→ 移至 `infra/common/cache/` 或 `application/agent/cache/`
- `nacos/` 内容（A2aSyncProperties / NacosAgentCardFetcher）→ 移至 `infra/common/nacos/`

**注**：这些都是跨聚合或共用的基础设施，不应属于 agent 聚合私有目录。

---

## 合规亮点

1. **依赖方向完全正确**：无任何 infra 文件引用 application / adapter / client 业务类型
2. **Entity 规范度高**：
   - 所有 Entity 都用 `@Data` + MyBatis-Plus 注解
   - 审计字段齐全（`create_no` / `update_no` / `create_time` / `update_time` / `deleted`）
   - 字段 JavaDoc 完整
3. **RepositoryImpl 依赖管制严格**：仅注入 Mapper，无混杂
4. **存在性检查规范**：全部用 `num` 和 `deleted` 条件，无越权
5. **CommonDomainEventPublisher 实现正确**：基于 Spring ApplicationEventPublisher 的本地实现，符合规范
6. **Mapper 继承正确**：全部继承 `BaseMapper<XxxEntity>`，无自定义 SQL（未发现 SQL 注入风险）
7. **session / evaluation 聚合规范度最高**：完全按五子目录标准实现，无额外目录

---

## 修复优先级与建议

### Tier 1（P0，阻塞）
**估时**：1-2 小时

1. **Repository 接口移方法** (agent)
   - 从 `AgentRepository` 移除 `findByNacosServiceKey()`
   - 在 `domain/agent/gateway/AgentReadGateway` 或 `AgentA2aGateway` 中新增该方法
   - 更新 infra 的 AgentReadGatewayImpl 或新增 AgentA2aGatewayImpl

### Tier 2（P1，强烈建议）
**估时**：2-3 小时

2. **Factory 移除 Publisher 注入**
   - 删除 AgentFactoryImpl 和 SkillFactoryImpl 中的 `private final DomainEventPublisher domainEventPublisher` 字段
   - 删除 `wire()` 方法中的 `agent.setDomainEventPublisher(domainEventPublisher)` 行
   - 若 domain 实体需要 Publisher，由 application 在 create 或 save 后直接通过 setter 装配（或修改 domain 构造策略）

3. **移除 infra Gateway 上的 @Transactional**
   - 删除 `SkillVersionReadGatewayImpl` 和 `AgentVersionReadGatewayImpl` 方法级 `@Transactional` 注解
   - 事务由 application Service 方法声明（如需）

### Tier 3（P2，一致性改进）
**估时**：3-4 小时

4. **重构 agent 包结构**
   - 创建 `infra/common/builder/`、`infra/common/cache/`、`infra/common/nacos/`
   - 移动相应文件并更新 import 路径
   - 或将这些放在 application 层对应位置（如 application/agent/config/builder）

5. **补齐 common 子包**
   - 创建 `infra/common/constant/` 并统一 `DeleteFlagConstant` 等
   - 创建 `infra/common/exception/` 并统一 `LockException` 等
   - 更新引用

---

## 检查清单

| 项 | 状态 | 备注 |
|---|---|---|
| 依赖方向（infra 不引 application/adapter/client）| ✅ | 完全合规 |
| 包结构（仅 5 子目录，无 query/dto/service） | ⚠️ | agent 有越权，session/evaluation/skill 正确 |
| Repository 三方法硬约束 | ❌ | Agent 有 4 个方法 |
| RepositoryImpl 仅注入 Mapper | ✅ | 完全合规 |
| FactoryImpl 仅注入 Repository | ❌ | agent/skill 注入了 Publisher |
| Entity @Data + JavaDoc + 审计字段 | ✅ | 完全合规 |
| 存在性用 num | ✅ | 完全合规 |
| common 子包齐全 | ❌ | 缺 constant / exception |
| 事务在 application | ⚠️ | 2 个 gateway 违规 |
| DomainEventPublisher 实现 | ✅ | 正确实现 |

---

## 总体评分

**规范度**：73/100

- 依赖纪律：100/100
- 包结构规范：80/100
- Repository 约束：85/100（1 个接口违规）
- RepositoryImpl 依赖：100/100
- FactoryImpl 依赖：70/100（2 个注入违规）
- Entity 规范：100/100
- JavaDoc 完整性：95/100（极少数私有字段缺注释）
- 事务边界：85/100（2 处 infra 层加 @Transactional）
- 通用基础设施：80/100（common 缺子包，agent 有越权目录）

**最危险的违规**：Repository 接口超规（P0，直接影响三层约束）

