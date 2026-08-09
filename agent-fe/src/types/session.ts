/**
 * 调试台 / 会话领域类型 — 对齐 rd-agent-be SessionController 实际响应（v2.4）
 */

export type MessageRole = 'USER' | 'ASSISTANT' | 'TOOL';
export type InputType = 'TEXT' | 'JSON';
export type InvocationStatus = 'SUCCESS' | 'FAILED' | 'TRUNCATED';

export interface StepNodeVO {
  stepId: string;
  skillName: string;
  input?: unknown;
  output?: unknown;
  status: 'success' | 'pending' | 'error';
  latencyMs?: number;
  error?: string | null;
  startedAt?: string;
  truncated?: boolean;
}

export interface StepChainVO {
  steps: StepNodeVO[];
}

/**
 * 单条消息。
 * - {@link content} 在 BE 是 {@code Object}：TEXT 模式为 string，JSON 模式为 object；
 *   前端展示侧需做兼容（统一 stringify 后 render）。
 * - BE 不返回 sessionNum（messages 已按 sessionNum 拉取，无需冗余）。
 */
export interface MessageVO {
  num: string;
  role: MessageRole;
  inputType?: InputType | null;
  content: string | Record<string, any>;
  stepChain?: StepChainVO | null;
  /**
   * 助手消息按到达顺序的段列表（thinking / text / tool_use）。
   * BE v3.x 新协议:历史消息也带 segments,FE 与本轮流式共用 AssistantSegmentList 渲染路径。
   * 旧消息或非 assistant 消息为 null/undefined,FE 自动降级到 content + stepChain。
   */
  segments?: AssistantSegment[] | null;
  traceId?: string;
  createTime: string;
}

/** 会话创建/重命名等接口的完整 VO */
export interface SessionVO {
  num: string;
  agentNum: string;
  agentVersionNum: string;
  skillHint?: string | null;
  title?: string;
  lastMessageAt?: string | null;
  createTime: string;
  origin?: string;
  /** 会话默认调用上下文 */
  invokeContext?: Record<string, string | number | boolean> | null;
}

/** 列表项 */
export interface SessionListVO {
  num: string;
  agentNum: string;
  agentVersionNum: string;
  title?: string;
  lastMessageAt?: string | null;
  origin?: string;
  createTime?: string;
}

/** 详情：含会话元信息 + 完整消息列表 */
export interface SessionDetailVO {
  num: string;
  agentNum: string;
  agentVersionNum: string;
  skillHint?: string | null;
  title?: string;
  createTime: string;
  origin?: string;
  /** 会话默认调用上下文 */
  invokeContext?: Record<string, string | number | boolean> | null;
  messages: MessageVO[];
}

/**
 * SSE invoke 请求 — 匹配后端 DebugInvokeRequest 的 @JsonProperty 字段命名（snake_case）。
 * agentNum 后端无 @JsonProperty，保持 camelCase；input_type 后端 @NotBlank 必填。
 */
export interface DebugInvokeRequest {
  agentNum: string;
  /** 复用会话 num；空则后端按 traceId 新建会话 */
  session_num?: string;
  input: string | Record<string, any>;
  /** 输入类型；目前仅 text 实际生效，json 已在请求体支持但 BE 透传时会 String.valueOf */
  input_type: 'text' | 'json';
  skill_hint?: string;
  /**
   * 2026-07-28 版本化调试：目标 Agent 版本。
   * 空 = 当前在线版本；'DRAFT' = 草稿态版本；'vX.Y.Z' = 指定历史 / 发布版本。
   */
  target_version?: string;
  /** 调用上下文：用于系统提示词 {{key}} 替换并合并进会话默认上下文 */
  context?: Record<string, string | number | boolean>;
}

/* ============================================================
 * AgentScope Event 协议（BE 直接返回 Flux<io.agentscope.core.agent.Event>）
 * SSE 帧不带 event: 字段，每条 data 是裸 Event JSON：
 *   data: {"type":"REASONING","message":{...},"isLast":false,"source":null}
 * ============================================================ */

