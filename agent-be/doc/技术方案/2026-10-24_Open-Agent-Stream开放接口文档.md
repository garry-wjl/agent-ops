# Open Agent Stream 开放接口文档（streamEvents）

> 状态：与现网代码对齐（`feature-20261024-agentscope-2.0.4`）  
> 权威实现：`OpenAgentController#stream` / `AgentRunCoordinator#invokeOrResumeStream` / `AgentRunnerService#runAgentFramesV2`  
> 产品内展示：Agent 详情页「API 信息」Tab  
> **本接口与 `/command/invoke` 并存**，不替换粗粒度 Event 协议。

---

## 1. 端点

| 项 | 值 |
|----|-----|
| Method | `POST` |
| Path | `/api/v1/open/agents/command/stream` |
| Auth | `Authorization: Bearer ak-…` |
| Request | `application/json`（**camelCase**，字段同 invoke） |
| Success | `text/event-stream;charset=UTF-8` |
| Pre-SSE 错误 | `application/json` → `Result` |

调试台对应路径：`POST /api/v1/debug-console/stream`（登录态 + `agent:invoke`；请求体 snake_case，同原 `/debug-console/invoke`）。

---

## 2. 与 `/command/invoke` 的关系

| | `/command/invoke` | `/command/stream`（本接口） |
|---|---|---|
| AgentScope API | `stream()` → 粗粒度 `Event` | `streamEvents()` → 细粒度 `AgentEvent` |
| 业务帧 `data.type` | `REASONING` / `TOOL_RESULT` / `AGENT_RESULT`… | `TEXT_BLOCK_DELTA` / `TOOL_CALL_*` / `TOOL_RESULT_*` / `AGENT_RESULT`… |
| 控制帧 | 相同：`run_started` / `Sandbox.Status` / `user_input.*` / `run_finished` | 同左 |
| 续传 / HITL / 心跳 | 相同 | 同左 |
| `run_started.data.protocol` | 无 | `"agent_events_v2"` |

旧客户端继续用 invoke；新客户端或调试台用 stream。

---

## 3. 请求体

与 [Open Agent Invoke](./2026-10-11_Open-Agent-Invoke开放接口文档.md) 第 3 节完全一致（`OpenInvokeParam`）。

---

## 4. SSE 协议

### 4.1 控制帧

同 invoke 文档 §4.1。`run_started` 额外可带：

```json
{ "runId":"RUN…", "sessionNum":"SES…", "agentNum":"AGT…", "status":"RUNNING", "protocol":"agent_events_v2" }
```

### 4.2 业务帧（无 `event` 行）— `data` 为 AgentScope `AgentEvent` JSON

典型顺序：

`AGENT_START` →（`TEXT_BLOCK_*` / `THINKING_BLOCK_*` / `TOOL_CALL_*` / `TOOL_RESULT_*`）* → `AGENT_RESULT` → `AGENT_END` →（控制）`run_finished`

| type | 含义 | 客户端建议 |
|------|------|------------|
| `TEXT_BLOCK_DELTA` | 文本增量 `delta` | 拼接到助手回复 |
| `THINKING_BLOCK_DELTA` | 思考增量 `delta` | 拼接到 thinking 区 |
| `TOOL_CALL_START` / `TOOL_CALL_DELTA` / `TOOL_CALL_END` | 工具调用参数流 | 按 `toolCallId` 建卡；DELTA 拼参数 JSON |
| `TOOL_RESULT_TEXT_DELTA` / `TOOL_RESULT_END` | 工具结果流 | 按 `toolCallId` 回填 |
| `REQUIRE_EXTERNAL_EXECUTION` | 外部工具挂起（含表单 HITL） | 等待随后 `user_input.required`；勿 abort |
| `AGENT_RESULT` | 业务结束（字段 `result` 为 Msg） | 可读 `result.usage`；勿仅凭此关连接 |
| `AGENT_END` | 生命周期结束 | 可忽略 |
| 其它 | `AGENT_START` / `HINT_BLOCK` / … | 可忽略或日志 |

HITL：`REQUIRE_EXTERNAL_EXECUTION`（工具名 `request_user_input`）→ `user_input.required` → `POST …/user-input/respond` → `user_input.accepted` → 同连接续跑。响应路径与 invoke 相同。

---

## 5. cURL

```bash
curl -N -X POST 'https://{host}/api/v1/open/agents/command/stream' \
  -H 'Authorization: Bearer ak-xxxxxxxx' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "agentNum": "AGT…",
    "input": "你好",
    "sessionNum": null
  }'
```

续传与 invoke 相同：`resume=true` + `lastEventId` / Header `Last-Event-ID`。
