# rd-agent-fe 前端项目代码规范

> AgentOps前端项目 — 基于 Vite + React 19 + Ant Design 6 的 SPA 应用。

---

## 1. 技术栈总览

| 维度 | 选型 | 版本 |
| --- | --- | --- |
| 构建工具 | Vite (rolldown) | 8.0.0 |
| 前端框架 | React + React DOM | 19.2.4 |
| 路由 | React Router DOM | 7.13.1 |
| UI 组件库 | Ant Design + @ant-design/pro-components | antd 6 / pro-components 3 |
| 状态管理 | Zustand | 5.0.5 |
| 数据请求 | React Query (TanStack) + Axios | 5.83.0 / 1.13.6 |
| 国际化 | react-intl | 10.0.0 |
| 工具库 | ahooks, classnames, react-markdown | — |
| 语言 | TypeScript | 5.9.3 |
| 样式 | Less | 4.6.4 |
| 代码规范 | oxlint + oxfmt + stylelint | — |
| Git 规范 | Husky + commitlint + lint-staged | — |

---

## 2. 目录结构

```
rd-agent-fe/
├── env/                          # 多环境配置
│   ├── .env.develop              # 开发环境
│   ├── .env.test                 # 测试环境
│   ├── .env.stag                 # 预发环境
│   └── .env.prod                 # 生产环境
├── scripts/                      # 构建 & 部署脚本
│   ├── apollo.ts                 # Apollo 相关脚本
│   └── deploy/                   # 部署相关
│       ├── index.ts
│       ├── util.ts
│       └── version-switch.ts
├── src/
│   ├── main.tsx                  # 应用入口（挂载 ErrorBoundary + App）
│   ├── App.tsx                   # 根组件（Router + Query + i18n Provider）
│   ├── components/               # 公共组件
│   │   ├── AppProvider.tsx       # 应用级 Provider（i18n + 主题 + 国际化）
│   │   ├── AuthGuard.tsx         # 路由权限守卫
│   │   ├── ErrorBoundary.tsx     # 全局错误边界
│   │   ├── IconRenderer/         # 图标注册与渲染
│   │   │   ├── index.tsx
│   │   │   └── registry.ts
│   │   ├── LocaleSwitcher.tsx    # 语言切换
│   │   └── NotificationConfig.tsx # 通知中心配置
│   ├── hooks/                    # 自定义 Hooks
│   │   └── index.ts              # 统一导出
│   ├── layouts/                  # 布局组件
│   │   └── BasicLayout.tsx       # ProLayout 侧边栏 + 顶栏布局
│   ├── locales/                  # 国际化语言包
│   │   ├── index.ts              # 聚合导出
│   │   ├── zh-CN/                # 中文
│   │   │   ├── component.ts
│   │   │   ├── menu.ts
│   │   │   └── pages.ts
│   │   ├── zh-CN.ts
│   │   ├── en-US/                # 英文
│   │   │   ├── component.ts
│   │   │   ├── menu.ts
│   │   │   └── pages.ts
│   │   └── en-US.ts
│   ├── pages/                    # 页面组件（按路由划分）
│   │   ├── 404.tsx               # 404 页面
│   │   ├── Agents/index.tsx      # Agent 管理
│   │   ├── Console/index.tsx     # 调试台
│   │   ├── Prompts/index.tsx     # Prompt 管理
│   │   └── Settings/index.tsx    # 系统设置
│   ├── router/                   # 路由系统
│   │   ├── index.ts              # 路由配置（参考 umi 格式）
│   │   ├── types.ts              # 路由类型定义
│   │   ├── loader.tsx            # 路由加载器（import.meta.glob 动态加载）
│   │   ├── hooks.tsx             # 路由菜单转换 Hooks
│   │   └── menus.ts              # 侧边栏菜单配置
│   ├── services/                 # 服务层（API + React Query Hooks）
│   │   ├── index.ts              # 统一导出 services 对象
│   │   ├── request.ts            # Axios 实例 & 拦截器
│   │   └── user/                 # 按业务域划分的 service
│   │       ├── api.ts            # API 调用函数
│   │       ├── hooks.ts          # React Query hooks
│   │       ├── types.ts          # 请求/响应类型
│   │       └── index.ts          # 模块导出
│   ├── stores/                   # 全局状态（Zustand）
│   │   └── global.ts             # 全局 store（折叠/主题/语言）
│   ├── styles/                   # 样式文件
│   │   ├── global.less           # 全局样式
│   │   ├── index.less            # 入口样式
│   │   ├── theme.ts              # Ant Design 主题配置
│   │   └── variables.less        # Less 变量
│   ├── types/                    # 全局类型定义
│   │   └── locale.ts
│   ├── utils/                    # 工具函数
│   │   └── index.ts
│   └── vite-env.d.ts             # Vite 类型声明
├── index.html                    # HTML 入口
├── package.json
├── vite.config.ts                # Vite 配置
├── tsconfig.json                 # TypeScript 主配置
├── tsconfig.app.json             # 应用 TS 配置
├── tsconfig.node.json            # Node TS 配置
├── commitlint.config.mjs         # Git commit 规范
├── lint-staged.config.mjs        # Git pre-commit lint
├── stylelint.config.mjs          # 样式 lint 配置
├── .oxfmtrc.json                 # 格式化配置
├── .oxlintrc.jsonc               # Lint 配置
└── pnpm-lock.yaml                # pnpm 锁文件
```

