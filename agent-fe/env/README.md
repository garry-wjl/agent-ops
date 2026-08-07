# 环境变量配置说明

本目录用于存放不同环境的环境变量配置文件。项目支持多环境配置，通过 Vite 的 `--mode` 参数来指定运行环境。

## 环境类型

项目支持以下四种环境：

### 1. develop（开发环境）
- **文件名称**: `.env.develop`
- **使用场景**: 本地开发调试
- **运行命令**: `npm run dev` 或 `vite --mode develop`
- **构建命令**: `npm run build:dev`
- **特点**: 
  - 通常指向本地开发服务器
  - 启用详细的错误信息和调试工具
  - 支持热更新

### 2. test（测试环境）
- **文件名称**: `.env.test`
- **使用场景**: 测试环境部署和测试
- **运行命令**: `vite --mode test`
- **构建命令**: `npm run build:test`
- **特点**:
  - 指向测试服务器
  - 用于功能测试和集成测试
  - 可能包含测试专用的配置

### 3. stag（预发布环境）
- **文件名称**: `.env.stag`
- **使用场景**: 预发布环境，用于生产前的最后验证
- **运行命令**: `vite --mode stag`
- **构建命令**: `npm run build:stag`
- **特点**:
  - 接近生产环境的配置
  - 用于最终的功能验证和性能测试
  - 通常与生产环境使用相同的基础设施

### 4. prod（生产环境）
- **文件名称**: `.env.prod`
- **使用场景**: 正式生产环境
- **运行命令**: `vite --mode prod`
- **构建命令**: `npm run build:prod`
- **特点**:
  - 生产环境配置
  - 启用代码压缩和优化
  - 指向生产服务器和 CDN

### 5. .env.local（本地覆盖配置）
- **文件名称**: `.env.local`
- **使用场景**: 本地个人开发配置，覆盖其他环境变量
- **特点**:
  - ⚠️ **优先级最高**，会覆盖所有其他环境变量文件中的同名变量
  - 用于个人本地开发时的特殊配置（如个人 API Key、本地代理地址等）
  - **不会被提交到版本控制系统**（已在 `.gitignore` 中忽略）
  - 适用于所有环境模式（develop/test/stag/prod）
  - 每个开发者可以有自己的 `.env.local` 文件，互不干扰

**使用示例**：
```env
# .env.local
# 个人本地开发配置，覆盖 .env.develop 中的配置
VITE_API_BASE_URL=http://localhost:3001  # 使用个人本地 API 服务器
VITE_DEBUG=true  # 个人调试开关
```

**注意事项**：
- `.env.local` 文件应该添加到 `.gitignore` 中（已默认忽略）
- 不要将 `.env.local` 提交到代码仓库
- 团队协作时，可以通过 `.env.example` 文件说明需要配置的变量

## 环境变量说明

### 必需的环境变量

#### VITE_API_BASE_URL
- **说明**: API 请求的基础地址
- **用途**: 
  - 在 `vite.config.ts` 中用于配置开发服务器的代理目标
  - 在 `src/utils/request.ts` 中作为 axios 的 baseURL
- **示例**: 
  - 开发环境: `http://localhost:8080`
  - 测试环境: `https://api-test.example.com`
  - 预发布环境: `https://api-stag.example.com`
  - 生产环境: `https://api.example.com`

#### VITE_CDN_URL
- **说明**: 静态资源 CDN 地址
- **用途**: 在 `vite.config.ts` 中配置构建输出的 base 路径
- **示例**:
  - 开发环境: `/` (使用相对路径)
  - 测试/预发布/生产环境: `https://cdn.example.com/`
- **注意**: 如果未设置，默认使用 `/`

### 环境变量文件示例

#### .env.develop
```env
# 开发环境配置
VITE_API_BASE_URL=http://localhost:8080
VITE_CDN_URL=/
```

