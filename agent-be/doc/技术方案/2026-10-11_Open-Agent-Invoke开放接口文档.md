# Open Agent Invoke 开放接口文档

> 状态：与现网代码对齐（`feature-20261011-invoke-resume-stream`）  
> 权威实现：`OpenAgentController` / `OpenInvokeParam` / `AgentRunCoordinator`  
> 产品内展示：Agent 详情页「API 信息」Tab（`ApiInfoTab.tsx`）  
> 本文档只描述 **Invoke 开跑与断线续传**；其它 open 接口见 API 信息 Tab。

---

## 1. 端点

| 项 | 值 |
|----|-----|
| Method | `POST` |
| Path | `/api/v1/open/agents/command/invoke` |
| Auth | `Authorization: Bearer ak-…`（`ApiKeyAuthenticationFilter`） |
| Request | `application/json`（字段 **camelCase**） |
| Success | `text/event-stream;charset=UTF-8` |
| Pre-SSE 错误 | `application/json` → `Result{ code, message, data, traceId }` |

进入 SSE 前完成：秘钥校验、`agentNum` 与秘钥归属一致性、附件/空内容校验（失败 → HTTP 4xx JSON，不进流）。

---

## 2. 鉴权与归属

- Header：`Authorization: Bearer <apiKey>`
- Body.`agentNum` **必须**等于秘钥归属 Agent，否则 `2019 API_KEY_AGENT_MISMATCH`
- 工作空间由秘钥隐含注入（不走 `X-Workspace-Num`）
- `operatorId` 可空，空则记 `system`
- 会话 `origin` 固定为 `API`

---

## 3. 请求体（`OpenInvokeParam`）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `agentNum` | string | 是 | 目标 Agent，须与秘钥一致 |
| `input` | string | 开跑时与 attachments 至少一个有内容 | 用户文本；续传时应为空 |
| `inputType` | string | 否 | `text` / `multimodal`（有附件建议 multimodal） |
| `attachments` | array | 开跑时与 input 至少一个有内容 | `[{ fileId, name?, mimeType?, size?, kind? }]`；须先 `uploadAttachment`；续传应为空 |
| `sessionNum` | string | 否 | 空则新建；同会话同时仅一个 RUNNING run |
| `operatorId` | string | 否 | 空 → `system` |
| `context` | object | 否 | 扁平键值；`{{key}}` 替换 + 浅合并会话上下文；续传忽略 |
| `resume` | boolean | 否 | `true` = 纯续传，不启新一轮模型 |
| `runId` | string | 否 | 本轮 runId（`RUN…`）；续传建议带上 |
| `lastEventId` | string | 否 | 已收最后一帧 SSE id，格式 `runId:seq` |

### 3.1 进入续传的条件（任一）

1. `resume === true`
2. 存在 `lastEventId`（body）或 Header `Last-Event-ID`，且 `input`/`attachments` 均为空

`lastEventId` 与 Header `Last-Event-ID` 等价，**body 优先**。

### 3.2 开跑约束

- `input` 与 `attachments` 不能同时为空 → `1001`
- 同一 `sessionNum` 已有 RUNNING run → `4008 SESSION_BUSY`

---

## 4. SSE 协议与事件类型（对接核心）

### 4.0 客户端分发（必读）

1. 按空行拆 SSE 块；解析 `id:` / `event:` / `data:`；`:` 开头为 comment（如 `:heartbeat`），忽略。
2. **有 `event` 行** → 控制帧：`run_started` | `Sandbox.Status` | `run_finished`。
3. **无 `event` 行** → 业务帧：解析 `data` JSON，按 **`data.type`** 分发。
4. 每帧若有 `id`，更新 `lastEventId = id`（格式 `{runId}:{seq}`）。
5. **以 `run_finished` 结束本轮订阅**；`AGENT_RESULT` 只表示业务收尾，其后仍会有 `run_finished`。
6. 断线：同一 URL，`resume=true` + `lastEventId`（或 Header `Last-Event-ID`），`input`/`attachments` 留空。

开跑与 HTTP 订阅解耦：事件先入 Redis Stream；**断开不中止推理**。空闲约每 **10s** `:heartbeat`。  
进入 SSE 前的错误为 `application/json` Result；**不会**下发 SSE `event: error`。

### 4.1 控制帧（有 `event`）

