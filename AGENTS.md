# agent-ops — Agent 入口地图

> Agent Sphere / AgentOps 本地工作区：Control Plane 管理后台前后端合集。
> 顶层只有两个独立工程：`agent-be`（rd-agent-be）+ `agent-fe`（rd-agent-fe）。
> 此文件是工作区级**目录**，不是百科全书。人类入口见 [README.md](README.md)；架构边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。
> 子项目细节请跳进对应仓库的 AGENTS.md。

---

## 1. 一句话定位

本目录是 **Agent Sphere 管理后台（AgentOps）** 的本地聚合根：统一管理 Agent / Skill / Prompt / 工具 / 模型 / 沙箱 / 评测 / 权限，并提供调试台。  
**不负责真正执行 Agent**——执行面在外部 Data Plane `rd-points-sphere`，由 `agent-be` 透传；前端只调 `agent-be`。

| 调用方 | 入口 | 说明 |
|--------|------|------|
| 浏览器 / 管理端 | `agent-fe` → `agent-be` Admin API | Cookie JWT + `X-Workspace-Num` |
| 业务侧（如 rd-points-platform） | `agent-be` Internal API | `X-Service-Token` 拉 active prompt 等 |
| Agent 运行时 | `rd-points-sphere`（本工作区外） | be 透传，fe 不直连 |

## 2. 顶层结构

```
agent-ops/
├── README.md                 ← 人类入口
├── AGENTS.md                 ← 你在这里（工作区入口地图）
├── CLAUDE.md                 ← 软链接 → AGENTS.md
├── ARCHITECTURE.md           ← 工作区架构边界（fe/be/sphere）
├── agent-be/                 ← Control Plane 后端（rd-agent-be）
│   ├── AGENTS.md             ← 后端业务入口
│   ├── CLAUDE.md             ← → AGENTS.md（落码规范指针见文内）
│   ├── ARCHITECTURE.md
│   ├── docs/                 ← 后端知识库
│   ├── doc/                  ← 中文产品/技术方案/用户手册
│   └── rd-agent-be-{facade,client,domain,infra,application,adapter}/
└── agent-fe/                 ← 管理后台前端（rd-agent-fe）
    ├── AGENTS.md             ← 前端业务入口
    ├── CLAUDE.md             ← → AGENTS.md
    ├── ARCHITECTURE.md
    ├── docs/                 ← 前端知识库
    └── src/                  ← pages / components / hooks / services / stores …
```

无根级 `pom.xml` / `package.json`：前后端各自独立构建、独立 CI。

## 3. 子项目速览

### 3.1 agent-be（后端）

- **技术栈**: Java 17 + Spring Boot 3.5，六层 Maven（facade / client / domain / infra / application / adapter）
- **角色**: Control Plane — 鉴权、工作空间、资产版本、会话元数据、评测、审计、透传 sphere
- **业务域**（跨层同名包）: `auth` / `workspace` / `agent` / `skill` / `skillcheck` / `prompt` / `tool` / `model` / `sandbox` / `session` / `evaluation` / `debugconsole` …
- **默认端口**: `8081`
- **入口文档**: [agent-be/AGENTS.md](agent-be/AGENTS.md) · [agent-be/ARCHITECTURE.md](agent-be/ARCHITECTURE.md)

### 3.2 agent-fe（前端）

- **技术栈**: React 19 + Ant Design 6 + Vite + pnpm（Pro Components / Ant Design X）
- **角色**: 管理 UI — 工作空间门户 + 工作区内资产/调试/评测/权限
- **主要页面**: Workspaces / Agents / Console / Skills / Evaluation / Prompts / Tools / Models / Sandboxes / Roles / Permission / Login
- **开发**: `pnpm dev`（代理 `/api` → be）；配置见 `agent-fe/env/`
- **入口文档**: [agent-fe/AGENTS.md](agent-fe/AGENTS.md) · [agent-fe/ARCHITECTURE.md](agent-fe/ARCHITECTURE.md)

## 4. 核心约束（工作区级，不可违反）

