# Security — 安全约束(前端视角)

> 这份文件定义本项目的"安全红线"。任何代码修改都不能违反这里的规则。
> 安全规则应尽量由 lint / 自动化检查机械强制,文档只是给 agent 看的"为什么"。

---

## 1. 认证 / 授权

### 1.1 认证机制

- **登录**:SSO/GCAC 已移除。未登录 → `/login`；本地 `disable-auth` 时「进入系统」拉取 `/api/v1/auth/me`
- **JWT 注入**:axios `withCredentials` 携带 Cookie（无需 fe 显式读 token）
- **登出**:清前端态 → 跳 `/login`

### 1.2 授权(UI 层)

- 角色:`admin` / `editor` / `viewer`(PRD §3.1)
- viewer 用户:写操作按钮 disabled + tooltip "需要 editor 权限"(F-AUTH-2)
- 不要靠后端 403 后再展示:必须前置阻止,提升体验
- 全局 store 缓存当前用户角色,组件级 `useAuth().hasRole('editor')` 判断

> ⚠️ UI 层只是"提升体验",**真正的鉴权在后端**;不能因为 UI 兜底就跳过后端权限检查。

## 2. 敏感数据处理

### 2.1 不可记录到日志 / Sentry 的字段

- JWT(虽然在 HttpOnly Cookie 里 fe 也读不到,但要确保不被业务代码打印)
- 用户邮箱明文(可记录工号)
- prompt 内容明文(避免 Sentry 上报时泄露 — 用 `Sentry.beforeSend` 过滤)

### 2.2 输入安全(XSS 防护)

> 这是前端的核心安全责任。

- **Prompt 编辑器**:用户输入仅作为字符串存储和展示,**禁止**渲染为 HTML 或执行 — 用 `<textarea>` / Monaco editor,不用 `dangerouslySetInnerHTML`
- **调试台输出**:agent 输出可能包含 markdown / 代码 / 用户提示,展示用 `react-markdown` 等显式 sanitize 库,**禁止裸用 `dangerouslySetInnerHTML`**
- **URL 参数**:从 URL 读 run*id / agent name 等参数时,先做白名单校验(仅允许 `[A-Za-z0-9*-]+`)
- **第三方 iframe**:MVP 不引入第三方 iframe;若未来引入必须配 CSP frame-ancestors

### 2.3 CSP / 安全 Header

<!-- TODO: 切片 1 部署时配置:
- Content-Security-Policy: default-src 'self'; img-src 'self' data:; ...
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- Strict-Transport-Security: max-age=...
-->

- TODO

## 3. 输入验证

### 3.1 边界处解析(Parse, don't validate)

> services 边界:把后端响应解析成强类型,字段缺失/类型不符立即 fail-fast

- 用 zod / yup 之类的运行时校验库(待选定)
- 不直接信任 `response.data as Foo`,必须 `parse(response.data)`
- 边界外的业务层假设数据合法,不再重复校验

### 3.2 表单校验

- Antd Form + 内置 rules
- 客户端校验仅作为体验优化,**真正校验在后端**
- 错误反馈:内联 + 红色 + 文字描述,不弹 modal

## 4. 已知威胁模型

| 威胁                         | 影响                                                      | 缓解措施                                                |
| ---------------------------- | --------------------------------------------------------- | ------------------------------------------------------- |
| Prompt 编辑器 XSS            | 攻击者通过 prompt 注入恶意脚本,影响后续读到 prompt 的用户 | 编辑器纯文本存储,展示 sanitize                          |
| 调试台 agent 输出 XSS        | 恶意 agent 返回 `<script>` 等内容                         | markdown 渲染走 sanitize 库                             |
| URL 参数注入                 | 攻击者构造特殊 run_id 触发 fe 异常                        | 白名单校验 + 异常 UI 兜底                               |
| Token 被偷(如恶意浏览器扩展) | 接管用户身份                                              | JWT 走 HttpOnly Cookie + Secure + SameSite=Lax          |
| CSRF                         | 攻击者引诱用户在登录态下访问恶意链接                      | SameSite=Lax + 写操作要求自定义 header(由 axios 默认带) |

## 5. 第三方依赖安全

<!-- TODO: 切片 1 工程骨架就位后:
- pnpm audit 接入 CI
- Dependabot / Renovate 自动 PR
- 漏洞响应 SLA:Critical < 24h、High < 7d
-->

- TODO

---

## 已知漏洞 / 待修复项

| 编号   | 描述 | 影响 | 计划修复 |
| ------ | ---- | ---- | -------- |
| (暂无) | -    | -    | -        |