---

## 3. 代码规范细则

### 3.1 命名约定

| 类型 | 规范 | 示例 |
| --- | --- | --- |
| 组件文件 | PascalCase | `BasicLayout.tsx`, `AuthGuard.tsx` |
| 页面目录 | PascalCase, 单 `index.tsx` | `pages/Console/index.tsx` |
| 工具函数 | camelCase | `formatDate.ts` |
| 类型/接口 | PascalCase | `UserInfo`, `LoginParams` |
| Hooks | `use` 前缀, camelCase | `useCurrentUserQuery` |
| 常量 | UPPER_SNAKE_CASE | `LOCALE_KEY`, `VALID_LOCALES` |
| CSS 类名 | kebab-case（Less 文件中） | `.page-container` |
| Store key | kebab-case 字符串 | `'app-locale'` |

### 3.2 目录与模块划分规则

1. **按功能域分目录**：`components/`, `pages/`, `services/`, `router/`, `locales/`, `styles/`, `stores/`, `utils/`，各目录职责清晰不交叉。
2. **服务层按业务域拆分**：`services/user/` 下按 `api.ts`（纯函数）、`hooks.ts`（React Query hooks）、`types.ts`（类型定义）、`index.ts`（统一导出）四层文件组织。新增业务域（如 `services/agent/`）遵循同样结构。
3. **页面组件统一 `index.tsx`**：每个页面目录下只有一个 `index.tsx`，避免页面内子组件散落（复杂页面可放 `components/` 子目录）。
4. **路由加载器**：使用 `import.meta.glob` 预加载 `pages/**/*.tsx` 和 `layouts/**/*.tsx`，页面组件必须在这两个路径下才能被路由系统识别。

### 3.3 路由规范

1. **配置格式**：参考 umi 路由配置，使用声明式数组 `routes: RouteItem[]`，支持 `redirect` 和 `component` 两种路由（通过 discriminated union 类型保证互斥）。
2. **路由类型**：
   - `RouteMeta` — 页面标题、是否需要登录、角色权限
   - `RouteItem` — `ComponentRoute | RedirectRoute`
   - `MenuConfig` — 侧边栏菜单配置（path, name, icon, children）
3. **权限守卫**：每个组件路由包裹 `AuthGuard`，根据 `meta.auth` 和 `meta.roles` 决定登录/角色校验。
4. **懒加载**：使用 `React.lazy` + `React.Suspense` 实现路由级别代码分割，loading 态使用 `Spin`。
5. **菜单与路由分离**：`router/menus.ts` 定义侧边栏菜单，`router/index.ts` 定义路由，两者通过 `path` 关联。

### 3.4 组件规范

1. **函数组件优先**：所有组件使用函数式 + Hooks 编写，禁止使用类组件（`ErrorBoundary` 除外，因其需要 `componentDidCatch` API）。
2. **Props 类型**：使用 `interface` 定义 Props，命名格式 `ComponentNameProps`。
3. **导出方式**：默认导出组件，`export default function ComponentName()`。
4. **公共组件位置**：跨页面复用的组件放在 `src/components/`，单页面专用组件放在页面目录下的 `components/` 子目录。

### 3.5 状态管理规范

1. **Zustand 全局 store**：用于跨组件共享的全局状态（折叠、主题、语言）。
2. **Store 定义**：使用 `create<State>()(set => ({}))` 模式，同步直接返回 `{ ... }`。
3. **Store 持久化**：需要持久化的状态使用 `localStorage` 读写，key 使用 kebab-case 格式。
4. **React Query 服务端状态**：所有 API 数据通过 `@tanstack/react-query` 管理，禁止使用 `useState + useEffect` 手动管理异步请求。
5. **queryKey 管理**：使用集中管理的 queryKey 对象（如 `userQueryKeys`），避免硬编码字符串。

### 3.6 服务层规范

