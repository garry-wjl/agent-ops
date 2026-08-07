# rd-agent-fe

> Agent Sphere 管理后台前端 — 调试台 / 智能体管理 / 提示词管理。

**技术栈:** React 19 + Antd 6 + Vite + pnpm(拷自 rd-points-fe 架构)

---

## 给人类的入口

这是一个 **agent-first** 的项目骨架。所有结构、约束和工作流都是为了让 AI agent 能高效推理整个仓库。

**如果你是 agent**:去看 [AGENTS.md](AGENTS.md)。

**如果你是人类,第一次进入**:

1. 读 [AGENTS.md](AGENTS.md) 理解整体地图
2. 读 [ARCHITECTURE.md](ARCHITECTURE.md) 理解分层和依赖规则
3. 浏览 [docs/](docs/) — 尤其 [docs/CONVENTIONS.md](docs/CONVENTIONS.md)(编码约定) 和 [docs/FE_DEVELOP_STANDARD.md](docs/FE_DEVELOP_STANDARD.md)(前端开发规范)
4. 完整产品需求见 [PRD-MVP.md](../docs/agent-sphere/PRD-MVP.md)
5. 设计原型见 [Figma hoQIHy1FcQdfE49oDsiSV9](https://www.figma.com/design/hoQIHy1FcQdfE49oDsiSV9/AgentSphere?node-id=0-1)

## 快速开始

```bash
nvm use           # Node 24+（见 .nvmrc）
pnpm install      # 安装依赖
pnpm dev          # 启动开发服务器 → http://localhost:3000
```

如需连接后端 API，在 `env/.env.develop` 中配置 `VITE_API_BASE_URL`。

## 验证

```bash
pnpm lint             # 代码检查（Oxlint）
pnpm format:check     # 格式检查（Oxfmt）
pnpm build            # 生产构建
```

UI 改动：必须在浏览器实际跑一遍主流程，不能只靠 typecheck 通过就声明完成。

## 文档

详见 [docs/](docs/) 目录。`AGENTS.md` 第 5 节有完整的 Lookup Table。

## 贡献

- 写代码前请先输出实现方案
- 任何架构层面的决定都要落到 `docs/design-docs/` 里
- 复杂任务请在 `docs/exec-plans/active/` 留下计划
- UI 改动请对照 Figma `hoQIHy1FcQdfE49oDsiSV9` 验证
- 跨 fe/be 接口契约改动需要回到 [`../rd-agent-ws/docs/CONVENTIONS.md`](../rd-agent-ws/docs/CONVENTIONS.md) 同步

---

> 本骨架由 [`harness-init`](https://openai.com/index/harness-engineering/) skill 生成。
