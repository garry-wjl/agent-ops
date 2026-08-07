# rd-agent-fe — Architecture

> 顶层架构、领域划分、依赖规则。
> 这份文件描述"什么允许做、什么不允许做",**不**描述"怎么实现"。

---

## 1. 一图看懂架构

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器 (Chrome / Edge)                                          │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
┌─────────────────────────────────▼───────────────────────────────┐
│  rd-agent-fe (React 19 + Antd 6 + Vite + pnpm)                   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ pages/                                                    │  │
│  │  - Console        (调试台,ChatGPT 风格对话流)             │  │
│  │  - Agents         (智能体管理:列表 + 详情)                │  │
│  │  - Prompts        (提示词管理:列表 + 编辑器 + 版本历史)   │  │
│  │  - SystemSettings (v0.2,MVP 灰显)                        │  │
│  │  - Login          (GT OAuth callback)                     │  │
│  │  - Errors         (403 / 404 / 5xx)                       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ components/   ← 复用 UI(避免业务耦合)                    │  │
│  │ hooks/        ← 数据获取 / 业务逻辑钩子                    │  │
│  │ services/     ← API client(对应 rd-agent-be Admin API)   │  │
│  │ stores/       ← 全局状态(用户 / 当前 thread / 流式状态)   │  │
│  │ types/        ← TypeScript 类型(部分 generated/)         │  │
│  │ utils/        ← 通用工具                                   │  │
│  │ i18n/         ← 仅 zh-CN(en-US 留空)                     │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ HTTPS + JWT
                                  ▼
                      ┌────────────────────┐
                      │   rd-agent-be      │  (唯一后端依赖)
                      └────────────────────┘
```

**关键**:fe **不直连 sphere**,所有数据(包括 step 流式)都走 rd-agent-be。

## 2. 领域划分

> 分层方式参考 rd-points-fe(已被验证)。

| 分组          | 用途                  | 包含模块                                                                     |
| ------------- | --------------------- | ---------------------------------------------------------------------------- |
| `pages/`      | 页面级组件,与路由 1:1 | Console / Agents / Prompts / SystemSettings / Login / Errors                 |
| `components/` | 通用 UI 组件          | Layout / NavSidebar / UserMenu / 公共表格 / 编辑器 wrapper(Monaco)/ 流式光标 |
| `hooks/`      | 业务逻辑钩子          | useAgents / usePrompt / useThread / useRunStream / useAuth                   |
| `services/`   | API client            | agentsService / promptsService / threadsService / authService                |
| `stores/`     | 全局状态              | userStore / streamStore(当前流式 run)                                        |
| `types/`      | TS 类型               | API 响应 / 业务实体(Thread / Run / Step / Prompt / Agent)                    |

详细模块拆分待**切片 1 工程骨架**敲定后补充。

## 3. 依赖方向(不可违反)

```
pages → components / hooks / services / stores / types  ✅
hooks → services / stores / types                       ✅
services → types                                        ✅
components → types(纯 UI 组件可以读 types)              ✅
components → services / stores                          ❌(组件不直接调 API,不读全局状态;通过 props 或 hooks)
services → hooks / components / stores                  ❌
types → 任何业务模块                                     ❌(types 是叶子节点)
```

> 规则核心:**只有 hooks 可以同时持有"数据"和"逻辑"**,组件保持纯;services 是无状态 API client。

## 4. 边界与例外

- 暂无例外
- (任何破例必须在此登记,附申请理由 + 负责人)

## 5. 强制手段(机械检查)

> 不变量必须由代码强制,不能只靠 review。

| 规则              | 强制方式                                                    | 配置位置                                             |
| ----------------- | ----------------------------------------------------------- | ---------------------------------------------------- |
| 依赖方向          | ESLint 自定义规则 / dependency-cruiser                      | `.eslintrc` / `.dependency-cruiser.js` <!-- TODO --> |
| TypeScript strict | `tsconfig.json` strict: true + noUncheckedIndexedAccess     | `tsconfig.json`                                      |
| 不直连 sphere     | ESLint 禁用 `import sphere*`、禁止裸 fetch 直连 sphere 域名 | `.eslintrc` <!-- TODO -->                            |
| 调试台无业务字段  | 自定义 lint 规则 / code review checklist                    | <!-- TODO -->                                        |
| 命名规范          | ESLint(camelCase 组件 / kebab-case 文件夹等)                | `.eslintrc`                                          |

## 6. 横切关注点(Providers)

> 横切能力通过 React Context / hooks 形式注入,组件不直接依赖具体实现。

- `AuthProvider` — 全局用户态 / 角色;hooks 通过 `useAuth()` 取
- `ApiClient` — axios 实例 + JWT 拦截器 + 错误归一化;services 通过依赖注入,不直接 `new Axios`
- `StreamProvider` — 当前流式 run 的状态机;调试台组件通过 `useRunStream(runId)` 订阅
- `I18nProvider` — i18n 初始化(仅 zh-CN 加载,en-US 占位)

## 7. 演进与例外申请

- 修改本文件之前必须经过 review
- 新增"例外"必须在第 4 节登记,并附上申请理由和负责人
- 当强制手段(lint/test)和文档冲突时,**强制手段优先**——文档要立即更新

---

## 参考

- [PRD-MVP §4 功能需求](../docs/agent-sphere/PRD-MVP.md#4-功能需求按菜单展开) — 各页面功能拆解
- [Figma 原型 hoQIHy1FcQdfE49oDsiSV9](https://www.figma.com/design/hoQIHy1FcQdfE49oDsiSV9/AgentSphere?node-id=0-1)
- [rd-points-fe AGENTS.md](../rd-points-fe/AGENTS.md) — 本仓库架构的拷贝来源
- [matklad: ARCHITECTURE.md](https://matklad.github.io/2021/02/06/ARCHITECTURE.md.html)
- [Parse, don't validate](https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/) — services 边界处用 zod / yup 解析响应到强类型