/** AgentScope EventType 枚举 — 见 io.agentscope.core.agent.EventType */
export type AgentEventType =
  | 'REASONING'      // LLM 思考/文本/工具调用请求（流式，可多 chunk）
  | 'TOOL_RESULT'    // 工具执行结果（流式工具可多 chunk）
  | 'HINT'           // RAG/Memory/Plan 注入的提示信息（单条）
  | 'AGENT_RESULT'   // 最终响应（默认不在流里，避免与 REASONING 重复）
  | 'SUMMARY';       // 达到 maxIters 的兜底总结

/**
 * Msg 内的 ContentBlock 联合类型 — 字段名严格对齐 AgentScope Java 1.0.x 的 JSON 序列化形态。
 * 关键约定（来自 io.agentscope.core.message.* 源码）：
 * - ThinkingBlock 的字段叫 thinking 不是 text；
 * - ToolResultBlock.id 同时是 tool_use 的 id（用于关联）；
 * - ToolResultBlock.output 是 ContentBlock[] 不是字符串。
 *
 * tool_use 的 fragment 协议：
 * - 首帧：name 是真实工具名，input 是结构化对象（也可能空 {}），content 是参数 JSON 字符串的第一段
 * - 后续帧：name === '__fragment__'，content 是参数 JSON 字符串的后续增量
 * - 同一次工具调用的所有帧共享同一个 id；FE 按 id 拼接 content 得到完整参数 JSON
 */
export type AgentContentBlock =
  | { type: 'text'; text: string }
  | { type: 'thinking'; thinking: string; metadata?: Record<string, unknown> }
  | {
      type: 'tool_use';
      id: string;
      name: string;
      input?: Record<string, unknown>;
      /** fragment 协议下的参数 JSON 增量；按 id 拼接 */
      content?: string;
      metadata?: Record<string, unknown>;
    }
  | { type: 'tool_result'; id: string; name?: string; output?: AgentContentBlock[]; metadata?: Record<string, unknown> }
  | { type: 'image' | 'audio' | 'video'; [k: string]: unknown };

/** AgentScope Msg（FE 关心字段） */
export interface AgentMsg {
  /** 同一逻辑消息的所有 chunk + 最终 PostReasoning 快照共享同一 id，用于跨帧去重 */
  id?: string;
  role: 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM';
  content: AgentContentBlock[];
  name?: string;
}

/** BE 在 SSE 流里发出的单条事件 — 与 Java Event 类一一对应 */
export interface AgentScopeEvent {
  type: AgentEventType;
  message: AgentMsg;
  /** 同一 EventType 的多 chunk 序列里，true 标记最后一片 */
  isLast: boolean;
  /** 嵌套子 Agent 来源；顶层 Agent 发的事件此处为 null */
  source?: { agentName?: string } | null;
}

/**
 * FE 内部解析后的统一壳子。
 * BE 当前不设置 SSE 的 event: 字段，故全部落入 'message' 通道携带 AgentScopeEvent；
 * 'error' 通道仅在 FE 自己 onError 兜底时构造，不来自 BE 协议。
 */
export type SsePlatformEvent =
  | { event: 'message'; data: AgentScopeEvent }
  | { event: 'error'; data: { code?: number; message: string; trace_id?: string } };

/* ============================================================
 * AssistantSegment — 按到达顺序的实时流分段（替代"类型分桶"）
 * 见 .planning/plans/streamed-hopping-tiger.md
 *
 * 设计要点：
 * - 同类相邻段合并（连续 thinking 帧拼成 1 个 thinking segment）
 * - 类型切换开新段（thinking → text 之间不合并）
 * - tool_use 按 toolCallId 匹配，fragment 帧追加 argsBuffer
 *
 * 持久化无对应字段；只在实时流期间存在，刷新后历史消息走 MessageVO 降级渲染
 * ============================================================ */
export type AssistantSegment =
  | { kind: 'thinking'; text: string }
  | { kind: 'text'; text: string }
  | {
      kind: 'tool_use';
      toolCallId: string;
      /** 首帧的真实工具名；fragment 帧（name === '__fragment__'）不覆盖 */
      toolName: string;
      /** fragment.content 增量拼接的参数 JSON 字符串，可能未拼完（尾部不闭合） */
      argsBuffer: string;
      /** 首帧给的结构化 input（如有）；只有真名首帧能填，后续不覆盖 */
      input?: Record<string, unknown>;
      status: 'pending' | 'success' | 'error';
      output?: unknown;
      latencyMs?: number;
      startedAt?: string;
      error?: string;
    };