#### .env.test
```env
# 测试环境配置
VITE_API_BASE_URL=https://api-test.example.com
VITE_CDN_URL=https://cdn-test.example.com/
```

#### .env.stag
```env
# 预发布环境配置
VITE_API_BASE_URL=https://api-stag.example.com
VITE_CDN_URL=https://cdn-stag.example.com/
```

#### .env.prod
```env
# 生产环境配置
VITE_API_BASE_URL=https://api.example.com
VITE_CDN_URL=https://cdn.example.com/
```

## 使用说明

### 1. 创建环境变量文件
在 `env` 目录下创建对应的环境变量文件，例如：
- `.env.develop` - 开发环境（团队共享）
- `.env.test` - 测试环境（团队共享）
- `.env.stag` - 预发布环境（团队共享）
- `.env.prod` - 生产环境（团队共享）
- `.env.local` - 本地个人配置（**不提交到仓库**，个人使用）

### 2. 添加环境变量
所有环境变量必须以 `VITE_` 开头，这样才能在客户端代码中通过 `import.meta.env.VITE_XXX` 访问。

### 3. 类型定义
在 `src/vite-env.d.ts` 中定义环境变量的 TypeScript 类型，确保类型安全。

### 4. 本地个人配置（.env.local）
如果需要覆盖团队共享的环境变量配置，可以创建 `.env.local` 文件：

```env
# .env.local
# 此文件不会被提交到版本控制系统
# 用于个人本地开发时的特殊配置

# 覆盖 API 地址（使用个人本地服务器）
VITE_API_BASE_URL=http://localhost:3001

# 添加个人调试配置
VITE_DEBUG=true
```

**使用场景**：
- 个人本地开发时需要使用不同的 API 地址
- 需要临时启用某些调试功能
- 个人 API Key 或 Token（不共享给团队）
- 本地代理配置

### 5. 安全注意事项
- ⚠️ **不要将包含敏感信息的环境变量文件提交到版本控制系统**
- 本目录已在 `.gitignore` 中被忽略（包括 `.env.local`）
- `.env.local` 文件**必须**添加到 `.gitignore`，确保不会意外提交
- 建议在团队中共享环境变量模板文件（如 `.env.example`），但不包含实际敏感值
- 敏感信息（API Key、Token、密码等）应该放在 `.env.local` 中，而不是团队共享的环境变量文件中

## 环境变量加载优先级

Vite 加载环境变量的优先级（从高到低）：

1. **`.env.local`** - 本地覆盖配置（优先级最高，所有环境都会加载）
2. **`.env.[mode].local`** - 特定环境的本地配置（如 `.env.develop.local`）
3. **`.env.[mode]`** - 特定环境的配置（如 `.env.develop`、`.env.prod`）
4. **`.env`** - 默认配置（如果存在）

**加载规则**：
- 优先级高的文件会覆盖优先级低的文件中的同名变量
- `.env.local` 在所有环境下都会被加载，且优先级最高
- `.env.[mode].local` 只在对应模式下加载

## 工作原理

Vite 会根据 `--mode` 参数加载对应的环境变量文件：

**示例：运行 `vite --mode develop` 时，会按以下顺序加载：**
1. `.env`（如果存在）
2. `.env.develop`（特定环境配置）
3. `.env.develop.local`（特定环境的本地配置，如果存在）
4. `.env.local`（本地覆盖配置，优先级最高）

**最终结果**：所有环境变量会被合并，优先级高的会覆盖优先级低的同名变量，然后加载到 `import.meta.env` 对象中，可以在代码中直接使用。

## 相关文件

- `vite.config.ts` - Vite 配置文件，定义了环境变量目录和加载方式
- `src/vite-env.d.ts` - 环境变量的 TypeScript 类型定义
- `src/utils/request.ts` - 使用 `VITE_API_BASE_URL` 配置 axios
- `.gitignore` - 忽略 env 目录，防止敏感信息泄露

