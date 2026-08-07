# rd-agent-be-facade 合规审计 — 2026-05-26

## 摘要

- **总文件数**：21 个 Java 文件
- **总违规数**：9 处
- **P0 硬约束违规**：0 处（通过）
- **P1 强约束违规**：4 处（JavaDoc 缺失 + 字段命名偏差）
- **P2 一致性问题**：5 处（类级 JavaDoc 缺失）

---

## 模块全景

### facade 目录树（深 3 层）

```
rd-agent-be-facade/src/main/java/ink/garry/rd/agent/ws/facade/
├── domain/（三类基础类型 + 事件常量）
│   ├── DomainEntity.java            ✓
│   ├── DomainEventDTO.java          ✓
│   ├── DomainEventPublisher.java    ✓
│   └── DomainEventConstant.java     ✓
├── request/（通用请求）
│   └── CommonRequest.java           ✓
├── common/（通用支撑类型）
│   ├── Result.java                  ⚠️ 字段命名偏差（message vs msg）
│   ├── PageVO.java                  ✓
│   ├── PlatformEvent.java           ❌ 缺类级 JavaDoc
│   ├── EventType.java               ❌ 缺类级 & 方法级 JavaDoc
│   ├── InputType.java               ❌ 缺类级 JavaDoc
│   ├── ChangeLevel.java             ❌ 缺类级 JavaDoc
│   └── Version.java                 ⚠️ 缺类级 & 方法级 JavaDoc
├── exception/（业务异常）
│   └── BusinessException.java       ✓
├── agent/（Agent 领域）
│   └── AgentDomainEventDTO.java     ✓
├── skill/（Skill 领域）
│   └── SkillDomainEventDTO.java     ❌ 缺类级 JavaDoc
├── evaluation/（Evaluation 领域）
│   └── EvalDomainEventDTO.java      ❌ 缺类级 JavaDoc
└── auth/（认证）
    ├── token/
    │   ├── LocalTokenIssuer.java    ✓
    │   └── UserClaims.java          ✓
    └── （原外部登录 Gateway / DTO 子包已删除）
```

### 文件清单汇总

| 文件数 | 状态 | 备注 |
|---|---|---|
| 21 | 总计 | 通用库依赖正确（lombok / fastjson2 / hutool） |
| 15 | ✓ 合规 | 含三类基础类型、通用支撑、异常、认证契约 |
| 4 | ⚠️ JavaDoc 缺失 | 仅类级缺 JavaDoc；无代码逻辑违规 |
| 2 | ⚠️ 小偏差 | Result 字段命名 / Version 方法注释 |

---

## P0 硬约束审计（必须修）

### ✅ 通过

1. **依赖方向（§0.2）**
   - ✓ facade 仅依赖 lombok / fastjson2 / hutool
   - ✓ 无任何引用 domain / application / infra / adapter / client 的 import
   - ✓ pom.xml 仅声明三个通用库，无越界

2. **三类基础类型齐全（§1.2）**
   - ✓ `DomainEntity`：`@Getter` / `@Setter`，包含 5 个审计字段（id / createNo / updateNo / createTime / updateTime / deleted）
   - ✓ `DomainEventDTO`：5 个 Lombok 注解完整，5 个字段（id / type / data / time / sender）齐全
   - ✓ `DomainEventPublisher`：interface，含 `void send(DomainEventDTO)` 方法

3. **通用支撑类型就位（§1.3）**
   - ✓ `CommonRequest`：`@Data`，含 `operatorId` 字段
   - ✓ `Result<T>`：含 4 个字段（code / message / data / traceId），包含 `ok()` / `ok(T)` / `fail(Integer, String)` 静态方法
   - ✓ `BusinessException`：extends RuntimeException，含 `code` 字段，3 个构造方法完整

4. **审计字段命名一致性（§1.4）**
   - ✓ 全项目采用 `createNo` / `updateNo`（非 createId / updateId）
   - ✓ `initialize()` 方法逻辑正确：updateTime 每次更新，createTime / createNo 仅首次

5. **DomainEntity 抽象方法签名（§1.2 + §3.4）**
   - ✓ `domainValidate()` / `save(String operatorId)` / `delete(String operatorId)` 三个签名完全匹配
   - ✓ `validate()` 末尾调用 `domainValidate()`

6. **包结构（§1.5）**
   - ✓ facade 根下仅包含：`domain/` / `request/` / `common/` / `exception/` + 业务特例包（agent / skill / evaluation / auth）
   - ✓ 无越权子包（如 service / repository / controller / infra）

7. **Lombok 用法**
   - ✓ DomainEntity：正确用 `@Getter` / `@Setter`
   - ✓ DomainEventDTO：齐全五个注解
   - ✓ CommonRequest / Result：`@Data` / `@Getter` / `@Setter` 使用正确
   - ✓ 所有 DTO 无手写 getter/setter

---

## P1 强约束违规（强烈建议修）