| event | 何时出现 | data 形状 | 客户端动作 |
|-------|----------|-----------|------------|
| `run_started` | 本轮第一帧 | `{ runId, sessionNum, agentNum, status }` **值均为 string**；`status="RUNNING"` | 保存 `runId`、`sessionNum` |
| `Sandbox.Status` | 有沙箱工具时（可整轮不下发） | `{ sessionNum, phase, instanceId?, message?, ts }`；`phase`=IDLE / PREPARING / READY / FAILED；**`ts` 为 number** | 更新沙箱 UI；`FAILED` 可提示 |
| `run_finished` | 本轮终态 | `{ runId, status }` **均为 string**；`status`=DONE / FAILED / CANCELLED | **结束 loading**；FAILED 当失败。续传已收齐时可能再补一条合成帧（`seq=lastSeq+1`） |

### 4.2 业务帧 · `data.type`（无 `event` 行）

`data` 为 AgentScope `Event` JSON。典型顺序：

`REASONING*` ↔ `TOOL_RESULT*`（多轮 ReAct）→ `AGENT_RESULT` →（控制）`run_finished`

| type | 含义 | 如何渲染 | 备注 |
|------|------|----------|------|
| `REASONING` | LLM 文本 / 思考 / 发起工具 | 遍历 `message.content`：`text` 拼回复；`thinking` 拼思考（字段名是 **thinking**）；`tool_use` 见 §4.4 | 可多 chunk：`isLast=false…true`；末帧常带 `usage` |
| `TOOL_RESULT` | 工具执行结果 | `content[]` 中 `type=tool_result`，用 `id` 关联 `tool_use`；`output` 为 ContentBlock[] | 可流式多帧 |
| `HINT` | RAG/Memory/Plan 提示 | 可折叠或忽略 | 单条 |
| `AGENT_RESULT` | 业务结束 | 建议只取 `message.usage`（本轮汇总 Token）；正文可能与 REASONING 重复 | **不要**据此关连接，等 `run_finished` |
| `SUMMARY` | maxIters 等兜底总结 | 按文本展示 | 少见 |
| `ALL` | 仅服务端过滤用 | — | 一般不下发 |

#### Event 对象字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 上表 |
| `message` | Msg | 见下 |
| `isLast` | boolean | 同一 `message.id`：false=中间片追加；true=最终片可落库 |
| `messageId` | string | 等同 `message.id` |
| `source` | object / null | 子 Agent 来源；顶层 null |

#### Msg

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 逻辑消息 ID |
| `role` | string | USER / ASSISTANT / SYSTEM / TOOL |
| `content` | ContentBlock[] | 见 §4.3 |
| `name` | string? | 发送方名 |
| `usage` | ChatUsage? | `{ inputTokens, outputTokens, cachedTokens?, time?, totalTokens? }` |
| `timestamp` | string? | 时间戳 |
| `metadata` | object? | 扩展 |

### 4.3 ContentBlock

| type | JSON 要点 | 处理 |
|------|-----------|------|
| `text` | `{ "type":"text", "text":"…" }` | 拼助手回复 |
| `thinking` | `{ "type":"thinking", "thinking":"…" }` | 拼思考区；**不要**读 `text` |
| `tool_use` | `{ id, name, input?, content? }` | 见 §4.4 fragment |
| `tool_result` | `{ id, name?, output? }` | `id` 对应 tool_use；`output` 为 ContentBlock[] |
| `image` / `audio` / `video` / `hint` / `data` | 扩展 | 按需；未知 type 忽略不报错 |

### 4.4 tool_use fragment 协议（必读）

工具入参常分多帧到达，**必须按 `id` 拼接 `content` 字符串**，不能只读 `input`。

1. **首帧**：`name` = 真实工具名；`input` 可能为 `{}`；`content` = 参数 JSON 第一段（可空）。
2. **后续帧**：同一 `id`，`name` 固定 `"__fragment__"`；将 `content` **追加**到该 id 的缓冲；**不要**用 `__fragment__` 覆盖工具名。
3. **完成**：收到同 id 的 `TOOL_RESULT`，或不再有该 id 的增量时，对缓冲 `JSON.parse` 得到完整入参。

### 4.5 缓冲 TTL（`app.agent-run.*`）

| 配置 | 默认 | 含义 |
|------|------|------|
| `ttl-running` | `2h` | 运行中 key 最长存活 |
| `ttl-after-finish` | `45m` | 结束后重连窗口 |
| `max-events` | `50000` | 单 run 最大事件条数 |
| `poll-interval` | `200ms` | 订阅轮询间隔 |

## 5. 业务码（Invoke / 续传相关）

| code | 枚举 | 说明 |
|------|------|------|
| 1001 | INVALID_PARAM | 开跑内容为空等 |
| 2017 | API_KEY_MISSING | 缺少有效秘钥 |
| 2018 | API_KEY_INVALID | 秘钥无效或已删除 |
| 2019 | API_KEY_AGENT_MISMATCH | 秘钥与 agentNum 不一致 |
| 4004 | RUN_NOT_FOUND | run 不存在/过期；续传缺游标且无进行中 run |
| 4006 | CURSOR_INVALID | Last-Event-ID 非法或不一致 |
| 4007 | CURSOR_EXPIRED | 缓冲已淘汰 |
| 4008 | SESSION_BUSY | 会话已有进行中调用 |

