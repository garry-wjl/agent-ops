# agent-ops

> Agent Sphere / AgentOps 本地工作区 — Control Plane 管理后台前后端合集。

**组成:** `agent-be`（rd-agent-be）+ `agent-fe`（rd-agent-fe）

---

## 给人类的入口

这是一个 **agent-first** 工作区：根目录只放地图与边界，细则在子工程里。

**如果你是 agent**: 去看 [AGENTS.md](AGENTS.md)。

**如果你是人类, 第一次进入**:

1. 读 [AGENTS.md](AGENTS.md) 理解工作区地图与约束
2. 读 [ARCHITECTURE.md](ARCHITECTURE.md) 理解 Control Plane / Data Plane 与 fe↔be 边界
3. 按任务进入子工程:
   - 后端 → [agent-be/README.md](agent-be/README.md) · [agent-be/AGENTS.md](agent-be/AGENTS.md)
   - 前端 → [agent-fe/README.md](agent-fe/README.md) · [agent-fe/AGENTS.md](agent-fe/AGENTS.md)
4. 中文产品/技术方案见 [agent-be/doc/](agent-be/doc/)
5. Figma 原型: [AgentSphere](https://www.figma.com/design/hoQIHy1FcQdfE49oDsiSV9/AgentSphere?node-id=0-1)

## 快速开始

前后端独立启动，无根级统一脚本。

### 后端 (agent-be)

```bash
cd agent-be
# 需 JDK 17；配置见 application.yml / application-dev.yml（无 Apollo）
mvn -pl rd-agent-be-adapter spring-boot:run
# → http://localhost:8081
```

### 前端 (agent-fe)

```bash
cd agent-fe
nvm use            # Node 见 .nvmrc
pnpm install
pnpm dev           # 开发服务器；/api 代理到 be:8081
```

连接后端时在 `agent-fe/env/.env.develop` 配置 `VITE_API_BASE_URL`（或依赖 Vite 代理）。

## 验证

```bash
# 后端
cd agent-be && mvn -DskipTests clean package && mvn test

# 前端
cd agent-fe && pnpm typecheck && pnpm test
# UI 改动还需浏览器走主流程
```

## 文档地图

| 文档 | 用途 |
|------|------|
| [AGENTS.md](AGENTS.md) | Agent 工作区入口（Lookup Table） |
| [CLAUDE.md](CLAUDE.md) | → AGENTS.md |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 工作区架构边界 |
| [agent-be/](agent-be/) | Control Plane 后端 |
| [agent-fe/](agent-fe/) | 管理后台前端 |

## 贡献

- 写代码前先输出实现方案（除非明确「直接写」）
- 只改一侧时遵守该侧 `AGENTS.md`；跨 fe/be 契约两边同步验证
- 架构决策落到对应子工程 `docs/design-docs/`
- 复杂任务在对应子工程 `docs/exec-plans/active/` 留计划
- 禁止直接在 `master` / `test` / `stag` / `prod` 上改代码
