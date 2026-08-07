# Reliability — 可靠性、SLO、错误处理

> 这份文件定义"什么算稳定"和"出错时该怎么办"。
> 让 agent 写代码时知道目标和兜底策略。

---

## 1. 关键 SLO

> 详见 [PRD-MVP §5.1 性能](../../docs/agent-sphere/PRD-MVP.md#51-性能) + §5.2 可用性。

| 指标 | 目标值 | 当前观测 | 监控位置 |
|------|-------|---------|---------|
| 调试台 step 轮询间隔 | 1.5s(沿用 SphereDebug) | - | fe 实现 |
| prompt 列表 API p95 | < 300ms | - | <!-- TODO: SkyWalking dashboard --> |
| prompt 编辑保存 p95 | < 500ms | - | <!-- TODO --> |
| 智能体列表加载 p95(含调 sphere /agents) | < 800ms | - | <!-- TODO --> |
| `/admin/api/v1/internal/prompts/active` p99 | < 50ms(走 Redis) | - | <!-- TODO --> |
| 服务可用性 | MVP 单机,故障靠浏览器重试 + platform 降级 | - | - |

## 2. 错误处理策略

### 2.1 异常体系

> 拷自 rd-points-platform 模板。

- `BizException`(业务异常):可预期的业务错误,响应 4xx,向用户展示具体 message
  - `PromptNotFoundException`、`ThreadPermissionDeniedException` 等具体子类
- `SystemException`(系统异常):bug 或基础设施问题,响应 5xx,向用户展示通用错误
- `SphereTransparentException`(透传异常):sphere 返回的错误原样透传给 fe(保留 sphere 的 error code + message)
- 不允许直接抛 `RuntimeException` 或裸 `Exception`(由 ArchUnit 强制)

### 2.2 重试策略

| 调用 | 是否重试 | 策略 |
|------|---------|------|
| fe → be | 由 fe 决定 | be 不主动 |
| be → sphere(透传调试) | **不重试** | sphere 调用是用户交互的一部分,失败立即返回 |
| be → sphere(后台缓存刷新,如 /agents 元数据) | 3 次,指数退避 100ms / 500ms / 2s | 失败用旧缓存兜底 |
| be → PG(写) | **不重试**(避免幂等问题) | 失败抛 SystemException |
| be → Redis(读) | 1 次,失败降级到 PG 直查 | - |

### 2.3 降级方案(重要)

| 故障 | 降级行为 |
|------|---------|
| sphere 不可达 | 调试台返回错误,**不影响 prompt 编辑功能** |
| sphere `/agents` 超时 | 智能体列表用 30s TTL 缓存的旧数据 + 顶部 warning banner |
| Redis 不可达 | 所有缓存读降级到 PG 直查 + WARN 日志 |
| **be 自身全宕** | **关键约束:platform 业务必须不中断**,见下面"§2.3.1 platform → be 拉取 prompt 的降级矩阵" |
| Apollo 配置中心不可达 | 启动时拉一次本地缓存,运行时降级到本地配置 |

#### 2.3.1 platform → be 拉取 prompt 的降级矩阵

> 这是本服务最关键的可靠性约束 — `/admin/api/v1/internal/prompts/active` 的设计目标是**"be 全宕时 platform 业务不中断"**。详见 [PRD-MVP §6.2](../../docs/agent-sphere/PRD-MVP.md#62-platform--be--sphere-的业务调用链场景-d关键)。

| 故障 | platform 行为 |
|------|--------------|
| be 不可达 / 5xx | 用进程缓存(最长 5 分钟),缓存空 → 不传 prompt(sphere 走代码默认) |
| be 返回 404 | 不传 prompt(正常情况) |
| be 返回 200 但 prompt 为空字符串 | 视为异常,记 WARN 日志,走降级 |

**核心保证**:rd-agent-be 全宕时 platform 业务**不中断**,仅失去"动态 prompt"能力。

## 3. 可观测性要求

### 3.1 日志

- 结构化日志(JSON,沿用 platform logback 配置)
- INFO:关键业务事件(prompt 创建 / 激活、thread 创建)
- WARN:可恢复异常(sphere 临时失败、缓存 miss 后降级)
- ERROR:需要人工介入(认证失败、PG 连接断、未捕获异常)
- 禁止记录:JWT 完整内容、`X-Service-Token`、用户邮箱明文(保留工号即可)

### 3.2 指标(Metrics)

> 沿用 platform Prometheus 模板。

<!-- TODO: 切片 1 落地时补全。至少包含:
- HTTP 请求总数 / 错误率 / 延迟分位(by endpoint)
- sphere 透传调用计数 / 错误率 / 延迟
- prompt 缓存命中率
- DB 连接池使用率
-->

- TODO

### 3.3 追踪(Tracing)

- 接 SkyWalking(沿用 platform 配置)
- fe → be → sphere 全链路 trace_id 透传
- platform → be 调用必须带 X-Request-Id 透传

## 4. 容量假设

<!-- TODO: 切片 1 上线后基于真实数据补充。当前假设:
- prompt 总数 < 100(按 sphere agent 数量计)
- prompt 历史版本总数 < 10000(每个 agent 平均 100 个版本)
- 同时在线 admin 用户 < 50(内部产品)
- 单日 prompt 编辑次数 < 200
- platform 拉取 prompt QPS < 100(批量评估时高峰)
-->

- TODO

## 5. 故障应急手册(Runbook)

<!-- TODO: 切片 3 上线前必须有,因为涉及 platform 联动。至少覆盖:
- be 完全宕机如何快速恢复
- prompt 数据误删/误改如何回滚
- 与 sphere 的连接断时的诊断顺序
- platform 报"prompt 拉取异常"时的排查路径
-->

- TODO

---

## 演练记录

| 日期 | 演练类型 | 结果 | 改进项 |
|------|---------|------|-------|
| YYYY-MM-DD | TODO | TODO | TODO |
