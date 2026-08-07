# 模型管理优化 — 实现进度跟进计划

> 上游方案:[2026-06-17_模型管理优化-技术方案.md](../../../doc/技术方案/2026-06-17_模型管理优化-技术方案.md)
> 代码分支:`feature-20260617-model-management-scope`
> 审计日期:2026-06-17(8 层并行 workflow 逐层比对,每条带 `file:line` 证据)

---

## 一、审计总体结论

**核心功能已全部落地,代码主线可用,但测试覆盖严重不足,整体仍处「可演示、不可安全交付」状态。**

scope 隔离、系统模型 Key 三层防线、ConfigSnapshot 资源版本号、权限种子与 DDL 等关键能力均已实现;主要风险集中在测试(仅 13%)和少量契约缺口。

**总体完成度:约 84%**(功能代码 ~95%,测试 ~13%)。

### 分层完成度

| 层 | 完成度 | 状态 | 关键说明 |
|---|---|---|---|
| facade | 100% | ✅ | `model:create/update/delete` 已加,保留 `model:read`,`system:manage_settings` 就位,BizCode 全复用 |
| client | 88% | 🟡 | scope 已落 Create/PageQuery/VO/DTO;**`ModelUpdateParam`(VO/DTO) 缺 scope** |
| domain | 100% | ✅ | Model scope + 一致性规则 + 4 领域动作 + ConfigSnapshot refs/enablePlan 全就绪 |
| infra + DDL | 85% | 🟡 | V29 生成列+唯一索引、权限种子已落;3 项架构偏离 + `ModelCredentialResolver` 零测试 |
| application-model | 100% | ✅ | 11 个方法全落地,系统 Key 三层防线到位 |
| application-agent | 88% | 🟡 | normalizeSnapshot 校验链完整;**缺去重**、**legacy 兜底缺 warning** |
| adapter | 100% | ✅ | 8 接口 + Key 剔除 + 路由权限全到位;Controller 由"共用"改为"拆分" |
| tests | 13% | ❌ | 8 项要求仅 1 项落地,且缺正向脱敏断言 |

---

## 二、待修复缺口清单(按优先级)

### 🔴 P0 — 阻塞交付

| # | 缺口 | 影响面 | 文件 | 状态 |
|---|---|---|---|---|
| P0-1 | 测试覆盖严重不足(8 项仅 1 项) | 全栈(权限越权/密钥泄漏/跨空间串改无回归保护) | client/domain/facade 模块零测试 | ⬜ |

P0-1 拆分为可执行测试:
- a. 安全回归测试:系统模型 VO JSON 不含 `apiKey/apiKeyMasked/apiKeyPrefix/apiKeyCipher`
- b. `ModelCommandService` 单测:PLATFORM workspace 置 null、SPACE 无 workspace 拒绝、唯一性预检分 scope
- c. `AgentCommandService` 单测:当前空间模型可保存、其他空间拒绝、系统模型可保存、禁用模型拒绝
- d. ConfigSnapshot 序列化测试:`enablePlan` 缺省 false、refs 反序列化、旧 nums 兼容
- e. 运行时解析测试:refs 按 versionNum、legacy 兜底 warning
- f. 权限集成测试:space_member 不能写、space_admin 可以、非 platform_admin 不能写系统模型(完整 HTTP 链)
- g. Flyway 迁移测试:存量迁移为 SPACE、workspace 可空、PLATFORM 唯一约束不影响 SPACE 跨空间

### 🟠 P1 — 功能/安全缺陷

| # | 缺口 | 影响面 | 文件:行 | 状态 |
|---|---|---|---|---|
| P1-1 | `normalizeSnapshot` 缺去重(dedup)逻辑 | agent 发布/运行时(重复 refs 致重复注册) | `AgentCommandService.java:566-583` | ⬜ |
| P1-2 | `ModelUpdateParam`(VO/DTO)缺 scope 字段 | 模型更新接口契约不一致 | `ModelUpdateParam.java` / `ModelUpdateParamDTO.java` | ⬜ |
| P1-3 | `ModelCredentialResolver` 零测试 | 运行时密钥解析(解密失败/跨 scope 串用难发现) | `ModelCredentialResolver.java` | ⬜ |

### 🟡 P2 — 可观测性 / 契约一致

| # | 缺口 | 影响面 | 文件:行 | 状态 |
|---|---|---|---|---|
| P2-1 | `ModelDomainEventDTO` 缺 scope 字段 | 下游事件消费方无法区分 SPACE/PLATFORM | `ModelDomainEventDTO.java` | ⬜ |
| P2-2 | `AgentRunnerFactory` legacy skillNums 兜底缺 warning | 存量数据迁移进度不可监控 | `AgentRunnerFactory.java:245-252` | ⬜ |
| P2-3 | `MODEL_ENABLE` 冗余常量 | 方案要求 enable/disable 走 `model:update` | `PermissionCode.java:70` | ⬜ |
| P2-4 | `ModelQueryServiceScopeTest` 缺正向脱敏断言 | 脱敏回归不可见 | `ModelQueryServiceScopeTest.java` | ⬜ |

