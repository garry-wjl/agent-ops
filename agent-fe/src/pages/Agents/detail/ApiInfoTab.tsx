/**
 * Agent 详情页 · API 信息 Tab
 *
 * 按业界常见 OpenAPI / 接口文档结构展示对外开放接口：
 * 基本信息 → 请求头 → 请求参数表 → 响应说明与字段表 → 示例。
 */
import type { ReactNode } from 'react';
import { Button, Descriptions, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CopyOutlined } from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';

const { Text, Title } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  borderInfo: '#DBEAFE',
} as const;

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
type ParamLocation = 'header' | 'path' | 'query' | 'body';

interface FieldDoc {
  name: string;
  location?: ParamLocation;
  type: string;
  required: boolean;
  description: string;
}

interface FieldGroup {
  title: string;
  description?: string;
  fields: FieldDoc[];
}

interface ApiSpec {
  method: HttpMethod;
  path: string;
  title: string;
  description: string;
  requestContentType: string;
  responseContentType: string;
  headers: FieldDoc[];
  requestParams: FieldDoc[];
  requestGroups?: FieldGroup[];
  responseSummary: string;
  responseGroups: FieldGroup[];
  curl: string;
  responseExample: string;
}

export interface ApiInfoTabProps {
  agentNum: string;
}

function buildBaseUrl(): string {
  const fromEnv = (import.meta as any)?.env?.VITE_API_BASE_URL ?? '';
  if (fromEnv) return String(fromEnv).replace(/\/+$/, '');
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }
  return '';
}

const AUTH_HEADERS: FieldDoc[] = [
  {
    name: 'Authorization',
    location: 'header',
    type: 'string',
    required: true,
    description: 'Bearer ak-…；秘钥在「秘钥管理」创建，须与请求中的 agentNum 归属一致',
  },
];

const JSON_CONTENT_HEADER: FieldDoc = {
  name: 'Content-Type',
  location: 'header',
  type: 'string',
  required: true,
  description: 'application/json',
};

const SSE_ACCEPT_HEADER: FieldDoc = {
  name: 'Accept',
  location: 'header',
  type: 'string',
  required: false,
  description: '建议 text/event-stream（SSE 调用）',
};

const RESULT_FIELDS: FieldDoc[] = [
  { name: 'code', type: 'number', required: true, description: '业务码；0 表示成功' },
  { name: 'message', type: 'string', required: true, description: '提示信息' },
  { name: 'data', type: 'object | null', required: false, description: '业务数据，见下方对象字段' },
  { name: 'traceId', type: 'string', required: false, description: '链路追踪 ID' },
];