1. **Control Plane / Data Plane 边界**: be **不执行** agent；fe **不直连** sphere；所有执行与流式数据走 be → sphere。
2. **改哪里读哪里**: 只动后端时读 `agent-be/AGENTS.md`；只动前端时读 `agent-fe/AGENTS.md`；跨 fe/be 契约变更两边都要核对，并同步共享约定（若存在 `rd-agent-ws/docs/CONVENTIONS.md`）。
3. **六层依赖（be）**: `adapter → application → {client, domain, infra}`；`infra → domain → facade`，禁止反向越界。
4. **API 契约**: Admin 面统一 `Result` + JWT/Cookie；多租户请求带 `X-Workspace-Num`；写操作需可审计。
5. **分支安全**: 禁止直接在 `master` / `test` / `stag` / `prod` 等环境分支改代码；先走 `feature-*` / `hotfix-*`。
6. **质量闭环**: 改完必须在对应工程跑验证命令（见第 6 节），全绿才算完成；UI 改动还需浏览器走主流程。
7. **知识沉淀**: 口头/聊天约定对 agent 不可见；架构决策与复杂进度分别落到子项目 `docs/design-docs/`、`docs/exec-plans/active/`。

## 5. Lookup Table — 想知道 X，去看 Y

| 想了解 | 去看 |
|--------|------|
| **工作区架构边界 / fe↔be↔sphere** | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **人类快速开始** | [README.md](README.md) |
| **后端怎么改 / 六层落码** | [agent-be/AGENTS.md](agent-be/AGENTS.md) · [agent-be/ARCHITECTURE.md](agent-be/ARCHITECTURE.md) |
| **前端怎么改 / 分层与页面** | [agent-fe/AGENTS.md](agent-fe/AGENTS.md) · [agent-fe/ARCHITECTURE.md](agent-fe/ARCHITECTURE.md) |
| 后端编码规范 / 安全 / 可靠性 / 部署 | [agent-be/docs/](agent-be/docs/) |
| 前端编码约定 / 开发规范 / 安全 | [agent-fe/docs/CONVENTIONS.md](agent-fe/docs/CONVENTIONS.md) · [agent-fe/docs/FE_DEVELOP_STANDARD.md](agent-fe/docs/FE_DEVELOP_STANDARD.md) |
| 中文产品 / 技术方案 / 用户手册 | [agent-be/doc/](agent-be/doc/) |
| 后端业务域实现 | `agent-be/rd-agent-be-*/src/.../<domain>/` |
| 前端 API client | [agent-fe/src/services/](agent-fe/src/services/) |
| 前端页面 | [agent-fe/src/pages/](agent-fe/src/pages/) |
| Figma 原型 | [AgentSphere (hoQIHy1FcQdfE49oDsiSV9)](https://www.figma.com/design/hoQIHy1FcQdfE49oDsiSV9/AgentSphere?node-id=0-1) |
| 完整 PRD（若本机另有 checkout） | `docs/agent-sphere/PRD-MVP.md`（相对兄弟目录，本工作区可能不存在） |

## 6. 验证闭环

按改动范围在对应工程执行：

```bash
# 后端
cd agent-be
mvn -DskipTests clean package
mvn test

# 前端
cd agent-fe
pnpm install          # 首次或依赖变更时
pnpm typecheck        # 或 pnpm build 中的 tsc
pnpm test             # Vitest
# UI 改动：pnpm dev → 浏览器走一遍主流程
```

跨端改接口时：**先定契约 → 两边同步 → 各自验证**，不要只改一侧。

## 7. 元规则（Agent 工作准则）

- **写代码前先输出实现方案**（用户明确说「直接写」或单行 typo 除外）
- **优先最小改动**: 只改任务需要的文件；不要顺手大重构
- **子项目规范优先**: 本文件管工作区地图；具体落码、测试、注释规则以 `agent-be` / `agent-fe` 内文档为准
- **跨端任务**: 明确影响面（fe / be / 两者），并在回复里说明验证范围
- **变更必带测试**: 业务代码新增或修改必须同步测试；缺测不算完成

## 8. 额外说明

- 本工作区**不包含** `rd-points-sphere`、`rd-points-platform`、`rd-agent-ws` 源码；文档中的相对路径可能指向其他 checkout。
- 外部依赖常见：Nacos、Redis、DB（schema `rd_agent`）、SkyWalking、Sentry。配置走 Spring YAML，不用 Apollo。SSO/GCAC 已移除；本地可用 `app.auth.disable-auth`。
- 两边 README / AGENTS 里偶有历史路径（如 `/admin/api/v1`）与现网 Controller（`/api/v1/...`）不完全一致时，**以代码为准**。

---

**这份文件应保持在 ~120 行以内。** 细则放进 `agent-be/docs/` 或 `agent-fe/docs/`，此处只维护指针与工作区边界。
