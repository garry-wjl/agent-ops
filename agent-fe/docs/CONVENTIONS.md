# Conventions — 编码约定缓存

> 后续任务直接读缓存，发现偏差时更新。
> 详细编码规范见 [FE_DEVELOP_STANDARD.md](FE_DEVELOP_STANDARD.md)。

---

## 1. 技术栈

- **框架**：React 19
- **UI 库**：Ant Design 6（Antd）
- **构建**：Vite 7
- **包管理**：pnpm 9+
- **类型**：TypeScript（strict + noUncheckedIndexedAccess）
- **路由**：<!-- TODO: 切片 1 决定，候选 React Router / TanStack Router -->
- **数据获取**：<!-- TODO: 切片 1 决定，候选 React Query / SWR -->
- **状态管理**：<!-- TODO: 切片 1 决定，候选 Zustand / Jotai -->
- **i18n**：沿用 rd-points-fe 框架（react-i18next 或同类），locale 仅 zh-CN
- **样式**：Antd 主题变量 + CSS-in-JS；避免裸 css 文件
- **代码编辑器组件**：Monaco Editor（prompt 编辑器 + 调试台 JSON 模式）
- **测试**：<!-- TODO: vitest + @testing-library/react -->

## 2. 命名规范

| 类型                | 格式                                         | 示例                         |
| ------------------- | -------------------------------------------- | ---------------------------- |
| 功能模块目录        | `kebab-case`                                 | `user-profile/`              |
| 组件目录/文件       | `PascalCase`                                 | `AgentCard.tsx`              |
| 样式文件            | `camelCase.module.less`                      | `agentCard.module.less`      |
| Store / 工具 / 服务 | `camelCase.ts`                               | `global.ts`                  |
| 常量                | `SCREAMING_SNAKE_CASE`                       | `API_TIMEOUT`                |
| Props 类型          | `ComponentName + Props`                      | `AgentCardProps`             |
| 布尔变量            | `is/has/can/should` 前缀                     | `isVisible`, `hasPermission` |
| 事件处理            | `handle` 前缀                                | `handleSubmit`               |
| React Query hook    | `use[Resource]Query` / `use[Action]Mutation` | `useAgentListQuery`          |

## 3. 目录结构

```
src/
├── pages/              ← 页面级组件（与路由 1:1）
│   ├── Console/        ← 调试台
│   ├── Agents/         ← 智能体管理
│   ├── Prompts/        ← 提示词管理
│   ├── SystemSettings/ ← v0.2 灰显
│   ├── Login/
│   └── Errors/
├── components/         ← 通用 UI 组件
│   ├── Layout/         ← NavSidebar / TopBar / Breadcrumb
│   ├── Editor/         ← Monaco wrapper
│   ├── StreamCursor/   ← 流式光标
│   └── ...
├── hooks/              ← 业务逻辑钩子（useAgents / usePrompt / useRunStream）
├── services/           ← API client（对应 rd-agent-be Admin API）
├── stores/             ← 全局状态（用户 / 当前 thread）
├── types/              ← TypeScript 类型（部分自动生成）
├── utils/
├── i18n/               ← 仅 zh-CN
└── main.tsx
```

## 4. 文件组织

- 按功能/领域组织，不按文件类型
- services 层：接口 ≤10 个 → 单文件；较多 → 目录（`api.ts` + `hooks.ts` + `types.ts` + `index.ts`）
- 组件超过 300 行考虑拆分
- 文件上限 800 行

## 5. Import 顺序

1. 外部库（react、antd、第三方）
2. 路径别名（`@/`）
3. 相对路径（`./` 或 `../`）
4. 样式文件（`.module.less`，永远最后）

## 6. 组件约定