### ⚪ P3 — 文档对齐

| # | 缺口 | 处理 |
|---|---|---|
| P3-1 | V30 未创建(权限种子已并入 V28) | 功能等价,记录说明 |
| P3-2 | 同步修订方案文档(createModel 签名、Controller 拆分、validateModelEnabled 命名) | 文档对齐 |
| P3-3 | 与前端对齐 `/api/v1/system/model/*` 独立路径 | 待前端 |

---

## 三、设计偏离记录(评估可接受,不改代码,记录在案)

| 偏离项 | 实际 | 评估 |
|---|---|---|
| `MODEL_ENABLE` 多余常量 | `PermissionCode.java:70` | **需决策**(见 P2-3) |
| Controller 拆分而非共用 | SPACE 走 `/api/v1/model/*`,PLATFORM 走 `/api/v1/system/model/*` | 基本可接受,需与前端确认 |
| Factory 实现在 domain 而非 infra | domain 层 `@Component` | 可接受(DDD 更合理) |
| PLATFORM 置 workspace=null 上移到 application | `ModelCommandService.createModel` | 可接受(功能等价) |
| `createModel` 签名偏离 | scope/workspaceNum 从 DTO 读 | 可接受(文档对齐) |
| `validateModelSelectable` → `private validateModelEnabled` | 改名 + guard | 可接受(语义等价) |

---

## 四、执行进度

> 按 P0 → P1 → P2 优先级修复。每项落地后打 ✅。

- [x] **P1-1** normalizeSnapshot 去重 — `AgentCommandService` 新增 `dedupSkillRefs`/`dedupToolRefs`(按 num+versionNum),补 `AgentCommandServiceNormalizeTest`
- [x] **P1-2** ModelUpdateParam scope 决策 — 决策**不加 scope 字段**(编辑不允许变更归属,方案 §1.2 决策 4),在 VO/DTO JavaDoc 注明 + `assertWritableByEntry` 守卫
- [x] **P1-3** ModelCredentialResolver 单测 — 新增 `ModelCredentialResolverTest`(按 num 解密 / ENABLED 守卫 / 跨 scope 不串用 / 不存在 / 未启用)
- [x] **P2-1** ModelDomainEventDTO.scope — DTO 补 `scope` 字段 + `from()` 赋值,补 `ModelDomainTest`
- [x] **P2-2** AgentRunnerFactory legacy warning — `registerSkills` legacy skillNums 分支补 `log.warn`
- [x] **P2-3** MODEL_ENABLE 常量决策 — **移除** `MODEL_ENABLE` 常量(enable/disable 走 `model:update`),同步清理 V28/V29 seed + RouteRoleMapping 注释,补收敛 SQL
- [x] **P2-4** ModelQueryServiceScopeTest 正向脱敏断言 — 补空间模型 detail/requireSelectable 脱敏串断言 + 系统 model 不得出脱敏
- [x] **P0-1 安全回归测试** — 新增 `ModelVoAssemblerSecurityTest`(系统模型 VO/detail JSON 不含 apiKey*/apiKeyMasked/apiKeyPrefix/apiKeyCipher;selectable 两端无 Key;反向断言空间模型仍带 apiKeyMasked)
- [x] **最终验证** `mvn test` 全绿 — **BUILD SUCCESS, 55 个测试全过**

### P0-1 测试落地明细(本轮已补)
- ✅ 安全回归: `ModelVoAssemblerSecurityTest`(8 test)
- ✅ scope 一致性 + 事件载荷: `ModelDomainTest`(9 test)
- ✅ 运行时凭证解析: `ModelCredentialResolverTest`(5 test)
- ✅ normalizeSnapshot 去重 + enablePlan 默认值: `AgentCommandServiceNormalizeTest`(6 test)
- ✅ ModelQueryService 正向脱敏断言: 扩展 `ModelQueryServiceScopeTest`(+3 test)

> P0-1 剩余尚未补的:Flyway 迁移测试(需 Testcontainers/嵌入式 MySQL,与现有无容器测试范式不匹配,留作后续)、权限集成测试(需完整 HTTP + Redis,属集成层)、AgentCommandService 端到端单测(依赖 Spring 容器,本次先以纯函数去重测试覆盖核心逻辑)、ConfigSnapshot Jackson 序列化测试。

---

## 五、验证闭环

```bash
mvn -DskipTests clean package   # 编译装配六层
mvn test                         # 单测全绿(含 jacoco)
```

修复完成后归档本文件到 `completed/` 并加日期前缀。