const EVENT_RESPONSE_GROUPS: FieldGroup[] = [
  {
    title: 'SSE 帧',
    description: '每帧仅含 data 行：data: <Event JSON>；鉴权等失败时可能直接返回 application/json 的 Result，而非 SSE。',
    fields: [
      {
        name: 'data',
        type: 'Event (JSON)',
        required: true,
        description: 'AgentScope Event 对象，字段见下表',
      },
    ],
  },
  {
    title: 'Event',
    fields: [
      {
        name: 'type',
        type: 'string',
        required: true,
        description:
          'REASONING | TOOL_RESULT | HINT | AGENT_RESULT | SUMMARY（ALL 仅作过滤，一般不下发）',
      },
      { name: 'message', type: 'Msg', required: true, description: '本帧消息体，见 Msg' },
      {
        name: 'isLast',
        type: 'boolean',
        required: true,
        description: 'true=该 messageId 最终帧（可落库）；false=流式中间帧',
      },
      {
        name: 'source',
        type: 'EventSource | null',
        required: false,
        description: '子 Agent 来源；顶层 Agent 为 null',
      },
      {
        name: 'messageId',
        type: 'string',
        required: true,
        description: '等同 message.id；同 id 多帧属同一逻辑消息',
      },
    ],
  },
  {
    title: 'Msg（message）',
    fields: [
      { name: 'id', type: 'string', required: true, description: '消息 ID；流式多帧共享' },
      { name: 'name', type: 'string', required: false, description: '发送方名称' },
      {
        name: 'role',
        type: 'string',
        required: true,
        description: 'USER | ASSISTANT | SYSTEM | TOOL',
      },
      {
        name: 'content',
        type: 'ContentBlock[]',
        required: true,
        description: '内容块数组，按 type 判别',
      },
      { name: 'metadata', type: 'object', required: false, description: '扩展元数据' },
      { name: 'timestamp', type: 'string', required: false, description: '时间戳' },
      { name: 'usage', type: 'ChatUsage', required: false, description: 'Token 用量，多见最终帧' },
    ],
  },
  {
    title: 'ContentBlock（content[]）',
    description: '以 type 为判别字段；常见子集如下。',
    fields: [
      {
        name: 'type',
        type: 'string',
        required: true,
        description:
          'text | thinking | tool_use | tool_result | image | audio | video | hint | data',
      },
      {
        name: 'text',
        type: 'string',
        required: false,
        description: 'type=text：可见文本',
      },
      {
        name: 'thinking',
        type: 'string',
        required: false,
        description: 'type=thinking：思考内容',
      },
      {
        name: 'id / name / input / state',
        type: 'mixed',
        required: false,
        description:
          'type=tool_use：id、name、input；state=pending|asking|allowed|submitted|finished；content/metadata 可选',
      },
      {
        name: 'id / name / output / state',
        type: 'mixed',
        required: false,
        description:
          'type=tool_result：id 对应 tool_use；output 为 ContentBlock[]；state=success|error|interrupted|denied|running',
      },
    ],
  },
  {
    title: 'ChatUsage（usage）',
    fields: [
      { name: 'inputTokens', type: 'number', required: true, description: '输入 token' },
      { name: 'outputTokens', type: 'number', required: true, description: '输出 token' },
      {
        name: 'cachedTokens',
        type: 'number',
        required: false,
        description: '缓存命中的输入 token，无则 0',
      },
      { name: 'time', type: 'number', required: false, description: '耗时（秒）' },
      {
        name: 'totalTokens',
        type: 'number',
        required: false,
        description: 'inputTokens + outputTokens（序列化时可能出现）',
      },
    ],
  },
  {
    title: 'EventSource（source）',
    description: '仅子 Agent 事件出现。',
    fields: [
      { name: 'agentKey', type: 'string', required: false, description: '运行时句柄' },
      { name: 'agentId', type: 'string', required: false, description: '子 Agent 类型 ID' },
      { name: 'agentName', type: 'string', required: false, description: '展示名' },
      { name: 'sessionId', type: 'string', required: false, description: '子 Agent 会话 ID' },
      { name: 'parentSessionId', type: 'string', required: false, description: '父会话 ID' },
      { name: 'taskId', type: 'string', required: false, description: '异步任务预留' },
      { name: 'depth', type: 'number', required: false, description: '嵌套深度，1=直接子级' },
      {
        name: 'path',
        type: 'string',
        required: false,
        description: '调用路径，如 main/researcher',
      },
    ],
  },
];

const A2UI_RESPONSE_GROUPS: FieldGroup[] = [
  {
    title: 'SSE 帧',
    description:
      '每帧 data 为一个 A2UI v0.9.1 envelope；鉴权失败等可能直接返回 Result JSON。本实现典型顺序：createSurface → updateComponents → 多次 updateDataModel；默认不下发 deleteSurface。',
    fields: [
      {
        name: 'data',
        type: 'Envelope (JSON)',
        required: true,
        description: 'A2UI envelope，字段见下表',
      },
    ],
  },
  {
    title: 'Envelope',
    fields: [
      {
        name: 'version',
        type: 'string',
        required: true,
        description: '固定 "v0.9.1"',
      },
      {
        name: 'createSurface | updateComponents | updateDataModel | deleteSurface',
        type: 'object',
        required: true,
        description: '四选一，每次恰好一个消息体键',
      },
    ],
  },
  {
    title: 'createSurface',
    fields: [
      {
        name: 'surfaceId',
        type: 'string',
        required: true,
        description: 'Surface 标识，默认 main',
      },
      {
        name: 'catalogId',
        type: 'string',
        required: true,
        description: '组件目录 URL（默认官方 basic catalog）',
      },
      {
        name: 'sendDataModel',
        type: 'boolean',
        required: true,
        description: '客户端 action 是否应回传 data model',
      },
    ],
  },
  {
    title: 'updateComponents',
    fields: [
      { name: 'surfaceId', type: 'string', required: true, description: '目标 surface' },
      {
        name: 'components',
        type: 'object[]',
        required: true,
        description: '组件列表；本实现初始含 root Column 与 assistant_text Text',
      },
      {
        name: 'components[].id',
        type: 'string',
        required: true,
        description: '组件 ID（约定：root / assistant_text）',
      },
      {
        name: 'components[].component',
        type: 'string',
        required: true,
        description: '组件类型名，如 Column、Text',
      },
      {
        name: 'components[].children',
        type: 'string[]',
        required: false,
        description: '子组件 ID 列表',
      },
      {
        name: 'components[].text',
        type: 'object',
        required: false,
        description: 'Text 绑定，如 { "path": "/assistantText" }',
      },
      {
        name: 'components[].variant',
        type: 'string',
        required: false,
        description: '如 body',
      },
    ],
  },
  {
    title: 'updateDataModel',
    fields: [
      { name: 'surfaceId', type: 'string', required: true, description: '目标 surface' },
      {
        name: 'path',
        type: 'string',
        required: true,
        description: 'JSON Pointer；助手文案为 /assistantText',
      },
      {
        name: 'value',
        type: 'any',
        required: true,
        description: '该 path 上的值；助手文案为累计全文 string',
      },
    ],
  },
  {
    title: 'deleteSurface',
    description: '协议保留；当前服务端 encoder 不主动发送。',
    fields: [
      {
        name: 'surfaceId',
        type: 'string',
        required: true,
        description: '要删除的 surface',
      },
    ],
  },
];

