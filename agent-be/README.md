# rd-agent-be

> Agent Sphere 管理后台 Control Plane 后端 — 鉴权 / Thread / Prompt / 审计 / 透传 sphere。

**技术栈:** Java 17 + Spring Boot(拷自 rd-points-platform 架构)

---

## 给人类的入口

这是一个 **agent-first** 的项目骨架。所有结构、约束和工作流都是为了让 AI agent 能高效推理整个仓库。

**如果你是 agent**:去看 [AGENTS.md](AGENTS.md)。

**如果你是人类,第一次进入**:
1. 读 [AGENTS.md](AGENTS.md) 理解整体地图
2. 读 [ARCHITECTURE.md](ARCHITECTURE.md) 理解 Control Plane 与 Data Plane 的职责边界
3. 浏览 [docs/](docs/) 看你感兴趣的领域
4. 完整产品需求见 [PRD-MVP.md](../docs/agent-sphere/PRD-MVP.md)

## 快速开始

### 前置
- **JDK 17**（`JAVA_HOME` 指向 17）。
- 配置一律从 Spring Boot YAML 读取：`rd-agent-be-adapter/src/main/resources/application.yml` + `application-{profile}.yml`（可用环境变量覆盖占位符）。**已不再使用 Apollo**。

### 1. 构建
```bash
mvn -DskipTests clean install
```

### 2. 启动
```bash
# 方式 A：Maven（默认 profile=dev，见 application.yml）
mvn -pl rd-agent-be-adapter spring-boot:run

# 方式 B：IDEA 运行 AgentBeApplication.main()
#   可选 VM options: --add-opens=java.base/java.lang=ALL-UNNAMED
#   可选覆盖: SPRING_PROFILES_ACTIVE=dev
```

切换环境用 Spring profile（不是 Apollo `ENV`）：

```bash
SPRING_PROFILES_ACTIVE=test mvn -pl rd-agent-be-adapter spring-boot:run
```

本地 `dev` 默认连本机 MySQL / Redis，并默认 `app.auth.disable-auth=false`（用户名密码登录；见 `application-dev.yml`）。需要免登时设 `AUTH_DISABLED=true`。生产敏感项用环境变量覆盖，例如 `JWT_SECRET`、`DB_PASSWORD`。

启动成功标志：日志出现 `Tomcat ... port 8081 (http)`。

## 验证

```bash
mvn -DskipTests clean package   # 编译 + 装配六层
mvn test                        # 单测 + ArchUnit 架构守卫
```

### 常见启动报错速查

| 报错 | 原因 | 解决 |
|---|---|---|
| `InaccessibleObjectException: ... does not "opens java.lang"` | 缺 `--add-opens` | IDE 跑 `main()` 时在 VM options 加 `--add-opens=java.base/java.lang=ALL-UNNAMED`；或改用 `mvn spring-boot:run` |
| 数据源 / Redis 连不上 | 本机未起 MySQL/Redis，或密码与 `application-dev.yml` 不一致 | 起依赖服务，或用 `DB_*` / `REDIS_*` 环境变量覆盖 |
| 多个 VM 参数粘连 / `找不到主类 java.lang=ALL-UNNAMED` | VM options 用了 `;` 或参数被空格拆断 | VM options **用空格分隔**，`--add-opens` 用 `=` 单 token 形式 |

## 文档

详见 [docs/](docs/) 目录。`AGENTS.md` 第 5 节有完整的 Lookup Table。

## 贡献

- 写代码前请先输出实现方案
- 任何架构层面的决定都要落到 `docs/design-docs/` 里
- 复杂任务请在 `docs/exec-plans/active/` 留下计划
- 跨 fe/be 接口契约改动需要回到 [`../rd-agent-ws/docs/CONVENTIONS.md`](../rd-agent-ws/docs/CONVENTIONS.md) 同步

---

> 本骨架由 [`harness-init`](https://openai.com/index/harness-engineering/) skill 生成。
