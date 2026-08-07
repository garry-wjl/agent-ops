# generated/ — 自动生成内容

> 这个目录的所有文件都由脚本/CI 自动生成,**不要手动修改**。
> 任何手改都会在下次重新生成时被覆盖。

---

## 当前已生成的内容(规划)

| 文件 | 内容 | 生成方式 | 更新频率 |
|------|------|---------|---------|
| `db-schema.md` | rd_agent schema 当前快照 | <!-- TODO: jOOQ / Flyway 脚本 --> | 每次 PR |
| `api-spec.json` | OpenAPI 接口定义 | SpringDoc 启动后 dump | 每次 PR |
| `api-spec-internal.json` | Internal API(`/admin/api/v1/internal/*`)单独 spec | SpringDoc 自定义 group | 每次 PR |

> Internal API 单独导出的目的:platform 团队订阅这一份,不会被 admin API 噪声打扰。

## 生成命令

<!-- TODO: 切片 1 工程骨架就位后填入。建议:
```bash
mvn compile && mvn spring-boot:run -Dspring-boot.run.arguments="--api-export=true"
```
或 make 脚本统一封装。
-->

```bash
# TODO: 重新生成命令
```

## CI 集成

<!-- TODO: 切片 1 落地时:
- CI 在 PR 中重新生成,diff 失败 → 提醒补 generated/
- 主分支上 cron 任务每天重新生成,确保不漂移
-->

- TODO

---

> **理念**:把"机械可生成"的内容明确隔离到本目录,这样 agent 才能区分"我可以信任这是真相"和"我需要谨慎对待人工维护"。
