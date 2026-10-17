# 用户表单 HITL（request_user_input）

**日期:** 2026-10-17  
**状态:** accepted  
**范围:** agent-be + agent-fe（调试台 / Open / A2UI）

## 1. 目标与边界

- Agent 执行中可暂停，向用户弹出结构化表单；用户提交或取消后**同一 run 续跑**（方案 A：原 SSE 连接不结束）。
- 开关：`ConfigSnapshot.enableUserFormInput`（缺省 false）。为 true 时注册内置 external 工具 `request_user_input`。
- **不**持久化表单 UI 快照到会话消息（调试台只读回放为前端本轮内存态）。历史会话不还原填表卡片。

## 2. 运行时

1. 模型调用 `request_user_input` → AgentScope `TOOL_SUSPENDED` → 业务帧 `AGENT_RESULT`（metadata `agentscope_generate_reason=TOOL_SUSPENDED`）。
2. 控制帧 `event: user_input.required`，payload 见 `UserInputRequiredEvent`（含 `requestId`、`form`）。
3. 客户端 `POST …/user-input/respond`（或 A2UI `submit_user_input` / `cancel_user_input`）。
4. 控制帧 `user_input.accepted`（`status=submitted|cancelled`）→ ToolResult 续跑 → 直至 `run_finished`。

取消时 ToolResult：`{"status":"cancelled","reason":"user_cancelled"}`；提交：`{"status":"submitted","values":{…}}`。

## 3. HTTP API

| 面 | Method | Path | 鉴权 |
|----|--------|------|------|
| 调试台 | POST | `/api/v1/debug-console/user-input/respond` | Cookie JWT + `X-Workspace-Num` |
| Open | POST | `/api/v1/open/agents/command/user-input/respond` | Bearer API Key |

Body（Open 推荐 camelCase；调试台可用 snake_case；Jackson 双向兼容）：

| 字段 | 必填 | 说明 |
|------|------|------|
| requestId | 是 | 来自 `user_input.required` |
| action | 是 | `submit` \| `cancel` |
| values | submit 时建议 | 字段 name → 值 |
| sessionNum / sessionId | 建议 | 与挂起会话一致时校验 |
| runId | 建议 | 与挂起 run 一致时校验 |
| replyId / toolCallId | 否 | 透传元数据 |

产品侧完整字段表与 curl：**Agent 详情 → API 信息 Tab**（`ApiInfoTab.tsx`）。

## 4. SSE 控制帧

| event | 含义 |
|-------|------|
| `user_input.required` | 待填；保持连接，去 respond |
| `user_input.accepted` | 已应答，即将续跑 |
| `user_input.rejected` | 挂起失效/超时等 |

客户端**不得**在挂起态 `AGENT_RESULT` 上关闭 SSE。

## 5. A2UI

- `a2ui/invoke` 流中遇 HITL：下发表单组件 + dataModel（`hitlRequestId`、`values`…）。
- `a2ui/action`：`action.name=submit_user_input|cancel_user_input` → 只唤醒 HITL，短流 ack `/hitlStatus`；原 invoke 连接续跑。

## 6. 工具 schema（摘要）

`request_user_input` 必填：`title`、`fields[]`（每项至少 `name`/`label`/`type`）。  
`type`：`string|textarea|number|boolean|date|enum|multi_enum`。动态枚举须先查工具再写入 `options`。

## 7. 已知限制

- Open respond 无 body.agentNum；靠 API Key 工作空间 + 内存 `requestId`。
- 表单 UI 不入会话持久化。
- 默认挂起等待约 30 分钟（`UserInputHitlService`）。