1. **Axios 实例**：统一在 `services/request.ts` 创建，配置 `baseURL`、`timeout`、`Content-Type`。
2. **请求拦截器**：统一处理 token 注入（从 localStorage 读取，`Bearer` 格式）。
3. **响应拦截器**：统一处理 HTTP 状态码错误（401/403/404/500），使用 `message.error` 提示。
4. **业务 API**：`api.ts` 中定义纯异步函数，返回原始数据。
5. **React Query hooks**：`hooks.ts` 中封装 `useQuery`/`useMutation`，处理缓存失效、重试策略。
6. **统一导出**：通过 `services/index.ts` 聚合所有业务域，使用 `services.xxx.useXxxQuery()` 方式调用。

### 3.7 国际化规范

1. **react-intl**：使用 `IntlProvider` 包裹应用根节点，通过 `useIntl()` 或 `<FormattedMessage>` 使用。
2. **语言包结构**：`locales/<locale>.ts` 为入口文件，聚合 `locales/<locale>/{component,menu,pages}.ts`。
3. **i18n key 格式**：使用 `menu.xxx`, `component.xxx`, `pages.xxx` 前缀区分模块。
4. **动态语言切换**：通过 Zustand store 管理 `locale`，切换时更新 `IntlProvider` 和 Antd `ConfigProvider`。

### 3.8 样式规范

1. **Less 优先**：使用 `.less` 文件，支持嵌套、变量、mixins。
2. **主题定制**：通过 `styles/theme.ts` 定义 Ant Design `ThemeConfig`，统一颜色、字体、圆角、间距。
3. **全局样式**：`styles/global.less` 定义全局样式（如 reset、公共样式类）。
4. **CSS Modules / CSS-in-JS**：不使用，直接通过 className 和 Less 文件管理。
5. **Stylelint**：遵循 `stylelint.config.mjs` 规则，提交前自动修复。

### 3.9 构建与部署

1. **多环境**：`env/.env.{develop|test|stag|prod}` 分离，通过 `vite --mode` 指定。
2. **构建命令**：`build:dev`, `build:test`, `build:stag`, `build:prod` 对应不同环境。
3. **代码分割**：Vite 配置将 react 相关库和 antd 分别打包到独立 chunk（`vendor-react`, `vendor-antd`），优化缓存。
4. **代理配置**：开发服务器代理 `/api` 到后端服务，目标地址从环境变量读取。
5. **基础路径**：`base` 从 `VITE_CDN_URL` 读取，支持 CDN 部署。

### 3.10 Git 提交规范

1. **Commit 格式**：遵循 `@commitlint/config-conventional`，格式 `<type>(<scope>): <subject>`。
2. **常用 type**：`feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`。
3. **Pre-commit**：`lint-staged` 对暂存文件自动执行 oxlint、oxfmt、stylelint。
4. **分支命名**：`feature/<YYYYMMDD>/<feature-name>`，如 `feature/20260513/new-struct`。

### 3.11 TypeScript 规范

1. **严格模式**：`strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`。
2. **路径别名**：`@/*` 映射到 `src/*`。
3. **ESNext 目标**：`target: "ES2023"`, `module: "ESNext"`。
4. **禁止枚举**：不使用 TypeScript `enum`，使用 `const` 对象或联合类型。
5. **类型导出**：使用 `export interface` / `export type`，避免 `export default` 导出类型。

### 3.12 布局规范

1. **ProLayout**：使用 `@ant-design/pro-components` 的 `ProLayout` 组件作为主布局。
2. **布局模式**：`mix` 模式（顶部导航 + 侧边栏），`inline` 侧边栏菜单。
3. **标签页**：当前 `showTabs: false`，如启用使用 `useTabs` hook 管理。
4. **用户信息**：从 `services.user.useCurrentUserQuery()` 获取，展示用户名、头像、角色。
5. **国际化切换**：通过 `localeOptions` 配置，在右上角提供切换。

---

## 4. 待完善项

> 以下是当前代码中尚未实现或需要后续补充的部分，新增代码时应注意对齐。

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 用户 API | Mock 状态 | `getCurrentUser` 返回硬编码数据，需对接真实接口 |
| 登录功能 | 未实现 | 缺少 `/login` 路由和登录页面组件 |
| 业务页面 | 骨架 | 当前只有 404 + 4 个占位页面 |
| 错误上报 | 待接入 | `ErrorBoundary` 中 TODO：接入 Sentry 等监控平台 |
| 响应拦截 | 注释中 | 响应拦截器中的 `data.code !== 0` 逻辑被注释，需根据后端规范调整 |
| 通知中心 | 待实现 | `NotificationConfig.tsx` 中全部为 TODO |
| 动态菜单 | 待实现 | `menus.ts` 中注释说明了接口动态菜单方案 |