> 详细规范见 [FE_DEVELOP_STANDARD.md §5 React 组件规范](FE_DEVELOP_STANDARD.md#5-react-组件规范)

- 函数组件 + 具名函数 + default export
- Props 类型：`interface XxxProps {}`，组件名 + Props 后缀
- 事件处理：`handle` 前缀
- 自定义 Hook：`use` 前缀
- 文件夹内推荐 `index.ts` 重导出
- 复用组件**禁止**直接调 services 或读 stores，必须通过 props 接收数据 / hooks 注入
- 可访问性（a11y）：
  - 所有交互元素必须可键盘 tab 访问
  - icon-only 按钮必须有 `aria-label`
  - Antd 默认 a11y 即可，自定义组件参考 WCAG 2.1 AA

## 7. 状态管理

| 状态类型                  | 存放位置                      | 例子                                |
| ------------------------- | ----------------------------- | ----------------------------------- |
| 服务端状态（列表 / 详情） | React Query / SWR 缓存        | agents 列表、prompt 详情            |
| 跨页面状态                | 全局 store                    | 当前用户、当前 thread、当前流式 run |
| 页面内状态                | useState / useReducer         | 表单输入、modal 开关                |
| URL 状态                  | router params / search params | run_id、agent_id、当前 tab          |

> 原则：能放本地不放全局，能放 URL 不放 store（便于刷新 / 分享 URL）

## 8. 样式与主题

- Antd 主题变量：主色 `#2B52D9`（沿用 PRD §4.2 / Figma）
- 字体：Noto Sans SC（全局）
- 文本 5 档：`#0F172B / #1E293B / #475569 / #64748B / #90A1B9`
- 调试台 step 类型颜色：text 蓝 `#2B52D9` / thinking 紫 `#7C3AED` / tool_use 绿 `#10B981` / tool_result 橙 `#F59E0B`
- 设计风格：**扁平化**（无内部分隔线 / 无卡片阴影 / 纯白底）
- Antd 定制只通过 `styles/theme.ts` token，禁止覆盖 `.ant-*` 类名
- 不允许：多套主题、动态主题切换（MVP 仅亮色）

## 9. 表单与校验

- 用 Antd Form + 内置 rules
- 校验失败错误显示在 input 下方（内联），不弹 modal
- 提交按钮在请求中显示 loading + disabled
- 关键操作（activate / rollback / 删除）弹 Antd Modal.confirm 二次确认

## 10. 后端 API 交互约定

> 详细规范见 [FE_DEVELOP_STANDARD.md §7 数据请求](FE_DEVELOP_STANDARD.md#7-数据请求)

- 响应信封 `{ code, message, data }` 由 `request.ts` 拦截器自动解包，service 函数直接拿 `data`
- API 函数用 `get`/`post`/`patch`/`del` 快捷函数，聚合到 `xxxApi` 对象
- Query Key 工厂与 `xxxApi` 放同一个 `api.ts`，层级式命名（资源名 → 操作 → 参数）
- React Query hook 命名：查询 `use{Resource}Query`，变更 `use{Action}Mutation`
- 类型字段跟随后端 snake_case，前端不做转换
- 简单模块单文件，复杂模块拆 `api.ts` + `hooks.ts` + `types.ts` + `index.ts`
- API client 类型由后端 OpenAPI 自动生成到 `docs/generated/`，减少手写错误

### 与后端的契约

| 项       | 约定                                                                         |
| -------- | ---------------------------------------------------------------------------- |
| 协议     | HTTPS                                                                        |
| 鉴权     | JWT Bearer（由 HttpOnly Cookie 携带）+ axios 拦截器自动注入                  |
| 路径前缀 | `/admin/api/v1/*`                                                            |
| 错误响应 | `{ code: string, message: string, request_id: string }`，fe 直接展示 message |
| 时间字段 | UTC ISO 8601，fe 用 dayjs 转本地时区展示                                     |
| 列表分页 | `?page=1&perPage=20`，响应 `{ data: [], total: N, page, perPage }`           |
| 流式接口 | `GET /admin/api/v1/runs/{id}/steps?seqAfter=...`，1.5s 轮询                  |

## 11. 路由与代码分割

- 页面级组件用 `React.lazy` + `Suspense` 按需加载
- 提示词编辑器 Monaco bundle 按需加载（进入 Prompts/Edit 时才加载）
- 路由级 fallback：统一骨架屏组件（避免空白闪烁）

## 12. 错误处理

- Axios 拦截器统一处理 HTTP 错误
- 组件级错误由 ErrorBoundary 兜底
- 表单校验使用 Ant Design Form 内建规则

## 13. 国际化约定

- locale 仅 zh-CN（MVP 不做多语言）
- 翻译 key 使用点号层级：`page.user.title`，`component.table.empty`
- 组件内用 `useIntl()` 的 `formatMessage`

## 14. 性能预算

- 首屏 JS gzipped < 300KB（超出 CI fail）
- 单页 LCP < 2s（局域网）
- 列表组件超过 100 行启用虚拟滚动（react-window 或 Antd Table virtual）
- 图片 lazy-load + 合理尺寸（避免大图首屏）

## 15. 可访问性（a11y）

- WCAG 2.1 AA（参考标准，不强制全覆盖）
- 键盘导航：所有交互可 tab 到达 + Enter 触发
- 屏幕阅读器：icon-only 按钮 `aria-label`
- 颜色对比度 ≥ 4.5:1（已在设计阶段保证）

## 16. 测试约定

<!-- TODO: 切片 1 决定测试栈。建议 vitest + @testing-library/react -->

- 单元测试：hooks / services / utils（必须）
- 组件测试：复杂交互组件（表单、流式状态机）
- E2E 测试：MVP 不强制，后续 Playwright / Cypress

## 17. 日志约定

<!-- TODO: 前端日志/错误上报策略 -->

- TODO

---

## 缓存维护

- **首次填充**：agent 进入项目后扫描代码风格自动生成，人类复核
- **更新时机**：发现代码实际风格和缓存不一致时、引入新框架/库时、团队约定变更时
- **更新方式**：直接修改对应章节，不要追加到末尾造成重复

---

> 任何 PR review 中反复出现的"我们一般会怎样做"都应该沉淀到本文件。