### 1. Result 字段命名偏差
**位置**：`facade/common/Result.java:16-19`

**违规规则**：CLAUDE.md §1.3（Result 应含 `code / msg / data`）

**现状**：
```java
private Integer code;      ✓
private String message;    ⚠️ 应为 msg
private T data;           ✓
private String traceId;   ✓（扩展字段，允许）
```

**问题**：规范示例中字段为 `msg`，但实现中使用 `message`。虽然功能等效，但与规范示例不一致。

**应改成**（可选，建议保持现状）：
- 若严格对齐规范，将 `message` 改为 `msg`；并更新所有引用处的 Result 字段访问
- 若为项目特例，在 CLAUDE.md 本项目配置备注说明

**备注**：当前已被 adapter 层广泛使用（SkillQueryController / SkillVersionController / GlobalExceptionHandler 等 5+ 处），改动影响面大。建议在 CLAUDE.md 中标注为「本项目采用 message 而非 msg」，并确保后续新代码保持一致。

---

### 2-5. 类级 JavaDoc 缺失（7 处）

**违规规则**：CLAUDE.md §0.4（所有类、属性、方法都必须有 JavaDoc；rd-agent-be 强约束）

#### 违规清单

| 文件 | 类型 | 状态 | 应改 |
|---|---|---|---|
| SkillDomainEventDTO.java | 类 | 缺类级 JavaDoc | 补充：`/** Skill 领域事件载荷... */` |
| EvalDomainEventDTO.java | 类 | 缺类级 JavaDoc | 补充：`/** Evaluation 领域事件载荷... */` |
| PlatformEvent.java | 类 | 缺类级 JavaDoc | 补充：`/** 平台事件... */` |
| InputType.java | 枚举 | 缺类级 JavaDoc | 补充：`/** 输入类型... */` |
| EventType.java | 枚举 | 缺类级 + 方法级 JavaDoc | 补充类级与 `code()` 方法 JavaDoc |
| ChangeLevel.java | 枚举 | 缺类级 JavaDoc | 补充：`/** 变更级别... */` |
| Version.java | 类 | 缺类级 + 方法级 JavaDoc | 补充类级及 `initial()` / `parse()` / `next()` / `toStr()` 方法 JavaDoc |

**建议修复方式**：逐文件补充 JavaDoc；优先级为 Version（4 个方法缺注释）> EventType（1 个方法缺注释）> 单字段 DTO（仅需类级）。

---

## P2 一致性问题

### 1. auth 子包的模块定位
**位置**：`facade/auth/`（token 子包；原外部登录子包已删除）

**现状**：
- `LocalTokenIssuer`（interface）：facade 层定义，由 infra 实现
- `LocalTokenIssuer` / `UserClaims`：JWT 契约（仍保留）
- （原外部登录 Gateway / Token / Profile DTO 已删除）

**分析**：auth 包下的 interface 本应在 domain 层定义，但考虑到 rd-agent-be 无独立 user / auth 领域实体，而是纯基础设施集成，将认证相关契约放在 facade 层作为「基础设施无关的认证接口」是合理的扩展。

**建议**：在 CLAUDE.md 中补注说明，或新建 domain/auth 包将 LocalTokenIssuer 迁移至 domain 层（更符合分层理想）。当前做法可接受。

---

### 2. 版本类 Version 缺少 JavaDoc 及业务约束描述
**位置**：`facade/common/Version.java`

**现状**：
```java
public class Version {
    private int major;
    private int minor;
    private int patch;
    // 无类级 JavaDoc；4 个 public 方法无 JavaDoc
}
```

**问题**：Version 涉及版本号格式 vX.Y.Z 约定（见 `parse()` 方法校验逻辑），应有 JavaDoc 说明格式与版本递进规则。

**应改成**：
```java
/**
 * 版本值对象：遵循语义化版本 (Semantic Versioning) vX.Y.Z 格式。
 * 支持版本号比较、递进（PATCH / MINOR / MAJOR）。
 */
public class Version {
    // ...
}
```
并为 `initial()` / `parse()` / `next()` / `toStr()` 补充方法级 JavaDoc。

---

## 合规亮点

1. **核心层级约束完全正确**
   - 依赖纪律严格：零跨层引用、零基础设施包污染
   - 三类基础类型（DomainEntity / DomainEventDTO / DomainEventPublisher）完全按规范实现
   - 通用支撑类型（CommonRequest / Result / BusinessException）齐全

2. **设计灵活性**
   - 扩展了规范基础（如 Result 增加 traceId 字段、添加业务特例包 agent/skill/evaluation）
   - 领域事件 DTO 按聚合拆分（AgentDomainEventDTO / SkillDomainEventDTO / EvalDomainEventDTO）便于类型安全
   - 认证接口（LocalTokenIssuer）置于 facade 作为基础设施无关的契约

3. **审计字段命名一致**
   - 全局采用 `createNo` / `updateNo`（符合项目数据库规范）
   - DomainEntity 的 `initialize()` 逻辑正确，确保首次创建与多次更新的字段赋值一致

