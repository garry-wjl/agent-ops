# Security Policy

## Supported Versions

当前以 GitHub `main` 分支为唯一积极维护线。安全修复优先合入 `main`，并按需向仍在使用的发布标签回溯（如有）。

| Version / Branch | Supported |
|------------------|-----------|
| `main`           | ✅        |
| older tags       | 按严重程度评估，尽力而为 |

## Reporting a Vulnerability

**请勿**在公开 Issue、Discussion 或 PR 中披露未修复的安全漏洞。

请使用以下任一方式私密报告：

1. **推荐**：GitHub Security Advisories  
   → [Report a vulnerability](https://github.com/garry-wjl/agent-ops/security/advisories/new)
2. 私信维护者：见 [MAINTAINERS.md](MAINTAINERS.md)

报告时尽量包含：

- 受影响组件（`agent-be` / `agent-fe` / 依赖）
- 版本或 commit
- 复现步骤或 PoC（最小化）
- 影响面（鉴权绕过、数据泄露、RCE、多租户越权等）
- 是否已有公开利用

## 响应预期

我们会尽力：

- **72 小时内**确认收到报告  
- 评估严重程度并给出初步修复计划  
- 修复合入后协调披露时间（可协商 CVE / Advisory）

在修复发布前，请勿公开细节。

## 安全相关范围（本项目）

特别关注但不限于：

- 鉴权 / JWT Cookie / 本地 `AUTH_DISABLED` 误开到生产
- 多租户隔离（`X-Workspace-Num`、会话归属）
- Internal API（`X-Service-Token`）暴露面
- MCP / Function Call 出站请求头透传与 SSRF
- 密钥、数据库口令、OSS/S3 凭证进入仓库或日志
- 依赖供应链（Maven / npm）已知高危 CVE

产品安全设计细节另见：

- [agent-be/docs/SECURITY.md](agent-be/docs/SECURITY.md)
- [agent-fe/docs/SECURITY.md](agent-fe/docs/SECURITY.md)

## Safe Harbor

善意、负责任的安全研究（不破坏数据、不中断服务、不越权访问无关系统）将被视为对本开源项目的帮助，我们不会为此对研究者采取法律行动，前提是你遵守本政策并给予合理披露时间。
