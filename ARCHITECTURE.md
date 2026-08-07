# agent-ops — Architecture

> 工作区级架构边界：什么属于本目录、fe/be 如何协作、与 Data Plane 的职责切分。
> 这份文件描述**什么允许做、什么不允许做**，不描述子工程内部怎么实现。
> 后端六层细则 → [agent-be/ARCHITECTURE.md](agent-be/ARCHITECTURE.md)
> 前端分层细则 → [agent-fe/ARCHITECTURE.md](agent-fe/ARCHITECTURE.md)
> 业务入口地图 → [AGENTS.md](AGENTS.md)

---

## 1. 一图看懂

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器 (Chrome / Edge)                                          │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTPS / 本地代理 /api
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  agent-fe (rd-agent-fe)                                          │
│  React 19 + Antd 6 + Vite                                        │
│  工作空间门户 + Agent/Skill/Prompt/工具/模型/沙箱/评测/权限/调试台 │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Cookie JWT + X-Workspace-Num
                               │ 统一 Result 响应
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  agent-be (rd-agent-be) — Control Plane                          │
│  Java 17 + Spring Boot 3.5，六层 Maven                           │
│  鉴权 / 工作空间 / 资产版本 / 会话元数据 / 评测 / 审计 / 透传     │
│  Admin API (人)  +  Internal API (服务, X-Service-Token)         │
│  DB schema rd_agent  ·  Redis prefix rd_agent:                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │ 内网 REST 透传
                               ▼
                    ┌──────────────────────┐
                    │  rd-points-sphere    │  Data Plane（本工作区外）
                    │  无状态执行 Agent     │
                    └──────────────────────┘
```

另有业务侧（如 `rd-points-platform`）可经 be 的 Internal API 拉取 active prompt 等，**不经过 fe**。

## 2. 工作区组成

| 目录 | 工程名 | 职责 | 不负责 |
|------|--------|------|--------|
| `agent-fe/` | rd-agent-fe | 管理 UI、调 Admin API | 直连 sphere、持有业务密钥 |
| `agent-be/` | rd-agent-be | Control Plane：资产、权限、审计、透传 | 真正跑 Agent 推理循环（属 sphere） |

本目录**没有**根级 monorepo 构建；无共享 `pom.xml` / `package.json`。  
本目录**不包含** sphere / platform / `rd-agent-ws` 源码；文档中的兄弟路径可能指向其他 checkout。

## 3. 强制边界（不可违反）

1. **fe 只依赖 be**: 所有数据（含调试台流式）走 `agent-be`；fe 不感知 sphere URL。
2. **be 不执行 agent**: sphere 相关执行只透传或按协议拉取；Control Plane 管配置与元数据。
3. **契约变更双边同步**: 改 Admin API / `Result` 字段 / 头约定时，同时改 `agent-fe/src/services` 与 be Controller/VO，并各自跑验证。
4. **多租户头**: 工作区内请求带 `X-Workspace-Num`（workspace / login / 平台级接口除外，以代码为准）。
5. **以代码为准**: README / 旧文档路径若与现网 Controller（多为 `/api/v1/...`）冲突，以代码为准。
6. **子工程内部规则不在此展开**: be 六层依赖、fe pages/services 分层分别见两侧 `ARCHITECTURE.md`。

## 4. 能力域对照（fe ↔ be）

| 能力 | fe（pages / services） | be（跨层同名域） |
|------|------------------------|------------------|
| 鉴权 | `Login` / `auth` | `auth`（JWT Cookie；本地可 `disable-auth`） |
| 工作空间 | `Workspaces` / `workspace` | `workspace` |
| Agent | `Agents` / `agent` | `agent` |
| 调试台 | `Console` | `debugconsole` + session / runner |
| Skill | `Skills` / `skill` | `skill` / `skillcheck` |
| Prompt | `Prompts` / `prompt` | `prompt` |
| 工具 | `Tools` / `tool` | `tool` |
| 模型 | `Models` / `model` | `model` |
| 沙箱 | `Sandboxes` / `sandbox` | `sandbox` |
| 评测 | `Evaluation` / `evaluation` | `evaluation` |
| 权限 / 角色 | `Permission` / `Roles` / `authz` | `auth`（roles 等） |
| 会话 | `session` service | `session` |

## 5. 依赖与外部系统（工作区视角）

| 依赖 | 谁用 | 说明 |
|------|------|------|
| JWT Cookie / disable-auth | be（fe `/login`） | 管理端鉴权；SSO/GCAC 已移除 |
| Spring YAML | be | `application.yml` / `application-{profile}.yml`（+ 环境变量占位） |
| DB / Redis | be | schema / key 前缀 `rd_agent` |
| Nacos / A2A 等 | be | Agent 发现与同步（见 be 文档） |
| rd-points-sphere | be | Data Plane 透传目标 |
| Sentry / SkyWalking / CI | fe 与/或 be | 观测与发布，见各侧 DEPLOYMENT |

## 6. 文档归属

| 内容 | 放哪里 |
|------|--------|
| 工作区地图 / Lookup | 根 [AGENTS.md](AGENTS.md) |
| 工作区边界（本文） | 根 `ARCHITECTURE.md` |
| be 落码 / 六层 / ArchUnit | `agent-be/` |
| fe 分层 / UI 约定 / Figma | `agent-fe/` |
| 产品 PRD / 技术方案（中文长文） | `agent-be/doc/` |
| 设计决策 / 执行计划 | 对应子工程 `docs/design-docs/` · `docs/exec-plans/` |

---

跨端架构决议先写进本文件或双方 `docs/design-docs/`，再改代码；子工程内部决议不必上提到根。