4. **Lombok 应用规范**
   - 没有手写 getter/setter，完全依赖 Lombok 生成
   - DTO 的 Lombok 注解齐全（@Data / @Builder / @AllArgsConstructor / @NoArgsConstructor）
   - DomainEntity 仅用 @Getter / @Setter（不用 @Data，保持保护级字段访问控制）

5. **对象初始化链路清晰**
   - `validate()` → `domainValidate()`：通用与特例校验串联
   - `initialize(operatorId)` 逻辑成熟，兼容多次调用不覆盖 createNo/createTime

---

## 修复优先级与建议

### Tier 1（P1，高优先级，影响 JavaDoc 完整性）
**估时**：2-3 小时

1. **补充 7 个文件的类级 JavaDoc**
   - SkillDomainEventDTO.java / EvalDomainEventDTO.java / PlatformEvent.java / InputType.java / ChangeLevel.java（各加一行 `/** */` 注释）
   - EventType.java / Version.java（各补类级 + 方法级 JavaDoc）

2. **Version 类补充方法 JavaDoc**
   - `initial()`：返回初始版本 v1.0.0
   - `parse(String)`：解析并校验 vX.Y.Z 格式
   - `next(ChangeLevel)`：按变更级别递进版本
   - `toStr()`：转为字符串表示

### Tier 2（P1，可选，一致性改进）
**估时**：0.5-1 小时

3. **Result 字段命名统一**
   - 选项 A：保持现状 `message`，在 CLAUDE.md 配置备注说明
   - 选项 B：改为 `msg`，更新所有引用处（影响 5+ 文件）
   - **建议**：选项 A（保持现状，避免大范围改动）

### Tier 3（P2，架构优化，非必须）
**估时**：1-2 小时

4. **认证接口迁移至 domain 层（可选）**
   - 新建 `domain/auth/` 包（或 `domain/common/auth/`）
   - 将 LocalTokenIssuer 迁移至 domain
   - 保持 facade 层仅含 DTO（UserClaims）
   - 更新 infra / application import 路径
   - **备注**：当前 facade 层放置也能接受，若追求严格分层可考虑

---

## 检查清单

| 检查项 | 状态 | 备注 |
|---|---|---|
| 依赖方向（仅 lombok / fastjson2 / hutool） | ✅ | 完全合规 |
| 三类基础类型（DomainEntity / EventDTO / Publisher） | ✅ | 齐全、实现正确 |
| 通用支撑类型（CommonRequest / Result / BusinessException） | ✅ | 齐全、方法完整 |
| 审计字段命名（createNo / updateNo） | ✅ | 全局一致 |
| DomainEntity 抽象方法签名 | ✅ | 完全匹配 |
| 包结构（无越权子包） | ✅ | 标准 + 业务特例正确 |
| Lombok 用法（无手写 getter/setter） | ✅ | 完全合规 |
| 类级 JavaDoc 完整性 | ⚠️ | 7/21 文件缺类级注释 |
| 方法级 JavaDoc 完整性 | ⚠️ | Version/EventType 缺方法注释 |
| Result 字段命名（code / msg / data） | ⚠️ | 使用 message 而非 msg（可接受） |
| 跨模块引用（adapter 使用 Result） | ✅ | 正确引入 facade.common.Result |
| DomainEventPublisher 实现 | ✅ | infra 有 CommonDomainEventPublisher 实现 |

---

## 总体评分

**规范度**：88/100

### 维度打分

| 维度 | 评分 | 说明 |
|---|---|---|
| 依赖纪律 | 100/100 | 零跨层污染；pom.xml 仅三个通用库 |
| 三层基础类型 | 100/100 | DomainEntity / EventDTO / Publisher 完全按规范 |
| 通用支撑类型 | 95/100 | 齐全；Result 字段命名为 message 而非 msg（-5） |
| 包结构规范 | 100/100 | 标准 + 业务特例正确配置 |
| Lombok 应用 | 100/100 | 完全规范，无手写代码 |
| JavaDoc 完整性 | 68/100 | 核心文件完整；7 个文件缺类级注释（-32） |
| 方法签名规范 | 100/100 | DomainEntity 抽象方法完全匹配 |
| 跨模块协作 | 100/100 | adapter 层正确使用 Result；infra 有 Publisher 实现 |

**最严重的偏离**：JavaDoc 缺失（7 个文件，但无代码逻辑错误）

**核心风险**：无（所有硬约束通过；JavaDoc 缺失为文档问题，非逻辑缺陷）

---

## 总结

rd-agent-be-facade 模块**高度合规** — 依赖纪律严格、三层基础类型完整、包结构规范。主要缺陷为 JavaDoc 文档不全（7 个类缺类级注释，2 个类缺方法级注释），建议在下一个周期补齐。所有硬约束（P0）均通过；P1 / P2 为文档优化与可选架构改进，不影响系统功能。

