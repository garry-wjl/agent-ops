# Reliability — 可靠性、错误展示、流式策略

> 这份文件定义"什么算稳定"和"出错时该怎么办"。
> 让 agent 写代码时知道目标和兜底策略。

---

## 1. 关键 SLO

> 详见 [PRD-MVP §5.1 性能](../../docs/agent-sphere/PRD-MVP.md#51-性能)。

| 指标                         | 目标值             | 监控位置                            |
| ---------------------------- | ------------------ | ----------------------------------- |
| 首屏 LCP                     | < 2s(局域网)       | Sentry / 浏览器 PerformanceObserver |
| 调试台 step 轮询间隔         | 1.5s 固定          | 代码常量,禁止动态调小               |
| 列表骨架屏触发阈值           | > 500ms 加载未完成 | hooks 实现                          |
| bundle size(首屏 JS gzipped) | < 300KB            | size-limit / CI                     |

## 2. 错误处理策略

### 2.1 错误展示分层

| 错误类型                        | UI 形态                                                                  |
| ------------------------------- | ------------------------------------------------------------------------ |
| 业务错误(4xx,如参数非法 / 重名) | 表单内联 + Antd message.error(2-3 秒)                                    |
| 权限错误(403)                   | toast + 按钮 disabled + tooltip "需要 editor 权限"                       |
| 未找到(404)                     | 页面级空状态 + "返回上一页" CTA                                          |
| 服务错误(5xx)                   | 页面级 ErrorBoundary + "服务暂时不可用,请稍后重试" + 二级展开 request_id |
| 网络错误(超时 / 断网)           | 顶部全局 banner + 重试按钮                                               |
| 未捕获异常                      | ErrorBoundary 兜底页 + Sentry 上报                                       |

> 完整错误码统一格式 `{ code, message, request_id }`,fe 优先用 message 展示,code 用于日志/上报。

### 2.2 重试策略

| 操作                                | 是否重试                                                     | 策略 |
| ----------------------------------- | ------------------------------------------------------------ | ---- |
| 列表加载                            | 用户手动重试(展示重试按钮)                                   | -    |
| 流式 step 轮询                      | 自动重试 3 次,指数退避 1.5s / 3s / 6s,失败后顶部 banner 提示 | -    |
| 表单提交(prompt 编辑 / 激活 / 回滚) | **不自动重试**(避免重复提交),失败后用户手动重试              | -    |
| 静态资源(图片 / icon)               | 浏览器默认                                                   | -    |

### 2.3 流式输出策略

> 沿用 SphereDebug 现状,详见 [PRD-MVP §4.2 F-CON-5](../../docs/agent-sphere/PRD-MVP.md#42-控制台调试台)。

- **协议**:轮询 `GET /admin/api/v1/runs/{id}/steps?seqAfter=…`,固定间隔 1.5s
- **触发条件**:发送消息后,直到 run 状态为 `completed` / `failed` / `cancelled` 才停止轮询
- **节流**:同一 run 同时只能有 1 个轮询循环;切换 thread / 关闭页面要立即取消
- **断线**:网络断开时暂停轮询,恢复后立即拉一次最新 step
- **取消**:`[⏹ 停止]` 按钮调 `POST /admin/api/v1/runs/{id}/cancel` + 立即停止本地轮询

### 2.4 降级方案

| 故障                  | 降级行为                                                                 |
| --------------------- | ------------------------------------------------------------------------ |
| be 5xx                | 页面级 ErrorBoundary + 重试按钮(MVP 不做多副本,见 PRD §5.2)              |
| be 全宕               | 浏览器侧无法工作;PRD 决议:不阻塞工分平台业务(由 platform 走 prompt 默认) |
| sphere 故障(透传 5xx) | 调试台显示错误 + 允许继续编辑 prompt(prompt 模块不依赖 sphere)           |
| Sentry 失败           | 静默 fail(不影响主流程)                                                  |

## 3. 可观测性要求

### 3.1 前端错误上报

- Sentry SDK,沿用工分平台 DSN + 独立 project(便于隔离监控)
- `window.onerror` + `unhandledrejection` 自动捕获
- ErrorBoundary 抛出的错误:补充 component stack + 当前 user_id + 当前 thread_id 上报
- 不上报:用户主动取消的请求(AbortError)、4xx 业务错误

### 3.2 用户行为埋点

<!-- TODO: 切片 1 接入前确定埋点方案,建议沿用工分平台:
- 关键操作(prompt activate / rollback / 调试台发送 / agent 切换)埋点
- 错误展示也埋点(便于反查产品体验)
-->

- TODO

### 3.3 性能监控

- Web Vitals(LCP / FID / CLS)上报 Sentry
- 关键交互打点:首屏到可交互、prompt 编辑器打开延迟

## 4. 容量假设

- 同时在线 admin 用户 < 50(内部产品)
- 单 thread 历史消息数 < 100(超出建议引导用户新建 thread)
- prompt 编辑器内容 < 50KB(超出 toast 警告)
- 智能体列表总数 < 100(MVP 不做无限滚动)

---

## 演练记录

| 日期       | 演练类型 | 结果 | 改进项 |
| ---------- | -------- | ---- | ------ |
| YYYY-MM-DD | TODO     | TODO | TODO   |
