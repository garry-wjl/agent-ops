# 前端开发规范

> 基于项目实际代码与 RD UED 前端编码标准融合制定，结合 React 19 + TypeScript 5 + Vite + Ant Design 技术栈，统一团队代码风格，提升协作效率与代码质量。

---

## 目录

1. [核心原则](#1-核心原则)
2. [工具链](#2-工具链)
3. [命名规范](#3-命名规范)
4. [TypeScript 规范](#4-typescript-规范)
5. [React 组件规范](#5-react-组件规范)
6. [状态管理](#6-状态管理)
7. [数据请求](#7-数据请求)
8. [路由规范](#8-路由规范)
9. [样式规范](#9-样式规范)
10. [国际化（i18n）](#10-国际化i18n)
11. [Import 规范](#11-import-规范)
12. [代码组织](#12-代码组织)

---

## 1. 核心原则

### 1.1 一致性

在整个代码库中保持统一的代码风格，遵循本规范约定。当规范未覆盖某类场景时，参照已有代码的主流写法，保持一致而非另立风格。

### 1.2 可读性

- 代码逻辑清晰，避免过度嵌套与复杂技巧
- 变量名、函数名须有实际意义，能自我解释用途
- 仅在逻辑不自明处添加注释，不写废话注释

```typescript
// ❌ 废话注释
const count = 0; // 设置 count 为 0

// ✅ 有价值的注释
// 兼容后端历史接口：status 为字符串 '0'/'1' 而非布尔值
const isActive = user.status === '1';
```

### 1.3 可维护性

- 遵循单一职责原则，一个函数/组件只做一件事
- 消除重复代码（DRY 原则），公共逻辑抽取为 hooks 或工具函数
- 适当拆分模块，组件超过 300 行应考虑拆分

### 1.4 简洁性

- 删除无用代码、注释掉的代码块、未使用的变量和导入
- 避免过度设计，不为假设中的未来需求添加额外复杂度
- 优先选择语言/框架的内置能力，而非引入新依赖

---

## 2. 工具链

### 2.1 包管理

**必须使用 pnpm**，禁止使用 npm 或 yarn。

```bash
pnpm install          # 安装依赖
pnpm add <pkg>        # 添加依赖
pnpm add -D <pkg>     # 添加开发依赖
pnpm remove <pkg>     # 移除依赖
```

### 2.2 代码检查（Oxlint）

项目使用 **Oxlint**（基于 Rust），配置见 `.oxlintrc.jsonc`。

```bash
pnpm lint             # 检查
pnpm lint:fix         # 自动修复
```

### 2.3 代码格式化（Oxfmt）

配置见 `.oxfmtrc.json`，核心规则：

| 规则           | 值                      |
| -------------- | ----------------------- |
| 分号           | 启用                    |
| 引号           | 单引号（JSX 同）        |
| 缩进           | 2 空格                  |
| 行宽           | 100 字符                |
| Trailing comma | ES5                     |
| 行尾           | LF                      |
| 箭头函数括号   | 仅多参数时加（`avoid`） |

```bash
pnpm format           # 格式化
pnpm format:check     # 验证格式
```

禁止在代码中使用 `// oxfmt-ignore` 跳过格式化，除非有充分理由并附说明。

### 2.4 样式检查（Stylelint）

```bash
pnpm lint:style       # 检查
pnpm lint:style:fix   # 自动修复
```

### 2.5 Pre-commit Hook

提交前自动执行 `lint-staged`：

- `.ts/.tsx` 文件：Oxlint + Oxfmt
- `.less` 文件：Stylelint

**禁止使用 `--no-verify` 跳过 hook**，若报错应修复后再提交。

---

## 3. 命名规范

### 3.1 文件与目录

#### 文件命名

| 类型                | 格式                       | 示例                                |
| ------------------- | -------------------------- | ----------------------------------- |
| React 组件          | `PascalCase.tsx`           | `AuthGuard.tsx`, `DetailDrawer.tsx` |
| 组件入口            | `index.tsx`                | `AuditLog/index.tsx`                |
| 组件样式            | 固定 `index.module.less`   | `AuditLog/index.module.less`        |
| 全局/共享样式       | `camelCase.less`           | `global.less`, `variables.less`     |
| Store / 工具 / 服务 | `camelCase.ts`             | `global.ts`, `request.ts`           |
| Hooks 文件          | `camelCase.ts`，`use` 前缀 | `usePermission.ts`                  |
| 类型 / 常量文件     | `camelCase.ts`             | `types.ts`, `constants.ts`          |

`.tsx` 一律 PascalCase，`.ts` 一律 camelCase，无例外。如果一个 `.ts` 文件需要写 JSX，改为 `.tsx` 并用 PascalCase。

#### 目录命名

| 类型       | 格式         | 示例                               |
| ---------- | ------------ | ---------------------------------- |
| 组件目录   | `PascalCase` | `AuditLog/`, `StatusCard/`         |
| 非组件目录 | `kebab-case` | `services/`, `stores/`, `locales/` |

#### 目录结构

组件需要样式 → 建目录（`index.tsx` + `index.module.less`）。不需要 → 单文件平铺。递归适用。

```
components/
  AuthGuard.tsx                ← 无样式，单文件
  ErrorBoundary.tsx

  StatusCard/                  ← 有样式，建目录
    index.tsx
    index.module.less

  AuditLog/                    ← 有样式 + 子组件
    index.tsx
    index.module.less
    columns.ts                 ← 纯逻辑
    DetailDrawer.tsx           ← 无样式，平铺
    DetailDrawer/              ← 若也需要样式，建子目录
      index.tsx
      index.module.less
```

**反例**：

- ❌ `auditLog.tsx`（组件文件用 camelCase）
- ❌ `Columns.ts`（非组件 .ts 文件用 PascalCase）
- ❌ `userCard.module.less`（出现非 index 的 `.module.less`）
- ❌ `user_profile/`（下划线目录）
- ❌ 无附属文件时建空目录

### 3.2 变量与函数

```typescript
// 变量：camelCase
const userInfo = {};
const isLoading = false;

// 模块级真正常量：SCREAMING_SNAKE_CASE
const API_TIMEOUT = 10000;
const DEFAULT_PAGE_SIZE = 20;

// 函数：camelCase，动词开头
function fetchUserList() {}
function handleSubmit() {}

// 布尔值：is/has/can/should 前缀
const isVisible = true;
const hasPermission = false;
const canDelete = checkPermission('delete');
```

### 3.3 React 组件

```typescript
// 组件：PascalCase，具名函数
function UserProfile() {}

// Props 类型：组件名 + Props 后缀
interface UserProfileProps {
  userId: string;
  onClose: () => void;
}

// 事件处理函数：handle 前缀
function handleClick() {}
function handleFormSubmit(values: FormValues) {}

// 自定义 Hook：use 前缀
function useUserPermission() {}
```

### 3.4 类型与接口

```typescript
// 接口：PascalCase，无 I 前缀
interface UserInfo {
  id: string;
  username: string;
}

// 类型别名：PascalCase
type UserRole = 'admin' | 'editor' | 'viewer';
type ApiResponse<T> = { data: T; code: number; message: string };

// 枚举：PascalCase，值为字符串
enum OrderStatus {
  Pending = 'pending',
  Processing = 'processing',
  Completed = 'completed',
}
```

---

## 4. TypeScript 规范

### 4.1 基本要求

- 项目开启严格模式（`strict: true`），所有代码必须通过类型检查
- 禁止使用 `any`；确实无法推断时使用 `unknown` 并做类型收窄
- 禁止使用 `@ts-ignore`；确有必要时用 `@ts-expect-error` 并附说明注释
- 不写多余的类型注解，能推断出的类型无需重复声明

```typescript
// ❌ 多余的类型注解
const count: number = 0;

// ✅ 依赖推断
const count = 0;

// ✅ 必要时才声明（初始值为空，无法推断元素类型）
const list: UserInfo[] = [];
```

### 4.2 interface vs type

```typescript
// ✅ interface 用于对象类型（可扩展、可 extends）
interface UserInfo {
  id: string;
  username: string;
  avatar?: string;
}

// ✅ type 用于联合类型、元组、工具类型
type Tab = 'list' | 'detail';
type Status = 'pending' | 'success' | 'error';

// ❌ 不要用 type 定义普通对象（改用 interface）
type UserInfo = { id: string; username: string };
```

### 4.3 类型定义位置

| 场景                 | 位置                           |
| -------------------- | ------------------------------ |
| 组件 Props（私有）   | 组件文件内，不导出             |
| 组件 Props（跨文件） | 组件文件内，`export interface` |
| 业务数据类型         | `services/{module}/types.ts`   |
| Store 状态类型       | `stores/{name}.ts` 内          |
| 全局通用类型         | `types/{name}.ts`              |

### 4.4 类型工具

优先使用 TypeScript 内置工具类型：

```typescript
type PartialUser = Partial<UserInfo>; // 所有字段可选
type UserIdOnly = Pick<UserInfo, 'id'>; // 选取字段
type UserWithoutId = Omit<UserInfo, 'id'>; // 排除字段

// 自定义工具类型放在 src/types/ 下
type Nullable<T> = T | null;
```

### 4.5 函数类型

```typescript
// ✅ 明确参数和返回值类型
function formatAmount(value: number, decimals = 2): string {
  return value.toFixed(decimals);
}

// ✅ 异步函数明确返回类型
async function fetchUser(id: string): Promise<UserInfo> {
  return request.get(`/users/${id}`);
}
```

### 4.6 非空断言

谨慎使用 `!`，应先做空值检查：

```typescript
// ❌ 不安全
const value = map.get(key)!;

// ✅ 先检查再使用
const value = map.get(key);
if (!value) return;

// ✅ 可选链 + nullish coalescing
const name = user?.username ?? '匿名';
```

---

## 5. React 组件规范

### 5.1 组件定义

使用**函数组件 + 具名函数**，`default export`。

```typescript
// ✅ 推荐：具名函数，便于 DevTools 调试
function UserCard({ userId, onClose }: UserCardProps) {
  return <div>...</div>;
}
export default UserCard;

// ❌ 匿名箭头组件
const UserCard = ({ userId }: UserCardProps) => <div>...</div>;
```

### 5.2 组件文件结构

单文件内按以下顺序组织：

```typescript
// 1. 导入（见第 11 章 Import 规范）
import { useState } from 'react';
import { Button } from 'antd';
import { useUserQuery } from '@/services/user';
import type { UserInfo } from '@/services/user/types';
import styles from './index.module.less';

// 2. 导出类型（需跨文件共享时）
export interface UserDetailData { ... }

// 3. 私有类型
interface UserDetailProps {
  userId: string;
}

// 4. 模块级常量
const DEFAULT_PAGE_SIZE = 20;

// 5. 组件定义
function UserDetail({ userId }: UserDetailProps) {
  // 5.1 Hooks（按依赖顺序）
  const { formatMessage } = useIntl();
  const [visible, setVisible] = useState(false);
  const { data: user } = useUserQuery(userId);

  // 5.2 派生数据
  const displayName = user?.nickname || user?.username;

  // 5.3 事件处理函数
  function handleClose() {
    setVisible(false);
  }

  // 5.4 渲染
  return <div>...</div>;
}

export default UserDetail;
```

### 5.3 Props 设计

```typescript
// ✅ Props 类型独立声明，不使用内联对象
interface ButtonGroupProps {
  items: ActionItem[];
  onConfirm?: () => void;    // 可选回调明确标注
  disabled?: boolean;
  className?: string;
}

// ✅ 明确声明需要哪些 props
function MyTable({ columns, dataSource, loading }: MyTableProps) {}

// ❌ 透传所有 props
function MyTable(props: any) {
  return <Table {...props} />;
}
```

### 5.4 Hooks 使用规范

- 只在函数组件或自定义 Hook 中调用 Hook，不在条件语句内调用
- `useEffect` 依赖数组必须完整，不得遗漏依赖（Oxlint 会检查）

```typescript
// ✅ 依赖完整
useEffect(() => {
  fetchData(userId);
}, [userId]);

// ❌ 遗漏依赖
useEffect(() => {
  fetchData(userId);
}, []);
```

### 5.5 条件渲染

```typescript
// ✅ 简单条件用 &&
{isLoading && <Spin />}

// ✅ 二元条件用三元
{isAdmin ? <AdminPanel /> : <UserPanel />}

// ✅ 复杂条件提前 return
function Content({ status }: { status: Status }) {
  if (status === 'error') return <ErrorView />;
  if (status === 'loading') return <LoadingView />;
  return <MainContent />;
}

// ❌ 多层嵌套三元
{a ? b ? <A /> : <B /> : <C />}
```

### 5.6 列表渲染

```typescript
// ✅ key 使用稳定的唯一标识
{list.map(item => (
  <UserCard key={item.id} user={item} />
))}

// ❌ 用 index 作 key（排序/增删时会出问题）
{list.map((item, index) => (
  <UserCard key={index} user={item} />
))}
```

### 5.7 性能优化

仅在**实测存在性能问题**时才做优化，不预防性地滥用：

```typescript
// useMemo：缓存计算代价高的派生数据
const sortedList = useMemo(
  () => list.sort((a, b) => a.name.localeCompare(b.name)),
  [list],
);

// useCallback：稳定传给子组件的回调（配合 memo 使用才有意义）
const handleDelete = useCallback(
  (id: string) => deleteUser(id),
  [deleteUser],
);

// React.memo：仅在子组件因父组件 re-render 引发性能问题时使用
const UserRow = memo(function UserRow({ user }: { user: UserInfo }) {
  return <tr>...</tr>;
});
```

---

## 6. 状态管理

### 6.1 状态选型

| 状态类型                         | 方案                      |
| -------------------------------- | ------------------------- |
| 服务端数据（列表、详情）         | TanStack React Query      |
| 全局 UI 状态（主题、语言、布局） | Zustand                   |
| 组件局部状态                     | `useState` / `useReducer` |
| 表单状态                         | Ant Design Form           |

禁止使用 Redux 或 Context API 管理全局状态。

### 6.2 Zustand Store 定义

```typescript
// stores/myFeature.ts
import { create } from 'zustand';

interface MyFeatureState {
  items: string[];
  loading: boolean;
  setItems: (items: string[]) => void;
  addItem: (item: string) => void;
  reset: () => void;
}

export const useMyFeatureStore = create<MyFeatureState>()(set => ({
  items: [],
  loading: false,
  setItems: items => set({ items }),
  addItem: item => set(state => ({ items: [...state.items, item] })),
  reset: () => set({ items: [], loading: false }),
}));
```

### 6.3 Store 使用

```typescript
// ✅ 选择式订阅，只订阅需要的字段
const theme = useGlobalStore(state => state.theme);
const { collapsed, setCollapsed } = useGlobalStore();

// ❌ 订阅整个 store（任何字段变化都触发重渲染）
const store = useGlobalStore();
```

### 6.4 持久化

在 store 内部处理，使用 `localStorage`；敏感数据使用 `utils/crypto.ts` 加密后存储：

```typescript
const STORAGE_KEY = 'my-feature';

const loadFromStorage = (): Partial<MyState> => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
};

// 在 action 中写入
setConfig: (config) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
  set(config);
},
```

---

## 7. 数据请求

### 7.1 目录结构：按复杂度分层

服务层**不强制拆分为多个文件**，根据模块实际复杂度决定：

```
services/
├── request.ts          # Axios 实例（勿修改基础配置）
├── user.ts             # 简单模块：单文件（API 函数 + 类型）
└── order/              # 复杂模块：拆分目录
    ├── api.ts          # Axios 请求函数
    ├── hooks.ts        # React Query hooks（有复杂缓存逻辑时才拆）
    ├── types.ts        # 类型定义（类型较多或跨模块共享时才拆）
    └── index.ts        # barrel export
```

**拆分判断依据**：

| 场景                       | 推荐结构                    |
| -------------------------- | --------------------------- |
| 接口 ≤ 10 个，类型简单     | 单文件 `user.ts`            |
| 接口较多，需要 React Query | 目录 +`api.ts` + `hooks.ts` |
| 类型被多模块共享           | 单独 `types.ts`             |

禁止为了"结构统一"强行拆分内容极少的模块（3 个接口不需要 4 个文件）。

### 7.2 API 函数规范

函数命名使用**动词 + 名词**，返回值类型明确：

```typescript
// services/user.ts（单文件示例）
import request from './request';

export interface UserInfo {
  id: string;
  username: string;
  email: string;
  role: string;
}
export interface UserListParams {
  page: number;
  pageSize: number;
  keyword?: string;
}

export function getUserList(params: UserListParams) {
  return request.get<{ list: UserInfo[]; total: number }>('/users', { params });
}

export function getUserById(id: string) {
  return request.get<UserInfo>(`/users/${id}`);
}

export function createUser(data: Pick<UserInfo, 'username' | 'email' | 'role'>) {
  return request.post<UserInfo>('/users', data);
}

export function updateUser(id: string, data: Partial<UserInfo>) {
  return request.put<UserInfo>(`/users/${id}`, data);
}

export function deleteUser(id: string) {
  return request.delete<void>(`/users/${id}`);
}
```

### 7.3 React Query Hooks

Query Key 使用**工厂函数**（支持层级失效）：

```typescript
// services/order/hooks.ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getOrderList, cancelOrder } from './api';
import type { OrderListParams } from './types';

// Query Key 工厂（与 hooks 放在同一文件）
export const orderKeys = {
  all: () => ['orders'] as const,
  lists: () => [...orderKeys.all(), 'list'] as const,
  list: (params: OrderListParams) => [...orderKeys.lists(), params] as const,
  detail: (id: string) => [...orderKeys.all(), 'detail', id] as const,
};

// 查询 hook：use[Resource]Query 命名
export function useOrderListQuery(params: OrderListParams) {
  return useQuery({
    queryKey: orderKeys.list(params),
    queryFn: () => getOrderList(params),
    staleTime: 5 * 60 * 1000,
  });
}

// 变更 hook：use[Action]Mutation 命名
export function useCancelOrderMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cancelOrder,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all() });
    },
  });
}
```

### 7.4 特殊场景：Streaming / FormData

流式响应或文件上传绕过 React Query，直接使用 `fetch`：

```typescript
async function uploadFiles(files: File[], extraData: string) {
  const formData = new FormData();
  formData.append('data', extraData);
  files.forEach(f => formData.append('files', f));

  const res = await fetch('/api/my-module/upload', {
    method: 'POST',
    body: formData,
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: '请求失败' }));
    throw new Error(err.error);
  }

  return res.json();
}
```

### 7.5 禁止事项

- 禁止在组件中直接调用 `request`，必须通过服务模块的 API 函数
- 禁止硬编码 API 地址，统一使用 `/api` 代理前缀或环境变量
- 禁止在多处重复处理相同的请求错误，统一在 `request.ts` 拦截器处理

---

## 8. 路由规范

### 8.1 路由配置

在 `router/index.ts` 中集中配置（Umi 风格，字符串引用自动懒加载）：

```typescript
export const routes: RouteItem[] = [
  {
    path: '/',
    component: 'layouts/BasicLayout',
    routes: [
      { path: '/', redirect: '/dashboard' },
      { path: '/dashboard', component: 'Dashboard', meta: { auth: true } },
      { path: '/user', component: 'UserManage', meta: { auth: true, roles: ['admin'] } },
    ],
  },
  { path: '/*', component: '404' },
];
```

组件解析规则（`router/loader.tsx`）：

- `'Dashboard'` → `pages/Dashboard/index.tsx` 或 `pages/Dashboard.tsx`
- `'layouts/BasicLayout'` → `layouts/BasicLayout.tsx`

### 8.2 权限控制

通过路由 `meta` 字段声明权限，由 `AuthGuard` 统一处理，不在页面组件内写权限判断：

```typescript
interface RouteMeta {
  auth?: boolean; // true 表示需要登录
  roles?: string[]; // 允许访问的角色，空则所有已登录用户可访问
}
```

### 8.3 路由导航：navigate vs window

**应用内导航**使用 React Router hooks：

```typescript
import { useNavigate } from 'react-router-dom';

const navigate = useNavigate();
navigate('/dashboard');
navigate(`/user/${id}`, { state: { from: 'list' } });
navigate(-1); // 返回上一页
```

**以下场景使用 `window`**（React Router 无法替代）：

```typescript
// ✅ 新开标签页
window.open(`/report/${id}`, '_blank', 'noopener,noreferrer');

// ✅ 跳出应用的外部重定向（OAuth、支付等）
window.location.href = `https://sso.example.com/login?redirect=${encodeURIComponent(window.location.origin)}`;

// ✅ 强制整页刷新（登出、权限变更后）
window.location.reload();
```

> **判断准则**：目标是应用内路由 → `useNavigate`；目标是外部 URL 或需要新窗口 → `window`。

### 8.4 菜单配置

路由和菜单**分离**，需单独在 `router/menus.ts` 配置：

```typescript
export const menus: MenuConfig[] = [
  { path: '/', name: 'menu.home', icon: 'home' },
  {
    path: '/user',
    name: 'menu.user',
    icon: 'user',
    children: [{ path: '/user/manage', name: 'menu.user.manage', icon: 'setting' }],
  },
];
```

---

## 9. 样式规范

项目采用 **Tailwind CSS + CSS Modules（Less）混合方案**，两者各司其职，不相互替代。

> **配置前置**：Tailwind 需禁用 preflight 避免与 Ant Design 冲突：
> `tailwind.config.ts` → `corePlugins: { preflight: false }`

### 9.1 选择哪种方案

**用 Tailwind**（标准布局工具类）：

- 布局结构：`flex`, `grid`, `flex-col`, `items-center`, `justify-between`
- 间距：`p-4`, `px-6`, `gap-3`, `mt-2`
- 宽高：`w-full`, `h-full`, `min-h-0`
- 显示控制：`hidden`, `block`, `overflow-hidden`
- 文本工具：`text-sm`, `font-medium`, `truncate`, `line-clamp-2`
- 响应式：`sm:flex-col`, `lg:grid-cols-3`

**用 CSS Modules + Less**（`.module.less`）：

- 有语义的组件核心样式（类名需在 DevTools 中可读）
- 多状态变体（`active`、`disabled`、`hover` + transition 组合）
- 自定义动画 / keyframes
- 使用 Less 变量（`@text-color`、`@border-color`）
- 使用 Less 混入（`.text-ellipsis()`、`.flex-center()`）
- 与 Ant Design token 配合的精细覆盖

**判断标准**：这段样式需要有意义的名字吗？→ CSS Modules。标准工具类组合能表达清楚吗？→ Tailwind。

### 9.2 混合使用示例

```tsx
import { cx } from 'classnames';
import styles from './index.module.less';

function ItemCard({ title, score, active }: ItemCardProps) {
  return (
    <div className={cx('flex flex-col gap-3', styles.card, active && styles.cardActive)}>
      <div className='flex items-center justify-between'>
        <span className={cx('font-medium', styles.title)}>{title}</span>
        <span className={score >= 80 ? styles.badgePass : styles.badgeFail}>{score}</span>
      </div>
      <p className='text-sm text-gray-500 line-clamp-2'>{description}</p>
    </div>
  );
}
```

```less
// index.module.less
@import (reference) '../../../styles/variables.less';

.card {
  border: 1px solid @border-color;
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
  cursor: pointer;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }
}

.cardActive {
  border-color: #4f46e5;
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.15);
}

.title {
  .text-ellipsis();
  color: @text-color;
}
```

### 9.3 CSS Modules className 命名

使用 **camelCase**，语义化，体现层级结构：

```less
// ✅ 正确
.pageHeader {
  ...;
}
.pageHeaderTitle {
  ...;
}
.cardBody {
  ...;
}

// ❌ 错误
.div1 {
  ...;
} // 无意义
.tab_btn {
  ...;
} // snake_case
.TabBtn {
  ...;
} // PascalCase
```

### 9.4 组合 className（条件类）

```tsx
// 统一使用 classnames 的 cx
import { cx } from 'classnames';

// 简单条件
<div className={cx('flex gap-2', styles.card, active && styles.cardActive)}>

// 多条件（对象语法）
<div className={cx('flex gap-2', styles.card, { [styles.cardActive]: active, [styles.cardDisabled]: disabled })}>
```

### 9.5 Less 属性书写顺序

```less
.element {
  // 1. 定位
  position: absolute;
  top: 0;
  right: 0;
  z-index: 10;

  // 2. 盒模型
  display: flex;
  width: 100px;
  height: 100px;
  margin: 0;
  padding: 16px;
  overflow: hidden;

  // 3. 视觉
  background: @bg-color;
  border: 1px solid @border-color;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

  // 4. 文字
  color: @text-color;
  font-size: 14px;
  line-height: 1.5;

  // 5. 其他
  cursor: pointer;
  transition: all 0.2s;
}
```

### 9.6 全局资源

| 文件                    | 用途                                                            |
| ----------------------- | --------------------------------------------------------------- |
| `styles/variables.less` | 颜色、字体全局变量（`@text-color`, `@border-color`）            |
| `styles/mixins.less`    | Less 混入（`.text-ellipsis()`, `.flex-center()`）               |
| `styles/theme.ts`       | Ant Design 主题 token                                           |
| `styles/global.less`    | 全局样式覆盖（仅用于 Ant Design 全局修改，加 `:global()` 包裹） |

### 9.7 Ant Design 主题定制

**只通过 `theme.ts` 的 token 配置**，不覆盖 Ant Design 内部 CSS 类：

```typescript
// ✅ 通过 Token 定制
export const antdTheme: ThemeConfig = {
  token: { colorPrimary: '#4F46E5', borderRadius: 8, controlHeight: 36 },
  components: { Button: { borderRadius: 8 }, Menu: { itemHeight: 40 } },
};

// ❌ 直接覆盖类名（脆弱，升级可能失效）
.ant-btn-primary { background: red; }
```

### 9.8 禁止写法

```tsx
// ❌ inline style 定义视觉样式
<div style={{ color: 'red', display: 'flex' }}>...</div>

// ❌ 用 Tailwind 覆盖 Ant Design 内部样式
<Button className="bg-blue-500">提交</Button>
```

**例外**：纯动态数值（动画进度、图表尺寸）可用 inline style：

```tsx
<div style={{ width: `${progress}%` }}>...</div>
```

### 9.9 自适应布局

页面布局应具备自适应能力，避免因写死固定尺寸导致不同分辨率或窗口大小下出现布局错乱。

**核心原则**：

- **页面级容器、内容区等流式区域**优先使用 `%`、`vw`、`flex`、`grid` 等弹性方式，避免用固定 `px` 宽度锁死
- 高度优先让内容自然撑开；需要限制高度时，优先使用 `min-height` / `max-height` 而非固定 `height`
- 表格、列表等数据区域应支持滚动，避免溢出遮挡其他元素

**允许使用固定值的场景**：

并非所有固定尺寸都不合理，以下场景可以合理使用固定 `px` 值：

| 场景                   | 说明                                                                      |
| ---------------------- | ------------------------------------------------------------------------- |
| 图标、头像等固定元素   | `width: 32px; height: 32px;` 等固定尺寸符合设计规范                       |
| 顶栏 / 工具栏高度      | `height: 64px` 等约定高度，全局统一即可                                   |
| 侧边栏宽度             | 折叠/展开各一个固定值（如 80px / 260px），配合内容区 `flex: 1` 自适应     |
| 弹窗 / 抽屉宽度        | `width: 520px` 等设计稿约定宽度，但应配合 `max-width: 100vw` 防止小屏溢出 |
| 间距、圆角等装饰性数值 | `padding: 16px`、`border-radius: 8px` 等不影响布局弹性                    |

**应避免的写法**：

```less
// ⚠️ 页面容器写死宽度 → 窗口小于该值时出现横向滚动
.page {
  width: 1200px;
}
// ✅ 改为弹性约束
.page {
  max-width: 1200px;
  width: 100%;
}

// ⚠️ 数据区域写死高度 → 数据量变化时内容截断或大量留白
.tableWrapper {
  height: 500px;
}
// ✅ 改为弹性 + 滚动
.tableWrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

// ⚠️ 绝对定位填充但未处理溢出
.content {
  position: absolute;
  top: 64px;
  bottom: 0;
  // 内容超出时无法滚动
}
// ✅ 补充 overflow
.content {
  position: absolute;
  top: 64px;
  bottom: 0;
  overflow: auto;
}
```

> **经验法则**：写下一个固定 `px` 值时，问自己"如果容器比这个值小/大会怎样？"——如果答案是溢出或大量留白，就应该改用弹性方案。

**推荐布局模式**：

```less
// 弹性布局 + 自适应
.pageContainer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.contentArea {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

// 栅格自适应
.cardGrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
```

**Tailwind 响应式断点**：

使用 Tailwind 响应式前缀适配不同屏幕宽度，至少关注 `md`（768px）和 `xl`（1280px）两个断点：

```tsx
// 响应式栅格
<div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">

// 响应式间距
<div className="p-3 md:p-4 xl:p-6">
```

**自查清单**：

- [ ] 页面在 1280px 和 1920px 宽度下均无意外的水平滚动条
- [ ] 表格区域内容超出时可独立滚动，不影响页面整体布局
- [ ] 侧边栏折叠/展开后内容区能自适应宽度变化
- [ ] 弹窗、抽屉组件内部内容过多时可滚动，不溢出视口
- [ ] 使用固定 `px` 值的地方已确认不会在合理窗口范围内引发布局问题

### 9.10 图标规范

**所有图标**必须先在 `components/IconRenderer/registry.ts` 注册：

```typescript
import { SearchOutlined } from '@ant-design/icons';

export const IconRegistry = {
  search: SearchOutlined,
} as const;
```

菜单中按字符串名引用，组件中用 `<IconRenderer name="search" />`。禁止在组件中直接 import 图标后使用。

---

## 10. 国际化（i18n）

### 10.1 翻译文件结构

```
locales/
├── zh-CN/
│   ├── menu.ts       # 菜单
│   ├── pages.ts      # 页面文案
│   └── component.ts  # 通用组件文案
├── en-US/
│   ├── menu.ts
│   ├── pages.ts
│   └── component.ts
├── zh-CN.ts          # 汇总导出
├── en-US.ts          # 汇总导出
└── index.ts          # localeConfig + getAllMessages
```

### 10.2 Key 命名规则

点号分隔的层级结构：`{模块}.{子模块}.{标识}`

```typescript
// ✅
'menu.home';
'page.user.title';
'page.user.search.placeholder';
'component.table.empty';

// ❌ 扁平无层级
'menuHome';
'userTitle';
```

### 10.3 在组件中使用

```typescript
import { useIntl } from 'react-intl';

function UserManage() {
  const { formatMessage } = useIntl();

  const title = formatMessage({ id: 'page.user.title' });
  const tip = formatMessage({ id: 'page.user.count.tip' }, { count: total });

  return <h1>{title}</h1>;
}
```

### 10.4 规范要求

- 新增翻译 key 时，`zh-CN` 和 `en-US` 需同步添加
- **核心用户界面文案**（菜单、页面标题、操作按钮、提示信息）使用国际化
- 以下情况**允许直接使用字面量**：
  - 纯开发/调试阶段的临时页面
  - 项目明确只需要单语言
  - 日志、console、错误堆栈等非用户可见内容

---

## 11. Import 规范

### 11.1 导入顺序

1. **外部库**（react、antd、第三方）
2. **路径别名**（`@/`）
3. **相对路径**（`./` 或 `../`）
4. **样式文件**（`.module.less`，永远最后）

```tsx
// ✅ 标准导入顺序
import { useState } from 'react';
import { Button, Form, Modal } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useGlobalStore } from '@/stores/global';
import type { MenuConfig } from '@/router/types';
import ItemCard, { type ItemData } from './ItemCard';
import styles from './index.module.less';
```

### 11.2 路径别名规则

```typescript
// ✅ 跨目录引用用 @/ 别名
import { useGlobalStore } from '@/stores/global';

// ❌ 超过 2 层的相对路径
import { useGlobalStore } from '../../../../stores/global';
```

**规则**：超过 2 层（`../../`）的相对导入改用 `@/` 别名；同目录或上一级用相对路径。

### 11.3 类型导入

始终使用 `import type` 导入纯类型：

```typescript
import type { UserInfo } from './types'; // ✅
import { type UserInfo } from './types'; // ✅（内联 type）
import { UserInfo } from './types'; // ❌
```

---

## 12. 代码组织

### 12.1 目录职责

```
apps/web/src/
├── pages/         # 页面级组件（对应路由）
├── layouts/       # 全局布局组件
├── components/    # 通用可复用 UI 组件（无业务逻辑）
├── services/      # API 层 + React Query hooks
├── stores/        # 全局状态（Zustand）
├── router/        # 路由配置 + 加载逻辑
├── hooks/         # 通用自定义 hooks
├── utils/         # 纯工具函数
├── locales/       # i18n 翻译文件
├── styles/        # 全局样式资源
└── types/         # 全局类型定义
```

### 12.2 Barrel Export

模块目录提供 `index.ts` 统一导出：

```typescript
// services/order/index.ts
export type { OrderInfo, OrderListParams } from './types';
export { orderApi, orderKeys } from './api';
export { useOrderListQuery, useCancelOrderMutation } from './hooks';
```

### 12.3 模块边界

前端只能引用**类型库**，不可引用后端运行时库（ORM、服务器 SDK 等）。违反边界会被 `nx lint` 报错，不得绕过。

---

## 附：禁止事项速查

| 禁止                                   | 替代方案                                         |
| -------------------------------------- | ------------------------------------------------ |
| `style={{ ... }}` 定义视觉样式         | Tailwind 工具类或 `.module.less`                 |
| Tailwind 覆盖 Ant Design 内部样式      | `theme.ts` token 或 `global.less`                |
| 类组件（ErrorBoundary 等框架限定除外） | 函数组件                                         |
| `any` 类型                             | 明确类型或 `unknown` + 收窄                      |
| 组件内直接调用 `request`               | `services/` 层 API 函数                          |
| 超过 2 层的相对路径（`../../../`）     | `@/` 路径别名                                    |
| `import { X }` 导入纯类型              | `import type { X }`                              |
| 直接 import 图标在组件中使用           | 在 `IconRegistry` 注册后通过 `IconRenderer` 使用 |
| 前端引用后端运行时库                   | 只引用纯类型库                                   |
| 大量覆盖 Ant Design CSS 类             | 通过 `theme.ts` token 配置                       |
| Store 内部直接修改状态                 | 通过定义的 action 修改                           |
| 应用内路由跳转使用 `window.location`   | `useNavigate`                                    |
| `--no-verify` 跳过 pre-commit hook     | 修复问题后再提交                                 |
| 为结构统一强行拆分极小模块             | 按复杂度灵活决定文件结构                         |

---

_本规范持续迭代，如有疑问或建议，请通过 MR 提交至本文档。_
