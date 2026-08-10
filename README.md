# agent-ops

[![CI](https://github.com/garry-wjl/agent-ops/actions/workflows/ci.yml/badge.svg)](https://github.com/garry-wjl/agent-ops/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Issues](https://img.shields.io/github/issues/garry-wjl/agent-ops)](https://github.com/garry-wjl/agent-ops/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

> Agent Sphere / AgentOps — 企业级 Agent 运行管理平台的 **Control Plane**（管理后台前后端合集）。

**组成:** `agent-be`（rd-agent-be）+ `agent-fe`（rd-agent-fe）

统一管理 Agent / Skill / Prompt / 工具 / 模型 / 沙箱 / 评测 / 权限，并提供调试台。  
**本仓库不负责真正执行 Agent**——执行面在外部 Data Plane；前端只调用 `agent-be`。

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
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献指南 |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | 行为准则 |
| [SECURITY.md](SECURITY.md) | 安全漏洞披露 |
| [SUPPORT.md](SUPPORT.md) | 获取帮助的渠道 |
| [MAINTAINERS.md](MAINTAINERS.md) | 维护者 |
| [LICENSE](LICENSE) / [NOTICE](NOTICE) | Apache-2.0 |
| [agent-be/](agent-be/) | Control Plane 后端 |
| [agent-fe/](agent-fe/) | 管理后台前端 |

## 社区与贡献

欢迎 Issue、Discussion 与 Pull Request。

- 贡献流程与分支约定 → [CONTRIBUTING.md](CONTRIBUTING.md)
- Bug / 功能 / 文档模板 → [New Issue](https://github.com/garry-wjl/agent-ops/issues/new/choose)
- 安全问题（请勿公开）→ [SECURITY.md](SECURITY.md)
- 行为准则 → [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- 维护者 → [MAINTAINERS.md](MAINTAINERS.md)

核心约定（摘要）：

- 从最新 `main` 拉 `feature-*` / `hotfix-*` / `docs-*` 分支，禁止直接改环境分支
- 业务代码变更必须同步测试；跨 fe/be 契约两边同步验证
- 遵守 Control Plane / Data Plane 边界与后端六层依赖方向

## License

本项目采用 [Apache License 2.0](LICENSE)。  
版权与第三方组件声明见 [NOTICE](NOTICE)。
