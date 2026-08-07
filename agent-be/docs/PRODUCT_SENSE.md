# Product Sense — 产品品味与取舍偏好

> 这份文件让 agent 在做产品判断时知道"我们的偏好是什么"。
> 不写在这里的,agent 会按训练集里的"业界平均"来做决定,常常和你不一致。

---

## 1. 我们解决谁的什么问题

- **主要用户画像**(详见 PRD-MVP §3.1):
  - **Agent 开发者**(editor):写新 agent → 调试 → 调 prompt
  - **算法 / PM**(editor):编辑 prompt → 真实 jira_key 评估 → 对比版本 → activate
  - **运维 / SRE**(viewer):查 agent 状态 / prompt 生效版本 / 审计日志
  - **管理员**(admin):用户角色管理(MVP 不做 UI,后端 API 就位)
- **核心 use case**(对 be 而言):
  - 提供 fe 调试台、智能体浏览、提示词管理所需的所有 API
  - 给 platform 拉取 active prompt 的内部 API(场景 D)
  - 审计所有写操作
- **不服务的用户**:
  - **工分系统的最终用户**(研发工程师 / TL / PM)— 他们用 rd-points-fe,不需要本服务
  - **外部第三方**— 仅内网服务

## 2. 决策原则

- **降级优先**:任何"动态能力"(如 prompt 拉取)都必须有降级路径,确保 be 故障不阻塞业务
- **优先复用,再考虑新建**:架构和模块拆分尽量对齐 rd-points-platform(已被验证),不轻易引入新的工具链
- **小心扩展接口表面**:Internal API(`/admin/api/v1/internal/*`)只增不改,改了 platform 就可能挂
- **错误信息分受众**:Admin API 给 fe 用,可以暴露具体 message;Internal API 给 platform 用,error 必须稳定可解析
- **配置项越少越好**:每个新增配置项都要回答"为什么不能用合理默认值"
- **不为'可能用得到'做产品设计**:除非有真实用户场景,不做泛化(见 PRD-MVP §3.3 / §7.2 — A/B 测试 / 灰度 / lock 等都不在 MVP)

## 3. 我们不会做 X,因为 Y

| 不做的 | 原因 | 何时重新评估 |
|--------|------|------------|
| 多人协同编辑同一 prompt(lock / 实时同步) | MVP PM 接受"最后写的覆盖前面" | v0.3,如果出现并发编辑投诉 |
| Prompt A/B 测试 / 按比例灰度 | MVP 仅支持全量切换,实现复杂度高 | v0.3+ |
| Agent 启停 / 重启 / 热加载 | 不在本服务职责范围(属于 sphere) | sphere 提供能力后再加 admin UI |
| 跨 agent prompt 模板复用 / inheritance | MVP 一个 agent 一个 prompt,不引入继承 | 出现 ≥3 个相似 agent 时 |
| 与 rd-points-platform 共享 user/role 数据 | 故意做产品边界隔离 | 不重新评估 |
| 服务间鉴权(be ↔ sphere) | v0.5 sphere 不支持,靠 NetworkPolicy 防护 | sphere 升级时 |
| en-US 国际化 | MVP 仅 zh-CN | v0.2 |
| 移动端适配 | 后台产品,桌面 only | 不重新评估 |

## 4. UI / UX 偏好

> be 不直接渲染 UI,但 API 设计需要配合 fe 的偏好(完整偏好见 [../rd-agent-fe/docs/PRODUCT_SENSE.md](../../rd-agent-fe/docs/PRODUCT_SENSE.md))。be 侧的 UX 相关约定:

- 关键操作(prompt activate / rollback / 删除)必须支持二次确认 — 接口需要明确的 `confirm: true` 参数
- 列表 API 默认返回前 20 条,通过 page/perPage 分页
- API 错误响应统一 `{ code, message, request_id }`,fe 可以直接展示 message
- 时间字段统一 UTC ISO 8601,展示时区由 fe 处理

## 5. 文案与措辞

- 用"调试台"不用 "playground"
- 用"对话流 / Thread"不用 "session"
- 用"激活版本"不用 "current/published version"
- 用"代码默认 prompt"不用 "fallback prompt"(在 PRD 是这样统一的)
- API error message 以动词开头:"请检查参数..."而非"错误:..."

## 6. 性能偏好

> 性能 SLO 详见 [docs/RELIABILITY.md](RELIABILITY.md),这里只列产品偏好。

- 用户编辑 prompt 后激活,期望 5s 内能在调试台看到效果(US-1)
- 智能体列表加载超过 800ms 必须显示骨架屏(由 fe 实现,但 be API 要保证 p95 < 800ms)
- prompt 列表 API p95 < 300ms,编辑保存 < 500ms — 不达标优先优化 PG 索引,不引入 Redis 缓存(避免一致性问题)
- platform 拉取 active prompt 接口要求 p99 < 50ms(走 Redis 热缓存)— platform 自己也有 5min 进程缓存兜底

---

> **提示**:每次 PR review 中提到的"我喜欢/我不喜欢"都应该被沉淀到本文件。否则下次 agent 还会犯同样的错。
