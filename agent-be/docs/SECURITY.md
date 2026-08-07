# Security — 安全约束

> 这份文件定义本项目的"安全红线"。任何代码修改都不能违反这里的规则。
> 安全规则应尽量由 lint / 自动化检查机械强制,文档只是给 agent 看的"为什么"。

---

## 1. 认证 / 授权

### 1.1 认证机制

- **Admin 平面**(fe → be):JWT Cookie（`session_token`）+ RBAC；本地可用 `app.auth.disable-auth=true` + 默认/`X-User-Id` 身份。SSO/GCAC 已移除
- **Open API**:`ApiKeyAuthenticationFilter`（`/api/v1/open/**`）
- **Data Plane 调用**(be → sphere):**MVP 阶段无服务间鉴权**,靠 K8s NetworkPolicy 限制只允许特定 pod 访问 sphere

> ⚠️ 见 memory:Sphere 部署必须配 NetworkPolicy。运维上线前必须确认 NetworkPolicy 就位,否则等同于裸奔。

### 1.2 授权模型

> 详见 [PRD-MVP §3.1 用户角色](../../docs/agent-sphere/PRD-MVP.md#31-用户角色)。

- 角色:`admin` / `editor` / `viewer`(MVP 实际用 3 档)
- 资源级权限:本 MVP 不做(所有 editor 可编辑所有 prompt)
- 后端实现:`@RequirePermission(Role.EDITOR)` 注解 + Spring AOP 拦截器
- 所有写接口必须明确标注角色要求,无注解的接口 ArchUnit 测试要 fail
- viewer 访问写操作 → 403 + 明确错误码 `AGT-AUTH-VIEWER-DENIED`

## 2. 敏感数据处理

### 2.1 不可记录到日志的字段

> 任何日志输出必须经过 `LogSanitizer` 过滤,违反由 lint 检查。

- JWT 完整内容
- `X-Service-Token`(platform 用的共享 secret)
- 数据库密码
- 各类 API Key / Secret

> 工号、邮箱、姓名可记录(内部产品,审计需要)。

### 2.2 加密 / 脱敏

- 本服务不存储真正的 PII(密码 / 银行卡 / 身份证),所以不需要业务级加密
- prompt 内容**不算敏感**(算法可见 + 审计需要),但也不应该出现在 INFO 日志(避免日志爆炸)
- 数据库连接 / Apollo / Redis 等基础设施凭证从环境变量 / K8s Secret 注入,不进代码 / 不进配置文件

### 2.3 数据保留

- 审计日志保留 1 年(建议值,待安全合规确认 — 见 [PRD-MVP §9](../../docs/agent-sphere/PRD-MVP.md#9-风险与待决问题))
- thread / prompt_versions:不主动清理(MVP 阶段数据量小,后续按需引入归档)

## 3. 输入验证

### 3.1 边界处解析(Parse, don't validate)

> 在系统边界(HTTP / sphere REST 响应)处把输入解析成强类型形态。一旦进入业务层就假设数据已经合法。

- HTTP 入口:Spring `@Valid` + Bean Validation,失败抛 `BizException` 统一 400 响应
- sphere 响应:用 Jackson 解析到强类型 DTO,字段缺失/类型不符立即 fail-fast
- prompt 内容:作为字符串存储,不做 HTML 转义(展示由 fe 处理);**编辑器禁止渲染为 HTML**(XSS 防护)
- prompt 变量占位符语法:`{var}`(单花括号,Python str.format 兼容,见 PRD §9 决议)— 边界处校验占位符合法性

### 3.2 上传文件

- MVP 不支持文件上传(prompt 是纯文本)
- 后续若新增,必须:类型白名单 + 大小限制 + 病毒扫描 + 存储在独立 OSS bucket

## 4. 已知威胁模型

| 威胁 | 影响 | 缓解措施 |
|------|------|---------|
| 内部用户越权编辑 prompt | 算法/PM 误改生产 prompt | RBAC + 审计日志 + 二次确认 + 版本回滚能力 |
| Internal API 被外部访问 | platform 之外的服务拿到 prompt | URL 前缀强制 SecurityFilter + K8s NetworkPolicy |
| `X-Service-Token` 泄露 | 攻击者可拉取所有 prompt | Token 走 K8s Secret;泄露后立即轮换;考虑 v0.2 升级到 mTLS |
| sphere 调试台被滥用跑高 token agent | 算成本爆炸 | MVP 仅做用户级 QPS 限流(见 PRD §2.2) |
| prompt 编辑器 XSS | 攻击者通过 prompt 注入恶意脚本 | 编辑器仅作为 string 存储,不渲染 HTML;调试台展示也用 textarea |
| be ↔ sphere 网络被嗅探 | prompt 内容被截获 | K8s NetworkPolicy 限制流量来源 + 内网 HTTPS(若 sphere 支持) |

## 5. 安全审计要求

> 完整字段定义见 [PRD-MVP §5.4](../../docs/agent-sphere/PRD-MVP.md#54-审计) + [../../rd-agent-ws/docs/CONVENTIONS.md §6](../../rd-agent-ws/docs/CONVENTIONS.md)。

必须落审计日志的操作:

- `prompt.create` / `prompt.update` / `prompt.activate` / `prompt.rollback` / `prompt.delete`
- `user.role.change`(v0.2 系统设置上线后)
- `audit.export`(谁导出过审计日志)— 自审计

保留 1 年(待安全合规确认)。MVP 不做查询 UI,仅落 PG。

## 6. 第三方依赖安全

<!-- TODO: 切片 1 工程骨架就位后:
- 接 Dependabot / Snyk / OWASP Dependency-Check
- 漏洞响应 SLA:Critical < 24h、High < 7d、Medium < 30d
-->

- TODO

---

## 已知漏洞 / 待修复项

| 编号 | 描述 | 影响 | 计划修复 |
|------|------|------|---------|
| (暂无) | - | - | - |
