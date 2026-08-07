# Deployment — 部署原则

> 这份文件描述本项目的部署目标、流程和回滚策略。
> 详细操作步骤可以放到 [`deployment/`](deployment/) 子目录下,本文件只列原则和入口。

---

## 1. 部署环境

| 环境    | 用途             | 分支                   | 构建命令          | 环境配置文件       |
| ------- | ---------------- | ---------------------- | ----------------- | ------------------ |
| develop | 开发自测         | `develop`              | `pnpm build:dev`  | `env/.env.develop` |
| test    | 集成测试         | `test`                 | `pnpm build:test` | `env/.env.test`    |
| stag    | 预发验证（圈内） | `stag`                 | `pnpm build:stag` | `env/.env.stag`    |
| prod    | 生产（圈内）     | `master` / `version/*` | `pnpm build:prod` | `env/.env.prod`    |

本地覆盖配置使用 `.env.local`（不入版本控制）。

> 与工分平台前端 `rd-points-fe` **完全独立子域**（同一员工需在两边各 GT OAuth 登录一次,PRD §9 已决议接受）。

## 2. CI/CD 流水线

配置文件：`.gitlab-ci.yml`

### 2.1 Pipeline 阶段

```
pre-task → static-test → build → deploy → post-task
```

| 阶段        | 任务                                  | 说明                                         |
| ----------- | ------------------------------------- | -------------------------------------------- |
| pre-task    | `install`                             | pnpm install（仅 pnpm-lock.yaml 变更时触发） |
| static-test | `lint` / `style-lint` / `code-review` | Oxlint + Stylelint + AI Code Review（MR 时） |
| build       | `*-build`                             | 按分支构建对应环境产物                       |
| deploy      | `*-publish` + `*-image-build`         | 静态资源上传 + Docker 镜像构建推送           |
| post-task   | `*-deploy`                            | Kunkka 部署到 K8s                            |

### 2.2 部署架构

```
                    ┌─────────────────────────────────┐
                    │         GitLab CI/CD             │
                    └──────────┬──────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
        圈外环境            圈内环境         镜像构建
     (develop/test)      (stag/prod)      (所有环境)
              │                │                │
              ▼                ▼                ▼
     OSS 静态资源上传    S3 静态资源上传    Harbor 镜像推送
     (ali-oss)          (@aws-sdk/client-s3)     │
              │                │                ▼
              └────────────────┴───────► Kunkka → K8s
```

- **圈外**（develop / test）：静态资源上传阿里云 OSS，配置见 `scripts/deploy/config.yml`（`OSS_*` 环境变量）
- **圈内**（stag / prod）：静态资源上传 S3（MinIO），配置见同一 YAML（`S3_*` 环境变量）
- **所有环境**：同时构建 Docker 镜像（nginx + dist），推送 Harbor，由 Kunkka 部署到 K8s
- **不再使用 Apollo**

### 2.3 新项目接入

修改 `.gitlab-ci.yml` 顶部变量：

```yaml
variables:
  CI_PAMS_EN_NAME: 'your-project-name' # 连字符格式
```

在 GitLab 项目 **Settings → CI/CD → Variables** 中配置部署密钥（对应 `scripts/deploy/config.yml` 占位符）：

| 变量 | 用途 |
| ---- | ---- |
| `OSS_KEY` / `OSS_SECRET` / `OSS_BUCKET` / `OSS_REGION` / `OSS_UPLOAD_PATH` / `OSS_CDN_DOMAIN` | 圈外 OSS 上传 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` / `S3_ENDPOINT` | 圈内 S3 上传 |

### 2.4 镜像构建（公开 nginx）

使用仓库 [`Dockerfile`](../Dockerfile)，基础镜像为 Docker Hub 公开镜像 `nginx:1.27-alpine`：

```bash
pnpm build
docker build -t rd-agent-fe:latest .
```

站点根目录为官方默认 `/usr/share/nginx/html`（见 [`nginx/default.conf`](../nginx/default.conf)）。

## 3. 配置管理

### 3.1 环境变量

> Vite 用 `VITE_*` 前缀的环境变量在构建时注入，位于 `env/` 目录。

- `VITE_API_BASE` / `VITE_API_BASE_URL` — 后端 API 基地址（如 `https://agent-be.garry.internal`）
- `VITE_GT_OAUTH_AUTHORIZE_URL` — GT OAuth 授权页 URL
- `VITE_GT_OAUTH_CLIENT_ID` — GT OAuth client id（public,不算密钥）
- `VITE_SENTRY_DSN` — Sentry DSN（public）
- `VITE_BUILD_VERSION` — git commit hash 或 release tag（用于错误上报关联）

### 3.2 密钥来源

- 部署配置：`scripts/deploy/config.yml`（由 `scripts/deployConfig.ts` 加载，`${ENV}` 展开）
- GitLab CI Variables：注入 `OSS_*` / `S3_*` 等密钥，覆盖 YAML 占位

### 3.3 不能放前端的字段

- ❌ GT OAuth client_secret（只能在 be）
- ❌ 任何后端调用的 service token
- ❌ 数据库连接信息

> Vite 环境变量在构建后嵌入到 JS bundle,**所有 `VITE_*` 都是公开的**。任何"敏感"配置都不能用 `VITE_*`。

## 4. 健康检查

- 前端为静态资源部署，无 liveness/readiness 端点
- 通过 CDN/OSS 监控确认部署成功
- K8s 容器通过 nginx 80 端口健康检查(与 `nginx/default.conf` 的 `listen` 一致)

## 5. 回滚

### 5.1 触发条件

- 首屏白屏
- 关键错误率 Sentry 飙升
- 主流程不可用

### 5.2 Prod 版本管理

prod 环境使用版本化部署，每次发布生成 `v{pipelineId}` 版本目录：

```
OSS/S3 存储结构:
├── v12345/          # 版本目录
│   ├── index.html
│   └── assets/
├── v12346/
├── version.json     # 版本清单（最多保留 10 个版本）
└── index.html       # 当前激活版本的 index.html 副本
```

### 5.3 回滚/前进操作

```bash
# 回退到上一个版本
DEPLOY_MODE=sws-prod pnpm run switch prev

# 前进到下一个版本
DEPLOY_MODE=sws-prod pnpm run switch next
```

需要在 CI Variables 中配置 `OSS_*` / `S3_*`（见 `scripts/deploy/config.yml`）。

### 5.4 非 Prod 环境

develop / test / stag 环境直接覆盖部署，回滚通过重新触发对应分支的 pipeline。

### 5.5 回滚后验证

- GT OAuth 登录走通
- 调试台能选 agent + 发送 + 看到流式输出
- 提示词管理列表能加载

## 6. 静态资源缓存策略

- HTML 文件：`Cache-Control: no-cache`（确保用户拿到最新版本）
- JS / CSS：文件名带 hash，`Cache-Control: max-age=31536000, immutable`
- 字体 / 图片：`Cache-Control: max-age=86400`

## 7. 数据迁移

- 纯前端项目，无数据库迁移

---

## 部署历史

| 日期 | 版本 | 环境 | 备注 |
| ---- | ---- | ---- | ---- |

---

> 详细部署操作步骤建议放到 `deployment/deployment-guide.md` 或类似位置,并在本文件链接过去。
