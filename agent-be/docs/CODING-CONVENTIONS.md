# Coding Conventions — 编码规范

> 本文件收录 rd-agent-be 后端代码的强制规范。规则一旦写入此处即视为「不可绕开」，违反需在 PR 中明确说明并取得 reviewer 同意。

---

## 1. 注释规范

### 1.1 强制规则：所有类 / 属性 / 方法都必须有 JavaDoc

| 范围 | 必须写注释 | 内容要点 |
|------|-----------|----------|
| 类 / 接口 / 枚举 / record | 是 | 1-3 行：角色（聚合根 / 实体 / 值对象 / 枚举 / 工厂 / 网关 / 仓储 / Service / Controller 等）+ 核心职责 |
| 字段 / 常量 | 是 | 一行 `/** ... */`：业务含义 + 必要的单位 / 格式 / 取值范围 |
| 方法（含 private、含重写） | 是 | 1-2 行说明用途；非显然的入参补 `@param`，有返回值补 `@return`，会抛业务异常补 `@throws` |
| 枚举常量 | 是 | 每个常量加一行 `/** ... */` 说明语义 |

**例外**：

- Lombok 自动生成的 getter/setter（无源代码）无需处理。
- `transient` 装配依赖字段也要写注释，标注「装配依赖：xxx」。
- 重写的抽象方法（如 `domainValidate / save / delete`）必须写注释，说明本实体在该方法里的契约要点（不复述步骤号注释）。

### 1.2 风格

- **中文** + 简洁，聚焦「为什么 / 语义 / 约束」，不复述「什么」（well-named identifier 已经表达了「什么」）。
- 单位 / 格式 / 取值范围必写：例如 `totalLatencyMs 单位 ms`、`traceId W3C 风格`、`versionNum 形如 vX.Y.Z`、`title 上限 128 字符`。
- 已有的步骤注释（`// 1. 初始化对象`）保留，不要删除。
- 不使用 emoji。

### 1.3 风格参考

`rd-agent-be-domain` 整体已按本规范补齐，可作为 reference：

- 聚合根：`ink.garry.rd.agent.ws.domain.agent.Agent`
- 值对象：`ink.garry.rd.agent.ws.domain.agent.valueobject.Version`
- 接口：`ink.garry.rd.agent.ws.domain.agent.gateway.AgentRunner`
- 工厂：`ink.garry.rd.agent.ws.domain.skill.factory.SkillFactory`
- 常量类：`ink.garry.rd.agent.ws.domain.common.DomainEventConstant`

### 1.4 新增 / 修改代码时

- 新增类、字段、方法 → 同步补 JavaDoc，不允许「先提交、后补」。
- 修改字段语义 / 方法契约 → 同步更新 JavaDoc。
- Code Review 时若发现缺注释，reviewer 必须 BLOCK。

---

## 2. 依赖管理与工具库选型

### 2.1 强制规则：工具方法优先 Hutool

`cn.hutool.*`(`hutool-all` 已在 parent pom 固化)是本仓库**唯一指定的通用工具库**。新增或重构代码遇到"需要一个工具方法"时：

1. **先查 Hutool**:覆盖 HTTP / JSON / IO / 字符串 / 集合 / 日期 / 加密 / 反射 / Excel / 压缩 / 网络 等 90% 通用场景
2. **Hutool 已有的能力,禁止引入替代库**(Guava / Apache Commons / OkHttp / Spring `StringUtils` 等)
3. **Hutool 没有的能力**,**必须先在 PR 描述或与负责人沟通后**再决定是否引入新依赖,**禁止自行选型直接落地**
4. 既有代码用了非 Hutool 工具且 Hutool 有等价能力时,允许在同一 PR 内顺手迁移,但需在 PR 描述说明

### 2.2 已定型的工具映射

| 场景 | 必选 |
|---|---|
| HTTP 客户端(同步) | `cn.hutool.http.HttpRequest` / `HttpUtil` |
| 字符串 | `cn.hutool.core.util.StrUtil` |
| 集合 | `cn.hutool.core.collection.CollUtil` |
| 日期 | `cn.hutool.core.date.DateUtil` |
| IO / 文件 | `cn.hutool.core.io.FileUtil` / `IoUtil` |
| 加密 / 摘要 | `cn.hutool.crypto.SecureUtil` |
| 反射 | `cn.hutool.core.util.ReflectUtil` |

### 2.3 既定的非 Hutool 库(与 Hutool 共存,不需替换)

