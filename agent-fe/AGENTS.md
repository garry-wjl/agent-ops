# rd-agent-fe — Agent 入口地图

> Agent Sphere 管理后台前端 — 调试台 / 智能体管理 / 提示词管理。
> 技术栈:React 19 + Antd 6 + Vite + pnpm(拷自 rd-points-fe 架构)
> 此文件是 agent 的**目录**,不是百科全书。想知道 X,请按下方 Lookup Table 跳转。

---

## 1. 项目一句话定位

Agent Sphere 管理后台的前端,提供 4 个一级菜单:**控制台(调试台)/ 智能体管理 / 提示词管理 / 系统管理(v0.2)**。所有数据来源于 rd-agent-be(无直连 sphere)。设计风格:扁平化(无内部分隔线、无卡片阴影、纯白底),调试台为 ChatGPT 风格对话流。

<!-- 详细产品需求见 PRD-MVP §4 + Figma 原型 hoQIHy1FcQdfE49oDsiSV9 -->

## 2. 顶层结构

```
rd-agent-fe/
├── AGENTS.md                  ← 你在这里
├── CLAUDE.md                  ← 软链接 → AGENTS.md
├── ARCHITECTURE.md            ← 顶层架构与依赖规则
├── docs/                      ← 知识库(system of record)
└── <代码目录,工程骨架待定 — 拷自 rd-points-fe 架构>
   <!-- TODO: 切片 1 工程骨架就位后,补充 src/ 目录树
        参考 rd-points-fe 的 pages / components / hooks / services / types 分层 -->
```

## 3. 核心约束(不可违反)

1. **不直连 sphere**:所有 sphere 数据走 rd-agent-be 透传,fe 不感知 sphere 存在(只感知 be 的 API)→ [ARCHITECTURE.md](ARCHITECTURE.md)
2. **设计还原**:页面布局必须对齐 Figma `hoQIHy1FcQdfE49oDsiSV9`,扁平化风格,Noto Sans SC 字体,主色 `#2B52D9` → [docs/CONVENTIONS.md](docs/CONVENTIONS.md)
3. **调试台无业务字段**:调试台只承载通用 sphere agent 调用,**禁止**出现 Jira / 工分 / 复杂度评估等业务字段(见 PRD §4.2)
4. **权限 UI 兜底**:viewer 用户访问 editor 操作 → 按钮 disabled + tooltip,不靠后端拦截后再抛错
5. **质量闭环**:每次写代码后必须执行验证命令(见第 6 节),失败自我修正
6. **单元测试强制**:任何业务代码新增或修改必须同步编写/更新单元测试,测试通过后才算任务完成

## 4. 项目 Skill / Slash Command(按场景调用)

<!-- TODO: 工程骨架就位后补充。可参考 rd-points-fe 的 .claude/commands/(若存在) -->

(暂无项目级命令)

## 5. Lookup Table — 想知道 X,去看 Y