function buildSpecs(agentNum: string): ApiSpec[] {
  const base = buildBaseUrl();

  return [
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/invoke',
      title: 'Invoke（SSE 流式调用）',
      description:
        '对外触发 Agent 推理，响应为 AgentScope Event SSE。operatorId 可选，留空记为 system。可选 context 用于系统提示词 {{key}} 替换并合并进会话默认上下文。',
      requestContentType: 'application/json',
      responseContentType: 'text/event-stream',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER, SSE_ACCEPT_HEADER],
      requestParams: [
        {
          name: 'agentNum',
          location: 'body',
          type: 'string',
          required: true,
          description: `目标 Agent 编号，须与秘钥归属一致（当前：${agentNum}）`,
        },
        {
          name: 'input',
          location: 'body',
          type: 'string',
          required: true,
          description: '用户输入文本',
        },
        {
          name: 'inputType',
          location: 'body',
          type: 'string',
          required: false,
          description: '输入类型，默认 text（预留多模态）',
        },
        {
          name: 'sessionNum',
          location: 'body',
          type: 'string',
          required: false,
          description: '会话编号；空则由下游创建新会话',
        },
        {
          name: 'operatorId',
          location: 'body',
          type: 'string',
          required: false,
          description: '操作人标识；空记 system',
        },
        {
          name: 'context',
          location: 'body',
          type: 'object',
          required: false,
          description: '扁平键值；替换系统提示词 {{key}}，并浅合并写入会话上下文',
        },
      ],
      responseSummary:
        '成功时为 SSE：每条 data 为一个 Event JSON。流正常结束关闭连接。非流式错误（如缺秘钥）返回 Result JSON。',
      responseGroups: EVENT_RESPONSE_GROUPS,
      curl: `curl -N -X POST '${base}/api/v1/open/agents/command/invoke' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: text/event-stream' \\
  -d '{
    "agentNum": "${agentNum}",
    "input": "这个单什么时候发货？",
    "inputType": "text",
    "sessionNum": null,
    "operatorId": null,
    "context": { "orderId": "ORD-123", "page": "order_detail" }
  }'`,
      responseExample: `data:{"type":"REASONING","message":{"id":"...","name":"agent","role":"ASSISTANT","content":[{"type":"text","text":"正在查询…"}],"metadata":{},"timestamp":"2026-08-15 15:00:00"},"isLast":false,"messageId":"..."}

data:{"type":"AGENT_RESULT","message":{"id":"...","name":"agent","role":"ASSISTANT","content":[{"type":"text","text":"预计 3 个工作日内发货。"}],"timestamp":"2026-08-15 15:00:01","usage":{"inputTokens":100,"outputTokens":20,"cachedTokens":0,"time":1.2}},"isLast":true,"messageId":"..."}

# 非 SSE 错误示例
# {"code":2017,"message":"缺少有效秘钥","data":null,"traceId":"..."}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/a2ui/invoke',
      title: 'A2UI Invoke（v0.9.1 SSE）',
      description:
        '按 A2UI v0.9.1 协议流式返回 UI 消息。与上方 Invoke 并存，不替换原 Event SSE。可选 surfaceId / catalogId；sendDataModel 默认 true。',
      requestContentType: 'application/json',
      responseContentType: 'text/event-stream',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER, SSE_ACCEPT_HEADER],
      requestParams: [
        {
          name: 'agentNum',
          location: 'body',
          type: 'string',
          required: true,
          description: `目标 Agent 编号（当前：${agentNum}）`,
        },
        {
          name: 'input',
          location: 'body',
          type: 'string',
          required: true,
          description: '用户输入文本',
        },
        {
          name: 'sessionNum',
          location: 'body',
          type: 'string',
          required: false,
          description: '会话编号；空则新建',
        },
        {
          name: 'operatorId',
          location: 'body',
          type: 'string',
          required: false,
          description: '操作人标识；空记 system',
        },
        {
          name: 'surfaceId',
          location: 'body',
          type: 'string',
          required: false,
          description: 'A2UI surfaceId，默认 main',
        },
        {
          name: 'catalogId',
          location: 'body',
          type: 'string',
          required: false,
          description: '组件目录 URL，默认官方 basic catalog',
        },
        {
          name: 'sendDataModel',
          location: 'body',
          type: 'boolean',
          required: false,
          description: 'createSurface.sendDataModel，默认 true',
        },
        {
          name: 'context',
          location: 'body',
          type: 'object',
          required: false,
          description: '调用上下文，替换提示词并写入会话',
        },
      ],
      responseSummary:
        '成功时为 SSE：每条 data 为一个 A2UI v0.9.1 envelope（version + 四选一消息体）。',
      responseGroups: A2UI_RESPONSE_GROUPS,
      curl: `curl -N -X POST '${base}/api/v1/open/agents/command/a2ui/invoke' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: text/event-stream' \\
  -d '{
    "agentNum": "${agentNum}",
    "input": "帮我填一个联系表单",
    "sessionNum": null,
    "operatorId": null,
    "surfaceId": "main",
    "catalogId": null,
    "sendDataModel": true,
    "context": { "page": "contact" }
  }'`,
      responseExample: `data:{"version":"v0.9.1","createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json","sendDataModel":true}}

data:{"version":"v0.9.1","updateComponents":{"surfaceId":"main","components":[{"id":"root","component":"Column","children":["assistant_text"]},{"id":"assistant_text","component":"Text","text":{"path":"/assistantText"},"variant":"body"}]}}

data:{"version":"v0.9.1","updateDataModel":{"surfaceId":"main","path":"/assistantText","value":"好的，我来帮你…"}}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/a2ui/action',
      title: 'A2UI Action（客户端回传 → v0.9.1 SSE）',
      description:
        '客户端交互回传（协议 action）。服务端转为 Agent 输入后，继续以 A2UI v0.9.1 SSE 返回 UI 更新。建议携带 sessionNum。',
      requestContentType: 'application/json',
      responseContentType: 'text/event-stream',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER, SSE_ACCEPT_HEADER],
      requestParams: [
        {
          name: 'agentNum',
          location: 'body',
          type: 'string',
          required: true,
          description: `目标 Agent 编号（当前：${agentNum}）`,
        },
        {
          name: 'sessionNum',
          location: 'body',
          type: 'string',
          required: false,
          description: '会话编号；建议携带以延续对话',
        },
        {
          name: 'operatorId',
          location: 'body',
          type: 'string',
          required: false,
          description: '操作人标识；空记 system',
        },
        {
          name: 'action',
          location: 'body',
          type: 'A2uiActionPayload',
          required: true,
          description: '协议 action 载荷，字段见下表',
        },
        {
          name: 'clientDataModel',
          location: 'body',
          type: 'object',
          required: false,
          description: '客户端 data model 快照；推荐 surfaceId → model',
        },
        {
          name: 'context',
          location: 'body',
          type: 'object',
          required: false,
          description: '额外调用上下文',
        },
      ],
      requestGroups: [
        {
          title: 'action（A2uiActionPayload）',
          fields: [
            {
              name: 'action.name',
              location: 'body',
              type: 'string',
              required: true,
              description: '动作名',
            },
            {
              name: 'action.surfaceId',
              location: 'body',
              type: 'string',
              required: true,
              description: '来源 surfaceId',
            },
            {
              name: 'action.sourceComponentId',
              location: 'body',
              type: 'string',
              required: true,
              description: '触发组件 ID',
            },
            {
              name: 'action.timestamp',
              location: 'body',
              type: 'string',
              required: true,
              description: 'ISO-8601 时间戳',
            },
            {
              name: 'action.context',
              location: 'body',
              type: 'object',
              required: true,
              description: '动作上下文（可为空对象）',
            },
          ],
        },
      ],
      responseSummary: '与 a2ui/invoke 相同：SSE 下发 A2UI v0.9.1 envelope。',
      responseGroups: A2UI_RESPONSE_GROUPS,
      curl: `curl -N -X POST '${base}/api/v1/open/agents/command/a2ui/action' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: text/event-stream' \\
  -d '{
    "agentNum": "${agentNum}",
    "sessionNum": "SES-xxx",
    "operatorId": null,
    "action": {
      "name": "submit_form",
      "surfaceId": "main",
      "sourceComponentId": "submit_button",
      "timestamp": "2026-08-15T02:00:00Z",
      "context": { "email": "user@example.com" }
    },
    "clientDataModel": {
      "main": { "email": "user@example.com", "name": "Alice" }
    },
    "context": null
  }'`,
      responseExample: `data:{"version":"v0.9.1","createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json","sendDataModel":true}}

data:{"version":"v0.9.1","updateDataModel":{"surfaceId":"main","path":"/assistantText","value":"已收到提交，正在处理…"}}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/createSession',
      title: '创建 Session',
      description:
        '为当前 Agent 创建一条新的对外会话；可选 context 写入会话默认调用上下文，供后续 invoke 继承。',
      requestContentType: 'application/json',
      responseContentType: 'application/json',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER],
      requestParams: [
        {
          name: 'agentNum',
          location: 'body',
          type: 'string',
          required: true,
          description: `目标 Agent 编号（当前：${agentNum}）`,
        },
        {
          name: 'title',
          location: 'body',
          type: 'string',
          required: false,
          description: '会话标题；空则系统兜底',
        },
        {
          name: 'skillHint',
          location: 'body',
          type: 'string',
          required: false,
          description: 'Skill 提示，可空',
        },
        {
          name: 'operatorId',
          location: 'body',
          type: 'string',
          required: false,
          description: '操作人标识；空记 system',
        },
        {
          name: 'context',
          location: 'body',
          type: 'object',
          required: false,
          description: '会话默认调用上下文，落库供后续 invoke 继承',
        },
      ],
      responseSummary: '统一 Result 包装；code=0 时 data 为 SessionDTO。',
      responseGroups: [
        { title: 'Result', fields: RESULT_FIELDS },
        {
          title: 'data（SessionDTO）',
          fields: [
            { name: 'id', type: 'number', required: true, description: '主键' },
            { name: 'num', type: 'string', required: true, description: '会话业务编号 SES…' },
            { name: 'agentNum', type: 'string', required: true, description: '绑定 Agent 编号' },
            {
              name: 'agentVersionNum',
              type: 'string',
              required: true,
              description: '绑定 Agent 版本编号',
            },
            { name: 'skillHint', type: 'string', required: false, description: 'Skill 提示' },
            {
              name: 'creatorUserId',
              type: 'string',
              required: true,
              description: '创建人 userId',
            },
            { name: 'title', type: 'string', required: false, description: '会话标题' },
            {
              name: 'lastMessageAt',
              type: 'string',
              required: false,
              description: '最近消息时间',
            },
            {
              name: 'origin',
              type: 'string',
              required: true,
              description: '来源：API / DEBUG_CONSOLE',
            },
            {
              name: 'invokeContext',
              type: 'object',
              required: false,
              description: '默认调用上下文',
            },
            { name: 'createNo', type: 'string', required: false, description: '创建人' },
            { name: 'updateNo', type: 'string', required: false, description: '更新人' },
            { name: 'createTime', type: 'string', required: false, description: '创建时间' },
            { name: 'updateTime', type: 'string', required: false, description: '更新时间' },
          ],
        },
      ],
      curl: `curl -X POST '${base}/api/v1/open/agents/command/createSession' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "agentNum": "${agentNum}",
    "title": "外部会话",
    "skillHint": null,
    "operatorId": null,
    "context": { "orderId": "ORD-123" }
  }'`,
      responseExample: `{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "num": "SES2026081515000001",
    "agentNum": "${agentNum}",
    "agentVersionNum": "v1.0.0",
    "title": "外部会话",
    "origin": "API",
    "invokeContext": { "orderId": "ORD-123" },
    "createTime": "2026-08-15 15:00:00"
  },
  "traceId": "..."
}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/query/sessionList',
      title: 'Session 列表（分页）',
      description: '按 pageNo / pageSize 查询当前 Agent 的对外会话列表。',
      requestContentType: 'application/json',
      responseContentType: 'application/json',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER],
      requestParams: [
        {
          name: 'agentNum',
          location: 'body',
          type: 'string',
          required: true,
          description: `目标 Agent 编号（当前：${agentNum}）`,
        },
        {
          name: 'pageNo',
          location: 'body',
          type: 'number',
          required: false,
          description: '页码，默认 1',
        },
        {
          name: 'pageSize',
          location: 'body',
          type: 'number',
          required: false,
          description: '每页条数，默认 20',
        },
        {
          name: 'operatorId',
          location: 'body',
          type: 'string',
          required: false,
          description: '操作人标识；空记 system',
        },
      ],
      responseSummary: '统一 Result 包装；data 为 PageVO<SessionListVO>。',
      responseGroups: [
        { title: 'Result', fields: RESULT_FIELDS },
        {
          title: 'data（PageVO）',
          fields: [
            { name: 'total', type: 'number', required: true, description: '总条数' },
            { name: 'pageNo', type: 'number', required: true, description: '当前页' },
            { name: 'pageSize', type: 'number', required: true, description: '每页大小' },
            {
              name: 'list',
              type: 'SessionListVO[]',
              required: true,
              description: '列表项，见下表',
            },
          ],
        },
        {
          title: 'list[]（SessionListVO）',
          fields: [
            { name: 'num', type: 'string', required: true, description: '会话编号' },
            { name: 'agentNum', type: 'string', required: true, description: 'Agent 编号' },
            {
              name: 'agentVersionNum',
              type: 'string',
              required: true,
              description: 'Agent 版本编号',
            },
            { name: 'title', type: 'string', required: false, description: '标题' },
            {
              name: 'lastMessageAt',
              type: 'string',
              required: false,
              description: '最近消息时间',
            },
            { name: 'origin', type: 'string', required: true, description: 'API / DEBUG_CONSOLE' },
            { name: 'createTime', type: 'string', required: false, description: '创建时间' },
          ],
        },
      ],
      curl: `curl -X POST '${base}/api/v1/open/agents/query/sessionList' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "agentNum": "${agentNum}",
    "pageNo": 1,
    "pageSize": 20,
    "operatorId": null
  }'`,
      responseExample: `{
  "code": 0,
  "message": "success",
  "data": {
    "total": 2,
    "pageNo": 1,
    "pageSize": 20,
    "list": [
      {
        "num": "SES2026081515000001",
        "agentNum": "${agentNum}",
        "agentVersionNum": "v1.0.0",
        "title": "外部会话",
        "lastMessageAt": "2026-08-15 15:01:00",
        "origin": "API",
        "createTime": "2026-08-15 15:00:00"
      }
    ]
  },
  "traceId": "..."
}`,
    },
    {
      method: 'GET',
      path: '/api/v1/open/agents/query/sessionDetail',
      title: 'Session 详情（含消息）',
      description: '按会话 num 拉取详情，返回 messages 数组（时间正序）。',
      requestContentType: '—',
      responseContentType: 'application/json',
      headers: [...AUTH_HEADERS],
      requestParams: [
        {
          name: 'num',
          location: 'query',
          type: 'string',
          required: true,
          description: '会话业务编号',
        },
      ],
      responseSummary: '统一 Result 包装；data 为 SessionDetailVO，含 messages。',
      responseGroups: [
        { title: 'Result', fields: RESULT_FIELDS },
        {
          title: 'data（SessionDetailVO）',
          fields: [
            { name: 'num', type: 'string', required: true, description: '会话编号' },
            { name: 'agentNum', type: 'string', required: true, description: 'Agent 编号' },
            {
              name: 'agentVersionNum',
              type: 'string',
              required: true,
              description: 'Agent 版本编号',
            },
            { name: 'skillHint', type: 'string', required: false, description: 'Skill 提示' },
            { name: 'title', type: 'string', required: false, description: '标题' },
            { name: 'createTime', type: 'string', required: false, description: '创建时间' },
            { name: 'origin', type: 'string', required: true, description: 'API / DEBUG_CONSOLE' },
            {
              name: 'invokeContext',
              type: 'object',
              required: false,
              description: '默认调用上下文',
            },
            {
              name: 'messages',
              type: 'MessageVO[]',
              required: true,
              description: '消息列表，见下表',
            },
          ],
        },
        {
          title: 'messages[]（MessageVO）',
          fields: [
            { name: 'num', type: 'string', required: true, description: '消息编号' },
            {
              name: 'role',
              type: 'string',
              required: true,
              description: 'user / assistant / system',
            },
            {
              name: 'inputType',
              type: 'string',
              required: false,
              description: '输入类型，如 text',
            },
            {
              name: 'content',
              type: 'string | object',
              required: false,
              description: '消息内容，结构随 inputType',
            },
            {
              name: 'stepChain',
              type: 'object',
              required: false,
              description: '执行步骤链（assistant 可能有）',
            },
            {
              name: 'segments',
              type: 'object[]',
              required: false,
              description: '助手分段：thinking / text / tool_use 等',
            },
            { name: 'traceId', type: 'string', required: false, description: '链路 ID' },
            { name: 'createTime', type: 'string', required: false, description: '创建时间' },
          ],
        },
      ],
      curl: `curl -X GET '${base}/api/v1/open/agents/query/sessionDetail?num=SES-xxx' \\
  -H 'Authorization: Bearer ak-xxxxxxxx'`,
      responseExample: `{
  "code": 0,
  "message": "success",
  "data": {
    "num": "SES2026081515000001",
    "agentNum": "${agentNum}",
    "agentVersionNum": "v1.0.0",
    "title": "外部会话",
    "origin": "API",
    "messages": [
      {
        "num": "MSG...",
        "role": "user",
        "inputType": "text",
        "content": "这个单什么时候发货？",
        "createTime": "2026-08-15 15:00:10"
      },
      {
        "num": "MSG...",
        "role": "assistant",
        "inputType": "text",
        "content": "预计 3 个工作日内发货。",
        "segments": [{ "kind": "text", "text": "预计 3 个工作日内发货。" }],
        "createTime": "2026-08-15 15:00:12"
      }
    ]
  },
  "traceId": "..."
}`,
    },
  ];
}

