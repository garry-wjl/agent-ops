# Deployment — 部署原则

> 这份文件描述本项目的部署目标、流程和回滚策略。
> 详细操作步骤可以放到 [`deployment/`](deployment/) 子目录下,本文件只列原则和入口。

---

## 1. 部署环境

| 环境 | 用途 | 入口 | 部署频率 |
|------|------|------|---------|
| dev | 开发自测 | <!-- TODO: ops 分配域名,建议 agent-be-dev.garry.internal --> | 每次 commit |
| staging | 集成测试 | <!-- TODO --> | 每天一次 |
| prod | 生产 | <!-- TODO: ops 分配,建议 agent-be.garry.internal --> | 按需 |

> 详见 [PRD-MVP §9](../../docs/agent-sphere/PRD-MVP.md#9-风险与待决问题) — "域名分配" 待 ops 确认。

## 2. 部署流程

<!-- TODO: 待 ops 确认 CI/CD 工具(Jenkins / GitHub Actions)后落地。MVP 阶段:
1. 拷 rd-points-platform 的 pipeline 模板
2. K8s Deployment / Service / Ingress YAML 抽出独立 chart
3. 灰度策略:MVP 单实例,prod 升级时短暂 downtime 可接受(内部产品)
4. 回滚:K8s rollback 上一个 image tag
-->

- TODO

## 3. 配置管理

### 3.1 Spring YAML + 环境变量

配置唯一来源：`rd-agent-be-adapter/src/main/resources/application.yml` 与 `application-{profile}.yml`。  
占位符形如 `${JWT_SECRET:...}`，生产用环境变量 / K8s Secret 覆盖。**不使用 Apollo。**

- **关键配置项**(不在文档贴具体值):
  - `spring.datasource.*` — DB 连接
  - `app.redis.*` — Redis 连接
  - `app.auth.jwt.*` / `app.auth.disable-auth` — JWT / 本地鉴权开关
  - `sandbox.*` / `oss.*` — 沙箱与对象存储

### 3.2 密钥来源

- **K8s Secret / 环境变量**:数据库密码、`JWT_SECRET`、`X-Service-Token` 等
- **YAML 默认值**:仅本地 / 非敏感默认；生产必须覆盖

## 4. 健康检查

- liveness:`/actuator/health/liveness` — 进程活着即返回 200
- readiness:`/actuator/health/readiness` — 检查 DB / Redis 等依赖可用
- 不检查 sphere(sphere 故障不应让 be 被 K8s 重启;走业务降级)

## 5. 回滚

- **触发条件**:错误率连续 5 分钟 > 5% / p99 延迟连续 5 分钟 > SLO 2 倍 / 关键告警
- **操作**:`kubectl rollout undo deployment/rd-agent-be -n rd-agent`
- **回滚后验证**:健康检查通过 + 抽样调一次 `/admin/api/v1/internal/prompts/active`

## 6. 数据迁移

- 使用 Flyway(沿用 platform 模板)管理 PG schema 变更
- **兼容性要求**:
  - 新版本必须能读旧 schema(向后兼容 1 个版本)
  - 删除字段分两步:先标 deprecated 不再写入(发版 1)→ 实际删字段(发版 2)
  - 加新字段:必须 nullable 或有默认值,不阻塞旧版本写入

---

## 部署历史

| 日期 | 版本 | 环境 | 备注 |
|------|------|------|------|
| YYYY-MM-DD | TODO | TODO | TODO |

---

> 详细部署操作步骤建议放到 `deployment/deployment-guide.md` 或类似位置,并在本文件链接过去。
