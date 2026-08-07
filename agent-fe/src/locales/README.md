# 国际化（i18n）目录

本目录用于存放项目的多语言翻译文件。

## 目录结构

```shell
locales/
├── index.ts          # 语言配置导出
├── zh-CN.ts          # 中文翻译入口
├── zh-CN/            # 中文翻译文件目录
│   ├── component.ts  # 组件相关翻译
│   ├── menu.ts       # 菜单相关翻译
│   └── pages.ts      # 页面相关翻译
├── en-US.ts          # 英文翻译入口
└── en-US/            # 英文翻译文件目录
    ├── component.ts
    ├── menu.ts
    └── pages.ts
```

## 支持的语言

- **zh-CN**: 简体中文（默认）
- **en-US**: 美式英语

## 使用说明

### 1. 添加新的翻译文件

在对应语言目录下创建新的翻译文件，例如：

```typescript
// locales/zh-CN/common.ts
export default {
  welcome: '欢迎',
  hello: '你好',
};
```

```typescript
// locales/en-US/common.ts
export default {
  welcome: 'Welcome',
  hello: 'Hello',
};
```

### 2. 在语言入口文件中引入

```typescript
// locales/zh-CN.ts
import component from './zh-CN/component';
import menu from './zh-CN/menu';
import pages from './zh-CN/pages';
import common from './zh-CN/common'; // 新增

export default {
  ...component,
  ...menu,
  ...pages,
  ...common, // 新增
};
```

### 3. 在组件中使用

使用 `react-intl` 的 `useIntl` hook：

```typescript
import { useIntl } from 'react-intl'

function MyComponent() {
  const intl = useIntl()

  return (
    <div>
      <h1>{intl.formatMessage({ id: 'common.welcome' })}</h1>
      <p>{intl.formatMessage({ id: 'common.hello' })}</p>
    </div>
  )
}
```

或者使用 `FormattedMessage` 组件：

```typescript
import { FormattedMessage } from 'react-intl'

function MyComponent() {
  return (
    <div>
      <h1><FormattedMessage id="common.welcome" /></h1>
      <p><FormattedMessage id="common.hello" /></p>
    </div>
  )
}
```

### 4. 切换语言

使用 `useGlobalStore` 获取语言状态：

```typescript
import { useGlobalStore } from '@/stores/global'
import type { Locale } from '@/types/locale'

function LanguageSwitcher() {
  const { locale, setLocale } = useGlobalStore()

  return (
    <select value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>
      <option value="zh-CN">中文</option>
      <option value="en-US">English</option>
    </select>
  )
}
```

## 添加新语言

### 1. 创建语言目录和文件

```bash
# 创建新语言目录
mkdir -p src/locales/fr-FR

# 创建翻译文件
touch src/locales/fr-FR/component.ts
touch src/locales/fr-FR/menu.ts
touch src/locales/fr-FR/pages.ts
```

### 2. 创建语言入口文件

```typescript
// locales/fr-FR.ts
import component from './fr-FR/component';
import menu from './fr-FR/menu';
import pages from './fr-FR/pages';

export default {
  ...component,
  ...menu,
  ...pages,
};
```

### 3. 在 index.ts 中注册

```typescript
// locales/index.ts
import enUS from './en-US';
import zhCN from './zh-CN';
import frFR from './fr-FR'; // 新增

export const localeConfig = {
  'zh-CN': {
    name: '中文',
    messages: zhCN,
  },
  'en-US': {
    name: 'English',
    messages: enUS,
  },
  'fr-FR': {
    // 新增
    name: 'Français',
    messages: frFR,
  },
} as const;
```

### 4. 更新类型定义

```typescript
// src/types/locale.ts
export type Locale = 'zh-CN' | 'en-US' | 'fr-FR'; // 添加新语言
```

## 翻译文件组织建议

- **component.ts**: 通用组件相关的翻译（按钮、表单、提示等）
- **menu.ts**: 菜单和导航相关的翻译
- **pages.ts**: 页面内容相关的翻译
- **common.ts**: 通用文本翻译
- **error.ts**: 错误信息翻译
- **validation.ts**: 表单验证信息翻译

## 最佳实践

1. **命名规范**: 使用点号分隔的层级结构（如 `common.welcome`）
2. **保持一致性**: 确保所有语言文件的结构保持一致
3. **避免硬编码**: 所有用户可见的文本都应该使用翻译
4. **类型安全**: 使用 TypeScript 确保翻译 key 的类型安全
5. **分组管理**: 按功能模块分组管理翻译文件，便于维护

## 相关文件

- `src/models/global.ts` - 全局状态（含语言切换）
- `src/types/locale.ts` - 语言类型定义
- `src/components/AppProvider.tsx` - 国际化 Provider 配置