const LOCATION_LABEL: Record<ParamLocation, string> = {
  header: 'Header',
  path: 'Path',
  query: 'Query',
  body: 'Body',
};

const fieldColumns: ColumnsType<FieldDoc> = [
  {
    title: '名称',
    dataIndex: 'name',
    key: 'name',
    width: 200,
    render: (v: string) => (
      <Text code style={{ fontSize: 12 }}>
        {v}
      </Text>
    ),
  },
  {
    title: '位置',
    dataIndex: 'location',
    key: 'location',
    width: 80,
    render: (v?: ParamLocation) => (v ? LOCATION_LABEL[v] : '—'),
  },
  {
    title: '类型',
    dataIndex: 'type',
    key: 'type',
    width: 140,
    render: (v: string) => (
      <Text style={{ fontSize: 12, fontFamily: 'ui-monospace, Menlo, Consolas, monospace' }}>
        {v}
      </Text>
    ),
  },
  {
    title: '必填',
    dataIndex: 'required',
    key: 'required',
    width: 64,
    render: (v: boolean) =>
      v ? <Tag color="red">是</Tag> : <Tag style={{ margin: 0 }}>否</Tag>,
  },
  {
    title: '说明',
    dataIndex: 'description',
    key: 'description',
    render: (v: string) => (
      <span style={{ fontSize: 12, color: COLOR.textSecondary }}>{v}</span>
    ),
  },
];

