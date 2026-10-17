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

const LAST_EVENT_ID_HEADER: FieldDoc = {
  name: 'Last-Event-ID',
  location: 'header',
  type: 'string',
  required: false,
  description:
    'SSE 标准续传游标，格式 runId:seq（与最近一帧的 id 相同）。与 body.lastEventId 二选一，body 优先；配合空 input/attachments 或 resume=true 进入续传',
};

const RESULT_FIELDS: FieldDoc[] = [
  { name: 'code', type: 'number', required: true, description: '业务码；0 表示成功' },
  { name: 'message', type: 'string', required: true, description: '提示信息' },
  { name: 'data', type: 'object | null', required: false, description: '业务数据，见下方对象字段' },
  { name: 'traceId', type: 'string', required: false, description: '链路追踪 ID' },
];

const EVENT_RESPONSE_GROUPS: FieldGroup[] = [
  {
    title: '① 推荐客户端处理流程（对接必读）',
    description:
      '按帧解析 SSE 块（以空行分隔）。先看有无 event 行，再看 data。每帧更新 lastEventId=id。以 run_finished 作为本轮订阅结束（不要只靠 AGENT_RESULT 关连接）。断线后用 resume + lastEventId 续传，勿重复提交用户 input。',
    fields: [
      {
        name: 'step1',
        type: 'parse',
        required: true,
        description: '解析块内 id: / event: / data:；以 : 开头的行为 comment（如 :heartbeat），忽略',
      },
      {
        name: 'step2',
        type: 'dispatch',
        required: true,
        description:
          '若 event 为 run_started | run_finished | Sandbox.Status | user_input.* → 走控制帧；若无 event 行 → 按 data.type 走业务帧',
      },
      {
        name: 'step3',
        type: 'cursor',
        required: true,
        description: '有 id 则 lastEventId=id（格式 RUN…:数字）；续传原样回传',
      },
      {
        name: 'step4',
        type: 'lifecycle',
        required: true,
        description:
          'run_started：记 runId/sessionNum；业务帧：渲染；user_input.required：展示表单并保持 SSE 不断开，POST …/user-input/respond；run_finished：结束 loading（status=FAILED 当失败）',
      },
      {
        name: 'step5',
        type: 'resume',
        required: true,
        description:
          '网络断开：同一接口 POST，resume=true + lastEventId（或 Header Last-Event-ID），input/attachments 留空；从断点后续帧开始，无重复、不重跑。HITL 挂起期间断线亦可用续传接回同一 run',
      },
    ],
  },
  {
    title: '② SSE 帧（通用字段）',
    description:
      '开跑与 HTTP 订阅解耦：事件先入 Redis Stream 再下发；断开不中止推理。空闲约每 10s `:heartbeat`。进入 SSE 前的错误为 application/json Result，不会出现 SSE event=error。',
    fields: [
      {
        name: 'id',
        type: 'string',
        required: true,
        description: 'SSE id = {runId}:{seq}，seq 从 1 单调递增；续传游标',
      },
      {
        name: 'event',
        type: 'string',
        required: false,
        description:
          '有则：run_started | run_finished | Sandbox.Status | user_input.required | user_input.accepted | user_input.rejected；无 event 行 = AgentScope 业务帧',
      },
      {
        name: 'data',
        type: 'JSON',
        required: true,
        description: '控制帧见下表；业务帧为 Event 对象（含 type/message/isLast/…）',
      },
    ],
  },
  {
    title: '③ 控制帧总览（有 event 名）',
    description:
      '先匹配 event，再解析 data。run_started / run_finished 的 data 字段值均为 JSON 字符串（非 number/boolean）。user_input.* 仅当 Agent 开启 enableUserFormInput 且模型调用 request_user_input 时出现。',
    fields: [
      {
        name: 'run_started',
        type: 'control',
        required: true,
        description:
          '本轮第一帧。data: { runId, sessionNum, agentNum, status:"RUNNING" }（值均为 string）。必须保存 runId、sessionNum',
      },
      {
        name: 'Sandbox.Status',
        type: 'control',
        required: false,
        description:
          '沙箱状态（Agent 未挂沙箱工具时可能整轮不下发）。data: { sessionNum, phase, instanceId?, message?, ts }；phase=IDLE|PREPARING|READY|FAILED；ts 为 number（epoch ms）',
      },
      {
        name: 'user_input.required',
        type: 'control',
        required: false,
        description:
          'HITL 待填表单。data: { runId, sessionId, replyId, requestId, toolCallId, toolName:"request_user_input", form:{ title, description?, fields:[{name,label,type,required?,options?,…}] } }。收到后须保持本 SSE 连接，向 POST /command/user-input/respond 提交；勿因随后的挂起态 AGENT_RESULT 关闭连接',
      },
      {
        name: 'user_input.accepted',
        type: 'control',
        required: false,
        description:
          'respond 成功后、Agent 续跑前。data: { runId, requestId, status:"submitted"|"cancelled" }。其后继续有业务帧，直至 run_finished',
      },
      {
        name: 'user_input.rejected',
        type: 'control',
        required: false,
        description:
          '挂起失效/超时等拒绝。data 含 message/reason 类说明；客户端应提示用户并勿再对该 requestId respond',
      },
      {
        name: 'run_finished',
        type: 'control',
        required: true,
        description:
          '本轮终态，订阅应结束。data: { runId, status }（string）；status=DONE|FAILED|CANCELLED。续传已收齐时可能再补一条合成帧（seq=lastSeq+1）',
      },
    ],
  },
  {
    title: '④ 业务帧 · Event.type（无 event 名时看 data.type）',
    description:
      'data 为 AgentScope Event JSON。典型顺序：多次 REASONING（流式）↔ TOOL_RESULT（多轮 ReAct）→ AGENT_RESULT → 随后还有 run_finished。HITL 时：AGENT_RESULT（TOOL_SUSPENDED）→ user_input.required →（respond）→ user_input.accepted → 续跑 REASONING… → AGENT_RESULT → run_finished。',
    fields: [
      {
        name: 'REASONING',
        type: 'EventType',
        required: true,
        description:
          '主展示通道：LLM 文本/思考/发起工具。可多 chunk（isLast=false…true）。遍历 message.content：text 拼回复；thinking 拼思考；tool_use 展示工具调用（见 fragment 协议）',
      },
      {
        name: 'TOOL_RESULT',
        type: 'EventType',
        required: false,
        description:
          '工具执行结果。content 内 type=tool_result，用 id 关联先前 tool_use；output 为 ContentBlock[]（常含 text）',
      },
      {
        name: 'HINT',
        type: 'EventType',
        required: false,
        description: 'RAG/Memory/Plan 等提示，单条；UI 可折叠或忽略',
      },
      {
        name: 'AGENT_RESULT',
        type: 'EventType',
        required: true,
        description:
          '本轮 Agent 业务结束帧。message.usage 为本轮汇总 Token。若 message.metadata.agentscope_generate_reason=TOOL_SUSPENDED 且含 tool_use name=request_user_input：这是 HITL 挂起，其后还有 user_input.required，客户端不得据此 abort SSE；真正结束仍看 run_finished',
      },
      {
        name: 'SUMMARY',
        type: 'EventType',
        required: false,
        description: '触及 maxIters 等场景的兜底总结；按文本展示即可',
      },
    ],
  },
  {
    title: '⑤ Event 对象字段',
    fields: [
      {
        name: 'type',
        type: 'string',
        required: true,
        description: 'REASONING | TOOL_RESULT | HINT | AGENT_RESULT | SUMMARY',
      },
      { name: 'message', type: 'Msg', required: true, description: '见 Msg / ContentBlock' },
      {
        name: 'isLast',
        type: 'boolean',
        required: true,
        description:
          '同一 message.id 的流式切片：false=中间片（追加渲染）；true=该消息最终片（可落库；REASONING 末帧常带 usage）',
      },
      {
        name: 'messageId',
        type: 'string',
        required: true,
        description: '等同 message.id；同 id 多帧属同一逻辑消息',
      },
      {
        name: 'source',
        type: 'EventSource | null',
        required: false,
        description: '子 Agent 来源；顶层为 null',
      },
    ],
  },
  {
    title: '⑥ Msg（message）',
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
        description: '内容块数组，按 type 判别（见下）',
      },
      { name: 'metadata', type: 'object', required: false, description: '扩展元数据' },
      { name: 'timestamp', type: 'string', required: false, description: '时间戳字符串' },
      {
        name: 'usage',
        type: 'ChatUsage',
        required: false,
        description: 'Token 用量；多见 REASONING isLast / AGENT_RESULT',
      },
    ],
  },
  {
    title: '⑦ ContentBlock（content[]）',
    description: '以 type 判别。思考字段名是 thinking（不是 text）。',
    fields: [
      {
        name: 'type=text',
        type: 'block',
        required: false,
        description: '{ "type":"text", "text":"可见增量或全文" } — 拼到助手回复',
      },
      {
        name: 'type=thinking',
        type: 'block',
        required: false,
        description: '{ "type":"thinking", "thinking":"…" } — 拼到思考区；勿读 text 字段',
      },
      {
        name: 'type=tool_use',
        type: 'block',
        required: false,
        description:
          '{ id, name, input?, content? }。首帧 name=真实工具名；后续同 id 且 name="__fragment__" 时把 content 字符串拼到参数 JSON（见 ⑧）',
      },
      {
        name: 'type=tool_result',
        type: 'block',
        required: false,
        description:
          '{ id, name?, output? }；id 对应 tool_use；output 为 ContentBlock[]（常见再嵌套 text）',
      },
      {
        name: 'type=image|audio|video|hint|data',
        type: 'block',
        required: false,
        description: '多模态/扩展块；按需展示，未知 type 建议忽略不报错',
      },
    ],
  },
  {
    title: '⑧ tool_use fragment 协议（对接必读）',
    description: '工具参数可能分多帧到达，必须按 id 拼接 content，不能只读 input。',
    fields: [
      {
        name: '首帧',
        type: 'tool_use',
        required: true,
        description:
          'name=真实工具名（如 shell）；input 可能为 {} 或不完整；content 为参数 JSON 字符串第一段（可空）',
      },
      {
        name: '后续帧',
        type: 'tool_use',
        required: false,
        description:
          '同一 id；name 固定 "__fragment__"；把 content 追加到该 id 的参数缓冲；不要用 __fragment__ 覆盖工具名',
      },
      {
        name: '完成时机',
        type: 'hint',
        required: false,
        description:
          '收到同 id 的 TOOL_RESULT（content 内 tool_result）或后续 REASONING 不再追加该 id 时，将缓冲 JSON.parse 为完整入参',
      },
    ],
  },
  {
    title: '⑨ ChatUsage（usage）',
    fields: [
      { name: 'inputTokens', type: 'number', required: true, description: '输入 token' },
      { name: 'outputTokens', type: 'number', required: true, description: '输出 token' },
      {
        name: 'cachedTokens',
        type: 'number',
        required: false,
        description: '缓存命中输入 token，无则 0',
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
    title: '⑩ EventSource（source）',
    description: '仅子 Agent 事件出现；顶层为 null。',
    fields: [
      { name: 'agentKey', type: 'string', required: false, description: '运行时句柄' },
      { name: 'agentId', type: 'string', required: false, description: '子 Agent 类型 ID' },
      { name: 'agentName', type: 'string', required: false, description: '展示名' },
      { name: 'sessionId', type: 'string', required: false, description: '子 Agent 会话 ID' },
      { name: 'parentSessionId', type: 'string', required: false, description: '父会话 ID' },
      { name: 'taskId', type: 'string', required: false, description: '异步任务预留' },
      { name: 'depth', type: 'number', required: false, description: '嵌套深度，1=直接子级' },
      { name: 'path', type: 'string', required: false, description: '调用路径，如 main/researcher' },
    ],
  },
  {
    title: '⑪ 错误与业务码',
    description:
      '进入 SSE 前失败 → HTTP + Result JSON（无 SSE）。流内失败 → 仍可能收到业务帧，最后 run_finished.status=FAILED。无 SSE event=error。',
    fields: [
      { name: '1001', type: 'INVALID_PARAM', required: false, description: '开跑时 input/attachments 同时为空等' },
      { name: '2017', type: 'API_KEY_MISSING', required: false, description: '缺少有效秘钥' },
      { name: '2018', type: 'API_KEY_INVALID', required: false, description: '秘钥无效或已删除' },
      { name: '2019', type: 'API_KEY_AGENT_MISMATCH', required: false, description: '秘钥与 body.agentNum 不一致' },
      { name: '4004', type: 'RUN_NOT_FOUND', required: false, description: 'run 不存在/过期；续传缺游标且无进行中 run' },
      { name: '4006', type: 'CURSOR_INVALID', required: false, description: 'Last-Event-ID 非法或与 runId 不一致' },
      { name: '4007', type: 'CURSOR_EXPIRED', required: false, description: '事件缓冲已淘汰' },
      { name: '4008', type: 'SESSION_BUSY', required: false, description: '同一 sessionNum 已有进行中 run' },
      { name: '41xx', type: 'ATTACHMENT_*', required: false, description: '附件非法/不存在/越权/过大等（进 SSE 前）' },
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
      path: '/api/v1/open/agents/command/uploadAttachment',
      title: '上传附件（预签名）',
      description:
        '开放侧上传聊天附件：返回 OSS 预签名后客户端 PUT 文件字节。成功后服务端登记 chat_attachment（永不删除）。随后在 invoke 的 attachments[] 中引用 fileId。',
      requestContentType: 'application/json',
      responseContentType: 'application/json',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER],
      requestParams: [
        {
          name: 'agentNum',
          location: 'body',
          type: 'string',
          required: true,
          description: `目标 Agent 编号，须与秘钥归属一致（当前：${agentNum}）`,
        },
        {
          name: 'fileName',
          location: 'body',
          type: 'string',
          required: true,
          description: '原始文件名',
        },
        {
          name: 'contentType',
          location: 'body',
          type: 'string',
          required: true,
          description: 'MIME，须在白名单内（图片/pdf/docx/xlsx/txt/md）',
        },
        {
          name: 'size',
          location: 'body',
          type: 'number',
          required: true,
          description: '文件字节大小（正数，默认上限 10MB）',
        },
      ],
      responseSummary:
        'Result.data 含 fileId、url、method、expiration、signedHeaders；客户端按 method 直传 OSS。',
      responseGroups: [
        {
          title: 'OssPresignResultVO',
          fields: [
            { name: 'fileId', type: 'string', required: true, description: '业务文件标识，invoke attachments 引用' },
            { name: 'url', type: 'string', required: true, description: '预签名 PUT URL' },
            { name: 'method', type: 'string', required: true, description: 'HTTP 方法，通常 PUT' },
            { name: 'expiration', type: 'string', required: true, description: '签名过期时间' },
            { name: 'signedHeaders', type: 'object', required: false, description: '需附带的签名头，可为空对象' },
          ],
        },
      ],
      curl: `curl -X POST '${base}/api/v1/open/agents/command/uploadAttachment' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "agentNum": "${agentNum}",
    "fileName": "合同.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "size": 512000
  }'`,
      responseExample: `{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "filexxxx",
    "url": "https://oss.example.com/...?x-oss-signature=...",
    "method": "PUT",
    "expiration": "2026-08-16T12:00:00Z",
    "signedHeaders": {}
  }
}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/invoke',
      title: 'Invoke（SSE 流式调用 / 断线续传）',
      description:
        '对外触发 Agent 推理或续传同一轮 run。字段为 camelCase（与调试台 snake_case 不同）。开跑：input 与 attachments 至少一个有内容；会话 origin 记为 API；operatorId 空则 system；可选 context 做系统提示词 {{key}} 替换并浅合并进会话。图片走 Vision，文档由模型按需调用内置 read_attachment。HTTP 断开不会中止本轮推理（事件写入 Redis 缓冲）。续传：resume=true，或 lastEventId/Last-Event-ID + 空 input/attachments；从缓冲重放并跟随 live，不会再次调用模型。同一 sessionNum 仅允许一个 RUNNING run（否则 4008）。缓冲默认运行中最长约 2h、结束后约 45m 可续传。校验失败在进入 SSE 前返回 4xx JSON。',
      requestContentType: 'application/json',
      responseContentType: 'text/event-stream',
      headers: [
        ...AUTH_HEADERS,
        JSON_CONTENT_HEADER,
        SSE_ACCEPT_HEADER,
        LAST_EVENT_ID_HEADER,
      ],
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
          required: false,
          description:
            '用户输入文本；开跑时与 attachments 二选一或并存（纯附件合法）。续传时应为空',
        },
        {
          name: 'inputType',
          location: 'body',
          type: 'string',
          required: false,
          description: '输入类型：text / multimodal（有附件时建议 multimodal）',
        },
        {
          name: 'attachments',
          location: 'body',
          type: 'array',
          required: false,
          description:
            '已上传登记的附件引用：[{ fileId, name?, mimeType?, size?, kind? }]；须先调 uploadAttachment。续传时应为空',
        },
        {
          name: 'sessionNum',
          location: 'body',
          type: 'string',
          required: false,
          description:
            '会话编号；开跑时空则新建。续传时建议带上；无 lastEventId/runId 时可凭进行中 run 解析',
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
          description: '扁平键值；替换系统提示词 {{key}}，并浅合并写入会话上下文（续传忽略）',
        },
        {
          name: 'resume',
          location: 'body',
          type: 'boolean',
          required: false,
          description:
            'true=纯续传，不启新一轮模型。也可省略，改用 lastEventId + 空 input/attachments 隐式续传',
        },
        {
          name: 'runId',
          location: 'body',
          type: 'string',
          required: false,
          description: '可选本轮 runId（形如 RUN…）；续传时建议带上，须与 lastEventId 中的 runId 一致',
        },
        {
          name: 'lastEventId',
          location: 'body',
          type: 'string',
          required: false,
          description:
            '已收到的最后一条 SSE id，格式 runId:seq；续传从下一条开始。与 Header Last-Event-ID 等价，body 优先',
        },
      ],
      responseSummary:
        '成功为 text/event-stream。分发：有 event → 控制帧（run_started / Sandbox.Status / user_input.* / run_finished）；无 event → 业务帧看 data.type（REASONING|TOOL_RESULT|HINT|AGENT_RESULT|SUMMARY）。每帧保存 id。以 run_finished 结束订阅（AGENT_RESULT 只表示业务收尾；HITL 挂起态 AGENT_RESULT 后还有 user_input.required，勿关连接）。tool_use 须按 id 拼接 __fragment__ 的 content。取消订阅不取消服务端 run。Agent 须开启 enableUserFormInput 才会出现表单 HITL。',
      responseGroups: EVENT_RESPONSE_GROUPS,
      curl: `# —— 开跑 ——
curl -N -X POST '${base}/api/v1/open/agents/command/invoke' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: text/event-stream' \\
  -d '{
    "agentNum": "${agentNum}",
    "input": "总结附件并看图",
    "inputType": "multimodal",
    "attachments": [
      { "fileId": "filexxxx", "name": "合同.docx", "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "size": 512000, "kind": "file" },
      { "fileId": "fileyyyy", "name": "截图.png", "mimeType": "image/png", "size": 102400, "kind": "image" }
    ],
    "sessionNum": null,
    "operatorId": null,
    "context": { "orderId": "ORD-123", "page": "order_detail" }
  }'

# —— 断线续传（保存上一帧 id，例如 RUNxxx:12）——
curl -N -X POST '${base}/api/v1/open/agents/command/invoke' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: text/event-stream' \\
  -H 'Last-Event-ID: RUNxxx:12' \\
  -d '{
    "agentNum": "${agentNum}",
    "sessionNum": "SES-xxx",
    "resume": true,
    "runId": "RUNxxx",
    "lastEventId": "RUNxxx:12"
  }'`,
      responseExample: `# —— 控制帧：run_started（data 字段值均为 string）——
id: RUN2109…:1
event: run_started
data: {"runId":"RUN2109…","sessionNum":"SES-xxx","agentNum":"${agentNum}","status":"RUNNING"}

# —— 控制帧：Sandbox.Status（可选；ts 为 number）——
id: RUN2109…:2
event: Sandbox.Status
data: {"sessionNum":"SES-xxx","phase":"READY","instanceId":"inst-1","ts":1728880000000}

# —— 业务帧：REASONING 流式文本（无 event 行；isLast=false）——
id: RUN2109…:3
data: {"type":"REASONING","message":{"id":"m1","role":"ASSISTANT","content":[{"type":"text","text":"正在"}],"timestamp":"2026-10-11 20:00:00"},"isLast":false,"messageId":"m1","source":null}

# —— 业务帧：REASONING 思考块（字段名 thinking）——
id: RUN2109…:4
data: {"type":"REASONING","message":{"id":"m1","role":"ASSISTANT","content":[{"type":"thinking","thinking":"先查订单状态…"}]},"isLast":false,"messageId":"m1"}

# —— 业务帧：tool_use 首帧 + fragment（按 id 拼接 content）——
id: RUN2109…:5
data: {"type":"REASONING","message":{"id":"m2","role":"ASSISTANT","content":[{"type":"tool_use","id":"call_1","name":"shell","input":{},"content":"{\\"cmd\\":"}]},"isLast":false,"messageId":"m2"}

id: RUN2109…:6
data: {"type":"REASONING","message":{"id":"m2","role":"ASSISTANT","content":[{"type":"tool_use","id":"call_1","name":"__fragment__","content":"\\"ls\\"}"}]},"isLast":true,"messageId":"m2"}

# —— 业务帧：TOOL_RESULT ——
id: RUN2109…:7
data: {"type":"TOOL_RESULT","message":{"id":"m3","role":"TOOL","content":[{"type":"tool_result","id":"call_1","output":[{"type":"text","text":"a.txt\\nb.txt"}]}]},"isLast":true,"messageId":"m3"}

# —— 业务帧：AGENT_RESULT（取 usage；仍须等 run_finished）——
id: RUN2109…:8
data: {"type":"AGENT_RESULT","message":{"id":"m4","role":"ASSISTANT","content":[{"type":"text","text":"目录下有两个文件。"}],"usage":{"inputTokens":100,"outputTokens":20,"cachedTokens":0,"time":1.2,"totalTokens":120}},"isLast":true,"messageId":"m4"}

# —— 控制帧：run_finished（本轮订阅结束）——
id: RUN2109…:9
event: run_finished
data: {"runId":"RUN2109…","status":"DONE"}

:heartbeat

# —— HITL 示例（enableUserFormInput=true 且模型调用 request_user_input）——
# 挂起态 AGENT_RESULT（勿 abort）→ user_input.required → 客户端 POST respond → accepted → 续跑
id: RUN2109…:40
data: {"type":"AGENT_RESULT","isLast":true,"message":{"id":"mH","role":"ASSISTANT","metadata":{"agentscope_generate_reason":"TOOL_SUSPENDED"},"content":[{"type":"tool_use","id":"call_u1","name":"request_user_input","input":{"title":"补充信息","fields":[{"name":"city","label":"城市","type":"string","required":true}]}}]}}

id: RUN2109…:41
event: user_input.required
data: {"runId":"RUN2109…","sessionId":"SES-xxx","replyId":"mH","requestId":"UIR…","toolCallId":"call_u1","toolName":"request_user_input","form":{"title":"补充信息","fields":[{"name":"city","label":"城市","type":"string","required":true}]}}

# 客户端另开 HTTP：POST /api/v1/open/agents/command/user-input/respond
# {"sessionNum":"SES-xxx","runId":"RUN2109…","requestId":"UIR…","action":"submit","values":{"city":"深圳"}}

id: RUN2109…:42
event: user_input.accepted
data: {"runId":"RUN2109…","requestId":"UIR…","status":"submitted"}

# 随后继续 REASONING / AGENT_RESULT / run_finished …

# 对接要点：
# 1) 分发：有 event → 控制帧；无 event → data.type
# 2) 每帧保存 id；断线 resume + lastEventId，勿重发 input
# 3) 结束条件：run_finished（不是 AGENT_RESULT；HITL 挂起更不能关）
# 4) tool_use：同 id 拼接 content；name=__fragment__ 不覆盖工具名
# 5) user_input.required → POST …/user-input/respond（同连接等待 accepted 后续帧）
# 6) 非 SSE 错误：{"code":2017,"message":"缺少有效秘钥","data":null,"traceId":"..."}
# 7) 会话忙：{"code":4008,"message":"会话已有进行中的调用",...}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/user-input/respond',
      title: '用户表单 HITL 回传（submit / cancel）',
      description:
        '当 Invoke SSE 收到 event:user_input.required 后，用本接口提交或取消表单，唤醒同一次 run 继续推理（方案 A：原 SSE 连接不结束）。须携带 required 帧中的 requestId；建议带 sessionNum/runId 做一致性校验。action=submit 时 values 为表单键值；action=cancel 时 values 可空（Agent 将收到 status=cancelled）。前提：Agent 配置开启 enableUserFormInput。调试台等价路径：POST /api/v1/debug-console/user-input/respond（Cookie JWT + X-Workspace-Num，body 字段推荐 snake_case）。',
      requestContentType: 'application/json',
      responseContentType: 'application/json',
      headers: [...AUTH_HEADERS, JSON_CONTENT_HEADER],
      requestParams: [
        {
          name: 'requestId',
          location: 'body',
          type: 'string',
          required: true,
          description: '来自 user_input.required 的 requestId（形如 UIR…）；亦接受 JSON 键 request_id',
        },
        {
          name: 'action',
          location: 'body',
          type: 'string',
          required: true,
          description: 'submit | cancel（大小写不敏感）',
        },
        {
          name: 'values',
          location: 'body',
          type: 'object',
          required: false,
          description: 'submit 时的表单值，key 与 form.fields[].name 对齐；cancel 可省略',
        },
        {
          name: 'sessionNum',
          location: 'body',
          type: 'string',
          required: false,
          description:
            '会话编号；与 required 帧 sessionId 一致时校验。亦接受 session_num / session_id',
        },
        {
          name: 'runId',
          location: 'body',
          type: 'string',
          required: false,
          description: '本轮 runId；与 required 帧一致时校验。亦接受 run_id',
        },
        {
          name: 'replyId',
          location: 'body',
          type: 'string',
          required: false,
          description: '可选，来自 required 帧 replyId。亦接受 reply_id',
        },
        {
          name: 'toolCallId',
          location: 'body',
          type: 'string',
          required: false,
          description: '可选，来自 required 帧 toolCallId。亦接受 tool_call_id',
        },
      ],
      responseSummary:
        'application/json Result：code=0 表示已唤醒挂起；失败常见为 requestId 不存在/已处理、action 非法、runId/session 不一致（业务码多为参数类）。成功后原 Invoke SSE 会下发 user_input.accepted 并续跑。',
      responseGroups: [
        {
          title: 'Result',
          fields: RESULT_FIELDS,
        },
      ],
      curl: `curl -X POST '${base}/api/v1/open/agents/command/user-input/respond' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "sessionNum": "SES-xxx",
    "runId": "RUN2109…",
    "requestId": "UIR…",
    "replyId": "mH",
    "toolCallId": "call_u1",
    "action": "submit",
    "values": { "city": "深圳" }
  }'

# 取消示例
curl -X POST '${base}/api/v1/open/agents/command/user-input/respond' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "sessionNum": "SES-xxx",
    "requestId": "UIR…",
    "action": "cancel"
  }'`,
      responseExample: `{
  "code": 0,
  "message": "success",
  "data": null,
  "traceId": "…"
}

# 失败示例：表单已处理或不存在
{
  "code": 1001,
  "message": "表单请求不存在或已处理: UIR…",
  "data": null,
  "traceId": "…"
}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/a2ui/invoke',
      title: 'A2UI Invoke（v0.9.1 SSE）',
      description:
        '按 A2UI v0.9.1 协议流式返回 UI 消息。与上方 Invoke 并存，不替换原 Event SSE。可选 surfaceId / catalogId；sendDataModel 默认 true。流结束时若有 Token 用量，会额外下发 updateDataModel path=/tokenUsage。',
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
        '成功时为 SSE：每条 data 为一个 A2UI v0.9.1 envelope（version + 四选一消息体）。若 Agent 开启 enableUserFormInput 且中途 HITL：会额外下发 updateComponents（表单 Column/TextField/Button，按钮 action.name=submit_user_input|cancel_user_input）与 updateDataModel（含 hitlRequestId、values 等）；客户端填完后走 a2ui/action HITL 分支。',
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

data:{"version":"v0.9.1","updateDataModel":{"surfaceId":"main","path":"/assistantText","value":"好的，我来帮你…"}}

# HITL 表单（示意）：含 TextField + Button(submit_user_input/cancel_user_input) + dataModel.hitl*
data:{"version":"v0.9.1","type":"updateComponents","surfaceId":"main","components":[{"id":"root","component":"Column","children":["user_form_title","user_form_field_0","user_form_submit","user_form_cancel"]},{"id":"user_form_title","component":"Text","text":{"literalString":"补充信息"}},{"id":"user_form_field_0","component":"TextField","label":{"literalString":"城市"},"text":{"path":"/values/city"}},{"id":"user_form_submit","component":"Button","label":{"literalString":"提交并继续"},"action":{"name":"submit_user_input"}},{"id":"user_form_cancel","component":"Button","label":{"literalString":"取消"},"action":{"name":"cancel_user_input"}}]}

data:{"version":"v0.9.1","type":"updateDataModel","surfaceId":"main","path":"/","value":{"hitlRequestId":"UIR…","hitlRunId":"RUN…","hitlSessionId":"SES-xxx","values":{}}}

data:{"version":"v0.9.1","updateDataModel":{"surfaceId":"main","path":"/tokenUsage","value":{"inputTokens":100,"outputTokens":20,"cachedTokens":0,"time":1.2,"totalTokens":120}}}`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/a2ui/action',
      title: 'A2UI Action（客户端回传 → v0.9.1 SSE）',
      description:
        '客户端交互回传（协议 action）。普通 action：服务端转为 Agent 文本输入后继续 A2UI SSE。HITL 专用：action.name 为 submit_user_input / cancel_user_input 时，不新开一轮 Agent，只唤醒挂起的 request_user_input（等同 user-input/respond）；须从 a2ui/invoke 下发的 dataModel 带回 hitlRequestId（及可选 hitlRunId/hitlSessionId），表单值放在 clientDataModel.values（或根级 values）。建议携带 sessionNum。结束帧同样可能下发 /tokenUsage。',
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
          description: '会话编号；建议携带以延续对话 / HITL',
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
          description:
            '客户端 data model 快照。HITL：含 hitlRequestId / hitlReplyId / hitlToolCallId / hitlRunId / hitlSessionId 与 values（字段名→值）',
        },
        {
          name: 'context',
          location: 'body',
          type: 'object',
          required: false,
          description: '额外调用上下文；HITL 时也可把 hitl* 放在 action.context',
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
              description:
                '动作名。HITL：submit_user_input（提交）| cancel_user_input（取消）；其它名走普通文本续聊',
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
              description: '触发组件 ID（如表单提交按钮 user_form_submit）',
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
              description:
                '动作上下文（可为空对象）。HITL 可放 hitlRequestId 等元数据',
            },
          ],
        },
      ],
      responseSummary:
        '普通 action：与 a2ui/invoke 相同，SSE 下发 A2UI v0.9.1 envelope。HITL submit/cancel：短 SSE，含 bootstrap + updateDataModel path=/hitlStatus value=submitted|cancelled；原 a2ui/invoke 连接上的 Agent 会续跑并继续推 UI。',
      responseGroups: A2UI_RESPONSE_GROUPS,
      curl: `# —— 普通 action（续聊）——
curl -N -X POST '${base}/api/v1/open/agents/command/a2ui/action' \\
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
  }'

# —— HITL：提交用户表单（submit_user_input）——
curl -N -X POST '${base}/api/v1/open/agents/command/a2ui/action' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: text/event-stream' \\
  -d '{
    "agentNum": "${agentNum}",
    "sessionNum": "SES-xxx",
    "action": {
      "name": "submit_user_input",
      "surfaceId": "main",
      "sourceComponentId": "user_form_submit",
      "timestamp": "2026-10-17T14:00:00Z",
      "context": {}
    },
    "clientDataModel": {
      "hitlRequestId": "UIR…",
      "hitlReplyId": "mH",
      "hitlToolCallId": "call_u1",
      "hitlRunId": "RUN…",
      "hitlSessionId": "SES-xxx",
      "values": { "city": "深圳" }
    }
  }'`,
      responseExample: `# 普通 action 续聊
data:{"version":"v0.9.1","createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json","sendDataModel":true}}

data:{"version":"v0.9.1","updateDataModel":{"surfaceId":"main","path":"/assistantText","value":"已收到提交，正在处理…"}}

data:{"version":"v0.9.1","updateDataModel":{"surfaceId":"main","path":"/tokenUsage","value":{"inputTokens":80,"outputTokens":15,"cachedTokens":0,"time":0.9,"totalTokens":95}}}

# HITL ack（短流）
data:{"version":"v0.9.1","createSurface":{"surfaceId":"main",…}}

data:{"version":"v0.9.1","surfaceId":"main","type":"updateDataModel","path":"/hitlStatus","value":"submitted"}`,
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
        ；秘钥已隐含归属 Agent，请求中的 agentNum 必须与秘钥一致，否则返回 403（2019）。非流式接口统一为{' '}
        <Text code>{`{ code, message, data, traceId }`}</Text>
        。Invoke SSE 每帧带 <Text code>id=runId:seq</Text>
        ，支持 <Text code>Last-Event-ID</Text> / body.lastEventId 断线续传；字段命名为 camelCase。
      </Text>
      {specs.map((s) => (
        <ApiCard key={`${s.method} ${s.path}`} spec={s} onCopy={handleCopy} />
      ))}
    </div>
  );
}
