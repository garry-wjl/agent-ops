# Core Beliefs — Agent-first 操作原则(rd-agent-fe 版本)

> 这份文件是 rd-agent-fe 特有的"agent 工作原则"。它影响 agent 在做技术决策、写代码、做 review 时的偏好。

---

## 通用原则(源自 OpenAI Harness Engineering)

1. **Repository = System of Record**:仓库是唯一真相源,Slack/钉钉/口头约定对 agent 不可见
2. **AGENTS.md 是地图,不是说明书**:入口文件越短越好(本仓库限 ≤ 120 行)
3. **Agent legibility first**:为 agent 可读性优化(命名、目录、文档结构)
4. **Enforce invariants, not implementations**:用代码强制边界(ESLint / TypeScript / dependency-cruiser),不微观管理实现
5. **Garbage collection > big refactor**:持续小修小补,不堆债再大爆发
6. **进度持久化**:复杂任务必须留下 plan 在 `docs/exec-plans/active/`
7. **写代码前先输出方案**:除非用户明确说"直接写"或单行 typo
8. **遇坏味道立即修**:不复制现有 anti-pattern,发现规范缺口反馈到 docs

## 本项目特有的原则

- **不直连 sphere**:fe 只感知 rd-agent-be,任何 sphere URL / 协议出现在 fe 代码里都是 bug
- **Figma 是设计真相**:任何与 Figma 不一致的实现都需要 PR 中说明理由;Figma 节点 id 应该出现在相关组件注释 / plan 里
- **调试台无业务字段**:**绝对**不在调试台引入 Jira / 工分 / 复杂度评估等业务字段(PRD §4.2);如果出现这种诉求,push back 并指向智能体特定页面
- **扁平化优先**:任何"加边框 / 加阴影 / 加分隔线"的提议默认拒绝;已是设计共识
- **服务端状态走数据获取库,不进 store**:列表 / 详情用 React Query / SWR;只有"跨页面用户态、当前 thread、流式 run"进全局 store
- **Parse, don't validate**:services 边界用 zod / yup 解析后端响应,不裸 `as Foo`
- **可访问性兜底**:icon-only 按钮必须有 `aria-label`;键盘 tab 路径必须可走完;颜色不是唯一信息载体
- **不复制 PRD**:PRD 永远在 `../docs/agent-sphere/PRD-MVP.md`,本仓库只引用不复制
- **拷自 rd-points-fe 不等于直接复制**:架构 / 工具链对齐,但**业务页面禁止盲目对齐**(SphereDebug 是参考,Console 是按 ChatGPT 风格重新设计的)
- **UI 验证不能省**:typecheck + lint + test 都过不等于功能正确;UI 改动必须在浏览器跑通主流程

## 与 rd-points-fe 拷贝架构的注意事项

> rd-agent-fe 拷自 rd-points-fe 架构,但要避免以下陷阱:

- 不要把 rd-points-fe 的工分业务页面(任务 / 看板 / 异常事件 / 组织架构)复制过来 — 它们和 agent sphere 无关
- 可以复制:Layout 骨架、GT OAuth callback 流程、axios 拦截器封装、Sentry 接入、i18n 配置、ESLint / Vite 配置、CI 模板
- SphereDebug 页面**不是**直接拷贝 — Console 是按 ChatGPT 风格重新设计(PRD §4.2 v0.2 修订)
- 旧 SphereDebug 的代码可以参考实现细节(轮询逻辑 / step 渲染),但 UI 形态完全不同

---

> 任何 PR review 中反复出现的"我们一般会怎样做"都应该沉淀到本文件,否则 agent 下次还会犯同样的错。