| 想了解                                | 去看                                                                                                                |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 顶层架构 / 分层规则 / 依赖方向        | [ARCHITECTURE.md](ARCHITECTURE.md)                                                                                  |
| 设计总章与设计决策档案                | [docs/DESIGN.md](docs/DESIGN.md) → [docs/design-docs/](docs/design-docs/)                                           |
| 产品规格 / 业务需求                   | [docs/product-specs/index.md](docs/product-specs/index.md)(原始 PRD 在 `../docs/agent-sphere/PRD-MVP.md`)           |
| 当前进行中的执行计划                  | [docs/PLANS.md](docs/PLANS.md) → [docs/exec-plans/active/](docs/exec-plans/active/)                                 |
| 技术债清单                            | [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md)                                        |
| TypeScript 类型 / 自动生成 API client | [docs/generated/](docs/generated/)                                                                                  |
| 质量评分卡                            | [docs/QUALITY_SCORE.md](docs/QUALITY_SCORE.md)                                                                      |
| 可靠性 / 错误展示 / 流式轮询策略      | [docs/RELIABILITY.md](docs/RELIABILITY.md)                                                                          |
| 安全约束(JWT / XSS / 敏感字段)        | [docs/SECURITY.md](docs/SECURITY.md)                                                                                |
| 产品品味与取舍偏好                    | [docs/PRODUCT_SENSE.md](docs/PRODUCT_SENSE.md)                                                                      |
| 部署原则(独立子域 / Sentry)           | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)                                                                            |
| **编码约定 / 前端配置**               | [docs/CONVENTIONS.md](docs/CONVENTIONS.md)                                                                          |
| **前端开发规范(详细版)**              | [docs/FE_DEVELOP_STANDARD.md](docs/FE_DEVELOP_STANDARD.md)                                                          |
| 经验沉淀(踩过的坑)                    | [docs/PATTERNS.md](docs/PATTERNS.md)                                                                                |
| 质量自检                              | [docs/QUALITY.md](docs/QUALITY.md)                                                                                  |
| 外部依赖文档(LLM 友好版)              | [docs/references/](docs/references/)                                                                                |
| Agent-first 操作原则                  | [docs/design-docs/core-beliefs.md](docs/design-docs/core-beliefs.md)                                                |
| 跨 fe/be 共享约定                     | [../rd-agent-ws/docs/CONVENTIONS.md](../rd-agent-ws/docs/CONVENTIONS.md)                                            |
| 后端入口(rd-agent-be)                 | [../rd-agent-be/AGENTS.md](../rd-agent-be/AGENTS.md)                                                                |
| **Figma 原型**                        | [hoQIHy1FcQdfE49oDsiSV9 - AgentSphere](https://www.figma.com/design/hoQIHy1FcQdfE49oDsiSV9/AgentSphere?node-id=0-1) |

## 6. 验证闭环

```bash
# TODO: 工程骨架就位后填入。参考 rd-points-fe:
#   pnpm install
#   pnpm typecheck && pnpm lint
#   pnpm test                             # 单元测试（新增/修改代码必须覆盖）
#   pnpm dev
```

**必须**做到:写完代码 → 执行上述命令 → 全绿才算完成。失败自我修正,不要把脏代码留给下一轮。
**UI 改动**还要:启动 dev server 在浏览器实际操作一遍主流程,不能只靠 typecheck 通过就声明完成。

## 7. 元规则(Agent 工作准则)

- **写代码前必须先输出实现方案**(除非用户明确说"直接写"或单行 typo 修复)
- **Repository = System of Record**:知识必须 in-repo、versioned,Slack/钉钉/口头约定**对 agent 不可见**,遇到时立即沉淀进 `docs/`
- **进度持久化**:复杂任务必须在 `docs/exec-plans/active/` 创建一份 plan,进度同步更新
- **遇到坏味道立即修**:不要复制现有的 anti-pattern;发现 lint/规范缺口,反馈到 `docs/` 而不是绕开
- **Figma 是设计真相**:任何 UI 偏离 Figma 都需要在 PR 说明理由;Figma 节点 id 应该出现在相关组件的注释或 plan 里
- **PRD 是只读源**:本仓库不复制 PRD,所有产品需求引用 `../docs/agent-sphere/PRD-MVP.md`
- **变更必带测试**:任何业务代码新增或修改必须同步编写/更新单元测试,`pnpm typecheck && pnpm test` 全绿才算完成;测试缺位的不算完成

## 8. 额外说明

- 独立部署在 `agent.garry.internal`(待 ops 分配),与工分平台前端 `rd-points-fe` 完全分离;同一员工需在两边各 GT OAuth 登录一次(已决议接受,见 PRD §9)
- i18n 框架沿用 rd-points-fe 但 locale 文件**只填中文**(en-US 留空 v0.2 补)
- Sentry 沿用工分平台 DSN,但**新建 project**便于独立监控
- 浏览器兼容:Chrome / Edge 最新版(不支持 IE / Safari < 16)

---

**这份文件应保持在 ~120 行以内。** 任何具体规则的扩展都应放进 `docs/` 对应文件,本文件只更新指针。

> 本骨架由 [`harness-init`](https://openai.com/index/harness-engineering/) skill 于 2026-05-11 生成。
