# Contributing to agent-ops

感谢你关注 **agent-ops**（Agent Sphere / AgentOps Control Plane）。  
本文说明如何参与贡献：提 Issue、修 Bug、加功能、改文档。

> English summary: fork → branch from `main` → small focused PR → pass checks → wait for review.  
> Details below are primarily in Chinese to match the project docs.

## 行为准则

参与本仓库即表示你同意遵守 [Code of Conduct](CODE_OF_CONDUCT.md)。

## 开始之前

1. 阅读 [README.md](README.md) 了解项目定位与快速开始  
2. 阅读 [ARCHITECTURE.md](ARCHITECTURE.md) 理解 Control Plane / Data Plane 边界  
3. Agent / 编码约束见 [AGENTS.md](AGENTS.md)；落码细则见子工程：
   - 后端：[agent-be/AGENTS.md](agent-be/AGENTS.md)
   - 前端：[agent-fe/AGENTS.md](agent-fe/AGENTS.md)

有疑问先开 [Discussion](https://github.com/garry-wjl/agent-ops/discussions) 或 Issue，避免方向偏差后大改。

## 开发环境

| 组件 | 要求 |
|------|------|
| 后端 `agent-be` | JDK 17、Maven 3.9+、MySQL、Redis |
| 前端 `agent-fe` | Node（见 `agent-fe/.nvmrc`）、pnpm |
| 可选 | Docker（本地联调） |

```bash
# 后端
cd agent-be
mvn -pl rd-agent-be-adapter spring-boot:run

# 前端
cd agent-fe
pnpm install
pnpm dev
```

验证：

```bash
cd agent-be && mvn -DskipTests clean package && mvn test
cd agent-fe && pnpm typecheck && pnpm test
```

## 分支与提交

- **基线分支**：从最新 `main` 拉功能 / 修复分支  
- **命名建议**：
  - `feature-<yyyymmdd>-<short-topic>`
  - `hotfix-<yyyymmdd>-<short-topic>`
  - `docs-<yyyymmdd>-<short-topic>`
- **禁止**直接在 `main` / `master` / `test` / `stag` / `prod` 等环境分支上改代码  
- Commit message 建议 Conventional Commits 风格，例如：
  - `feat: ...`
  - `fix: ...`
  - `docs: ...`
  - `test: ...`
  - `chore: ...`
- 一个 PR 尽量只做一件事；跨 fe/be 契约变更请在同一 PR 或关联 PR 中同步，并写清验证范围

## Issue

适合开 Issue 的情况：

- Bug（附复现步骤、期望 / 实际行为、环境、日志片段）
- 功能建议（动机、场景、非目标）
- 文档问题

请优先使用 Issue 模板。  
**安全漏洞不要公开提 Issue**，见 [SECURITY.md](SECURITY.md)。

## Pull Request

1. Fork 本仓库（外部贡献者）或在本仓库建分支（有写权限时）  
2. 基于最新 `main` 开发  
3. 业务代码变更必须同步测试；缺测不算完成  
4. 对齐 [Pull Request 模板](.github/PULL_REQUEST_TEMPLATE.md)  
5. 确保 CI 通过；UI 改动请补充截图或录屏（如适用）  
6. 保持 PR 可审：描述动机、方案、风险与回滚点  

Reviewer 通常关注：

- 是否破坏 Control Plane / Data Plane 边界（be 不执行 Agent；fe 不直连 sphere）
- 六层依赖是否越界（`adapter → application → {client, domain, infra}`）
- API 契约、多租户头（`X-Workspace-Num`）、鉴权与审计是否完整
- 测试与文档是否跟上

## 文档贡献

欢迎改进 README、架构说明、子工程 docs。  
口头约定对后来者不友好：架构决策请落到对应子工程 `docs/design-docs/`，复杂进度可写入 `docs/exec-plans/`。

## 许可

贡献代码即表示你同意以本仓库 [Apache License 2.0](LICENSE) 授权你的贡献（含对既有版权所有者的许可）。  
除非另有说明，你提交的内容默认按 Apache-2.0 发布。

## 需要帮助？

- 使用问题 → [SUPPORT.md](SUPPORT.md)
- 安全问题 → [SECURITY.md](SECURITY.md)
- 行为准则 → [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