| 库 | 用途 | 不替换原因 |
|---|---|---|
| `fastjson2`(`com.alibaba.fastjson2.JSON`) | JSON 序列化 / 反序列化 | parent pom 固化,生态成熟,与 Hutool `JSONUtil` 互补但更强;**继续使用,不用迁到 `JSONUtil`** |
| `spring-ai-*` | LLM / A2A 集成 | 框架强绑定 |
| `mybatis-plus` | 持久化 | 框架强绑定 |
| `jjwt` | JWT 签发 / 解析 | Hutool `JWT` 功能弱,jjwt 是行业标准 |

### 2.4 引入新依赖的流程

1. 在 PR 描述列出:**为什么 Hutool 不够用** + **候选库对比** + **维护性 / License / 体积评估**
2. 同步更新 parent `pom.xml` 的 `<properties>` 版本号 + `<dependencyManagement>` 声明
3. 同步更新本文件 §2.3 的清单
4. Reviewer 在合规审计时拒绝任何未走流程的 `<dependency>` 新增

---

## 3. 各层产出类型(VO / DTO / Domain)

### 3.1 强制规则:VO 专给 Controller

每一层"返回"什么类型,有硬约束:

| 层 | 返回类型 | 说明 |
|---|---|---|
| **domain** (聚合根 / Repository / Factory / Gateway 接口 / valueobject) | **Domain Entity / Value Object** | 不允许返回 DTO / VO |
| **infra** (Repository 实现 / Mapper / 外部客户端 / Builder) | **DTO**(或 Domain) | Mapper 返回 Entity 是 MyBatis 约定,不在此约束内 |
| **application** (CommandService / QueryService / Strategy / Runner) | **DTO** | 禁止返回 VO 或 Domain Entity |
| **adapter** (Controller / Listener / SSE 推送) | **VO**(由 DTO 转换而来) | Controller 收到 DTO 后**必须**显式 `toVO()`,不能透传 |

衍生约束:
- **Gateway 实现** 万不得已才返回 DTO;优先返回 Domain Entity / Value Object。例外:外部协议本身就是 DTO、Gateway 是对外契约适配器。
- **DTO 放在哪个模块,看"谁消费"**:
  - **infra 也要消费 → `facade.<domain>.*`**(facade 是 infra / application 共同可见的最低层)。典型例子:`AgentInvokeDTO` 被 `ConfigAgentBuilder`@infra 消费,必须放 facade。
  - **只有 application / adapter 消费 → `client.<domain>.dto.*`**(client 不需要 infra 可见,放 client 让契约与前端入参/出参就近)。典型例子:`AgentDTO` / `AgentVersionDTO` / `AgentA2aSyncHistoryDTO` 是 `AgentQueryService` 的全字段出参,不被 infra 消费,放 client。
  - **adapter 专用的入参 DTO** 也放 `client.<domain>.dto.*`(原约定保持不变)。
- **VO 放在 `client.<domain>.*`**,后缀 `*VO`。
- **DTO 后缀 `*DTO`**,字段命名与 Domain 保持语义一致即可,不需要逐字段镜像。

### 3.2 Strategy / Runner 不得直调持久层

`application` 层下属的 Strategy / Runner(如 `ConfigAgentRunner`、`A2aAgentRunner`、`AgentRunner` 实现等)如需查询业务数据,**必须**通过对应的 QueryService:

| 错误模式 | 正确模式 |
|---|---|
| Runner 注入 `AgentRepository` / `AgentMapper` / `AgentReadGateway` | Runner 注入 `AgentQueryService`,调 `loadAgentForInvoke(num)` 取 DTO |
| Runner 注入 `SkillRepository` / `SkillMapper` | Runner 注入 `SkillQueryService` |

**Why**: Strategy / Runner 是用例编排的一部分,如果绕过 QueryService 直查持久层,会破坏单一查询入口、复用缓存 / 鉴权 / 脱敏的能力,也使后续治理(打点 / 监控 / 替换实现)无处下手。

**例外**: `infra` 层内部的 Builder / Adapter(如 `ConfigAgentBuilder` 用 `AgentRepository` 递归装配子 Agent)允许使用 Repository,因为 Builder 不在 application 层,且 infra 无法反向依赖 application 层的 QueryService。

### 3.3 违规判定

- Code Review 看到 `application.*` 包下的 `Service / Runner / Strategy` 类返回了 `client.*VO` → BLOCK
- Code Review 看到 `adapter.*` 包下的 `Controller` 方法直接返回了 `*DTO` 而未做转换 → BLOCK
- Code Review 看到 Runner 注入了 `*Repository` / `*Mapper` / `*ReadGateway` → BLOCK

---

<!-- 后续编码规范（命名、包结构、异常、日志、测试覆盖率等）按需追加在此文件下方 -->
