# generated/ — 自动生成内容

> 这个目录的所有文件都由脚本/CI 自动生成,**不要手动修改**。
> 任何手改都会在下次重新生成时被覆盖。

---

## 当前已生成的内容(规划)

| 文件            | 内容                                                               | 生成方式                                                                              | 更新频率     |
| --------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------- | ------------ |
| `api-client.ts` | rd-agent-be Admin API 的 TS client                                 | <!-- TODO: openapi-typescript / orval / hey-api 等工具,从 be 的 OpenAPI spec 生成 --> | 每次 PR / CI |
| `api-types.ts`  | API 请求 / 响应类型                                                | 同上                                                                                  | 每次 PR / CI |
| `i18n-keys.ts`  | 从 `src/i18n/zh-CN.json` 自动生成 key 类型,确保 `t()` 调用类型安全 | <!-- TODO -->                                                                         | 每次 PR      |

> API client 和 types 必须自动生成,不能手写 — 避免和 be 不一致。

## 生成命令

<!-- TODO: 切片 1 工程骨架就位后填入。建议:
```bash
pnpm gen:api      # 拉 be 的 OpenAPI 重新生成 client
pnpm gen:i18n     # 重新生成 i18n key 类型
pnpm gen:all      # 全部
```
-->

```bash
# TODO: 重新生成命令
```

## CI 集成

<!-- TODO: 切片 1 落地时:
- CI 在 PR 中重新生成,diff 失败 → 提醒补 generated/ 或确认 be API 改动
- 主分支上 cron 任务每天重新生成,确保不漂移
-->

- TODO

---

> **理念**:把"机械可生成"的内容明确隔离到本目录,这样 agent 才能区分"我可以信任这是真相"和"我需要谨慎对待人工维护"。