附件相关另见 `41xx`（进入 SSE 前校验）。

---

## 6. 示例

### 6.1 开跑

```bash
curl -N -X POST 'https://{host}/api/v1/open/agents/command/invoke' \
  -H 'Authorization: Bearer ak-xxxxxxxx' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "agentNum": "AGT-xxx",
    "input": "总结一下订单",
    "inputType": "text",
    "sessionNum": null,
    "context": { "orderId": "ORD-123" }
  }'
```

### 6.2 断线续传

```bash
curl -N -X POST 'https://{host}/api/v1/open/agents/command/invoke' \
  -H 'Authorization: Bearer ak-xxxxxxxx' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -H 'Last-Event-ID: RUNxxx:12' \
  -d '{
    "agentNum": "AGT-xxx",
    "sessionNum": "SES-xxx",
    "resume": true,
    "runId": "RUNxxx",
    "lastEventId": "RUNxxx:12"
  }'
```

### 6.3 响应片段（含各事件类型）

```text
id: RUNxxx:1
event: run_started
data: {"runId":"RUNxxx","sessionNum":"SES-xxx","agentNum":"AGT-xxx","status":"RUNNING"}

id: RUNxxx:2
event: Sandbox.Status
data: {"sessionNum":"SES-xxx","phase":"READY","instanceId":"inst-1","ts":1728880000000}

id: RUNxxx:3
data: {"type":"REASONING","message":{"id":"m1","role":"ASSISTANT","content":[{"type":"text","text":"正在"}],"timestamp":"…"},"isLast":false,"messageId":"m1","source":null}

id: RUNxxx:4
data: {"type":"REASONING","message":{"id":"m1","role":"ASSISTANT","content":[{"type":"thinking","thinking":"先查目录…"}]},"isLast":false,"messageId":"m1"}

id: RUNxxx:5
data: {"type":"REASONING","message":{"id":"m2","role":"ASSISTANT","content":[{"type":"tool_use","id":"call_1","name":"shell","input":{},"content":"{\"cmd\":"}]},"isLast":false,"messageId":"m2"}

id: RUNxxx:6
data: {"type":"REASONING","message":{"id":"m2","role":"ASSISTANT","content":[{"type":"tool_use","id":"call_1","name":"__fragment__","content":"\"ls\"}"}]},"isLast":true,"messageId":"m2"}

id: RUNxxx:7
data: {"type":"TOOL_RESULT","message":{"id":"m3","role":"TOOL","content":[{"type":"tool_result","id":"call_1","output":[{"type":"text","text":"a.txt\nb.txt"}]}]},"isLast":true,"messageId":"m3"}

id: RUNxxx:8
data: {"type":"AGENT_RESULT","message":{"id":"m4","role":"ASSISTANT","content":[{"type":"text","text":"目录下有两个文件。"}],"usage":{"inputTokens":100,"outputTokens":20,"totalTokens":120}},"isLast":true,"messageId":"m4"}

id: RUNxxx:9
event: run_finished
data: {"runId":"RUNxxx","status":"DONE"}

:heartbeat
```

---

## 7. 与调试台差异（勿混淆）

| 项 | Open API | 调试台 `/api/v1/debug-console/invoke` |
|----|----------|----------------------------------------|
| 鉴权 | API Key Bearer | Cookie JWT + `X-Workspace-Num` |
| JSON 命名 | camelCase（`sessionNum`, `lastEventId`） | 部分 snake_case（`session_num`, `last_event_id`） |
| origin | `API` | `DEBUG_CONSOLE` |
| 续传语义 | 同一套 `AgentRunCoordinator` | 同一套；前端自动续传 ≤3 次 |

---

## 8. 代码索引

| 组件 | 路径 |
|------|------|
| Controller | `rd-agent-be-adapter/.../open/OpenAgentController.java` |
| 入参 | `rd-agent-be-client/.../agent/OpenInvokeParam.java` |
| 编排 / 续传 | `rd-agent-be-application/.../agentrunner/run/AgentRunCoordinator.java` |
| Redis 缓冲 | `.../agentrunner/run/RedisAgentRunEventStore.java` |
| SSE 映射 | `.../adapter/common/AgentInvokeSseMapper.java` |
| 心跳 | `.../adapter/common/SseKeepAlive.java` |
| 产品文档 Tab | `agent-fe/src/pages/Agents/detail/ApiInfoTab.tsx` |
| 集成测试 | `AgentRunResumeIntegrationTest` |