const responseFieldColumns: ColumnsType<FieldDoc> = fieldColumns.filter(
  (c) => c.key !== 'location',
);

const codeBlockStyle = {
  margin: 0,
  padding: 12,
  background: '#F8FAFC',
  fontSize: 12,
  borderRadius: 6,
  border: `1px solid ${COLOR.borderInfo}`,
} as const;

function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: COLOR.textPrimary,
        marginTop: 12,
        marginBottom: 8,
      }}
    >
      {children}
    </div>
  );
}

function FieldTable({
  fields,
  showLocation,
}: {
  fields: FieldDoc[];
  showLocation?: boolean;
}) {
  return (
    <Table<FieldDoc>
      size="small"
      bordered
      pagination={false}
      rowKey={(r) => `${r.location ?? ''}:${r.name}:${r.type}`}
      columns={showLocation ? fieldColumns : responseFieldColumns}
      dataSource={fields}
      style={{ marginBottom: 4 }}
    />
  );
}

function ApiCard({
  spec,
  onCopy,
}: {
  spec: ApiSpec;
  onCopy: (text: string) => void;
}) {
  return (
    <div
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        padding: 16,
        background: '#fff',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <Tag
          color={
            spec.method === 'GET' ? 'blue' : spec.method === 'POST' ? 'green' : 'orange'
          }
          style={{ fontWeight: 600, margin: 0 }}
        >
          {spec.method}
        </Tag>
        <Text
          style={{
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
            fontSize: 13,
            color: COLOR.textPrimary,
          }}
        >
          {spec.path}
        </Text>
      </div>
      <Title level={5} style={{ margin: '4px 0 0' }}>
        {spec.title}
      </Title>
      <Text style={{ fontSize: 12, color: COLOR.textSecondary }}>{spec.description}</Text>

      <SectionTitle>基本信息</SectionTitle>
      <Descriptions
        size="small"
        bordered
        column={2}
        styles={{ label: { width: 120, fontSize: 12 }, content: { fontSize: 12 } }}
        items={[
          {
            key: 'method',
            label: '请求方式',
            children: <Tag style={{ margin: 0 }}>{spec.method}</Tag>,
          },
          {
            key: 'path',
            label: '请求地址',
            children: (
              <Text code style={{ fontSize: 12 }}>
                {spec.path}
              </Text>
            ),
          },
          {
            key: 'auth',
            label: '鉴权',
            children: 'Authorization: Bearer ak-…',
          },
          {
            key: 'reqCt',
            label: '请求 Content-Type',
            children: spec.requestContentType,
          },
          {
            key: 'respCt',
            label: '响应 Content-Type',
            children: spec.responseContentType,
            span: 2,
          },
        ]}
      />

      <SectionTitle>请求头</SectionTitle>
      <FieldTable fields={spec.headers} showLocation />

      <SectionTitle>请求参数</SectionTitle>
      <FieldTable fields={spec.requestParams} showLocation />
      {spec.requestGroups?.map((g) => (
        <div key={g.title}>
          <div
            style={{
              fontSize: 12,
              fontWeight: 500,
              color: COLOR.textPrimary,
              margin: '8px 0 6px',
            }}
          >
            {g.title}
            {g.description ? (
              <Text style={{ fontWeight: 400, color: COLOR.textMuted, marginLeft: 8 }}>
                {g.description}
              </Text>
            ) : null}
          </div>
          <FieldTable fields={g.fields} showLocation />
        </div>
      ))}

      <SectionTitle>响应说明</SectionTitle>
      <Text style={{ fontSize: 12, color: COLOR.textSecondary, display: 'block', marginBottom: 8 }}>
        {spec.responseSummary}
      </Text>
      {spec.responseGroups.map((g) => (
        <div key={g.title}>
          <div
            style={{
              fontSize: 12,
              fontWeight: 500,
              color: COLOR.textPrimary,
              margin: '8px 0 6px',
            }}
          >
            {g.title}
            {g.description ? (
              <div
                style={{
                  fontWeight: 400,
                  color: COLOR.textMuted,
                  marginTop: 2,
                  fontSize: 12,
                }}
              >
                {g.description}
              </div>
            ) : null}
          </div>
          <FieldTable fields={g.fields} />
        </div>
      ))}

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: 12,
          marginBottom: 8,
        }}
      >
        <SectionTitle>请求示例</SectionTitle>
        <Button size="small" icon={<CopyOutlined />} onClick={() => onCopy(spec.curl)}>
          复制 cURL
        </Button>
      </div>
      <SyntaxHighlighter language="bash" customStyle={codeBlockStyle}>
        {spec.curl}
      </SyntaxHighlighter>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: 12,
          marginBottom: 8,
        }}
      >
        <SectionTitle>响应示例</SectionTitle>
        <Button
          size="small"
          icon={<CopyOutlined />}
          onClick={() => onCopy(spec.responseExample)}
        >
          复制示例
        </Button>
      </div>
      <SyntaxHighlighter language="json" customStyle={codeBlockStyle}>
        {spec.responseExample}
      </SyntaxHighlighter>
    </div>
  );
}

export default function ApiInfoTab({ agentNum }: ApiInfoTabProps) {
  const specs = buildSpecs(agentNum);

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制');
    } catch {
      const el = document.createElement('textarea');
      el.value = text;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
      message.success('已复制');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>
        对外开放接口（/api/v1/open/agents/**）。调用方需在 Header 携带{' '}
        <Text code>Authorization: Bearer ak-xxxx</Text>
        ；秘钥已隐含归属 Agent，请求中的 agentNum 必须与秘钥一致，否则返回 403。非流式接口统一为{' '}
        <Text code>{`{ code, message, data, traceId }`}</Text>
        ；SSE 接口每条事件的 data 为协议消息 JSON。
      </Text>
      {specs.map((s) => (
        <ApiCard key={`${s.method} ${s.path}`} spec={s} onCopy={handleCopy} />
      ))}
    </div>
  );
}
