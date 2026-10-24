import type {
  AssistantSegment,
  PendingUserInput,
  UserInputFieldDef,
  UserInputFieldOption,
  UserInputFieldType,
} from '@/types';

/** 平台内置表单 HITL 工具名（与 BE RequestUserInputTool.NAME 对齐） */
export const REQUEST_USER_INPUT_TOOL = 'request_user_input';

const FIELD_TYPES: UserInputFieldType[] = [
  'string',
  'textarea',
  'number',
  'boolean',
  'date',
  'enum',
  'multi_enum',
];

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function pickString(obj: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const raw = obj[key];
    if (typeof raw === 'string' && raw.trim()) return raw.trim();
  }
  return undefined;
}

function normalizeOptions(raw: unknown): UserInputFieldOption[] | undefined {
  if (!Array.isArray(raw) || raw.length === 0) return undefined;
  const out: UserInputFieldOption[] = [];
  for (const item of raw) {
    if (typeof item === 'string') {
      out.push({ value: item, label: item });
      continue;
    }
    if (isRecord(item)) {
      const value =
        pickString(item, 'value', 'id', 'key') ??
        (typeof item.value === 'number' ? String(item.value) : undefined);
      if (!value) continue;
      const label = pickString(item, 'label', 'name', 'title') ?? value;
      out.push({ value, label });
    }
  }
  return out.length ? out : undefined;
}

function normalizeFieldType(raw: unknown): UserInputFieldType {
  const s = typeof raw === 'string' ? raw.trim().toLowerCase() : '';
  if ((FIELD_TYPES as string[]).includes(s)) return s as UserInputFieldType;
  return 'string';
}

function normalizeField(raw: unknown): UserInputFieldDef | null {
  if (!isRecord(raw)) return null;
  const name = pickString(raw, 'name', 'key', 'field');
  if (!name) return null;
  return {
    name,
    label: pickString(raw, 'label', 'title'),
    type: normalizeFieldType(raw.type),
    required: raw.required === true,
    description: pickString(raw, 'description', 'hint', 'help'),
    placeholder: pickString(raw, 'placeholder'),
    default: raw.default ?? raw.defaultValue,
    options: normalizeOptions(raw.options ?? raw.enum ?? raw.choices),
  };
}

function extractFieldsRoot(data: Record<string, unknown>): unknown[] | undefined {
  if (Array.isArray(data.fields)) return data.fields;
  const form = data.form;
  if (isRecord(form) && Array.isArray(form.fields)) return form.fields;
  const schema = data.schema;
  if (isRecord(schema) && Array.isArray(schema.fields)) return schema.fields;
  return undefined;
}

/**
 * 将 SSE user_input.required 的 data 解析为 PendingUserInput；无法识别时返回 null。
 */
export function parseUserInputRequiredPayload(data: unknown): PendingUserInput | null {
  if (!isRecord(data)) return null;

  const requestId =
    pickString(data, 'requestId', 'request_id', 'id') ??
    pickString(data, 'requestID', 'request_ID');
  if (!requestId) return null;

  const form = isRecord(data.form) ? data.form : undefined;
  const rawFields = extractFieldsRoot(data);
  const fields = (rawFields ?? [])
    .map(normalizeField)
    .filter((f): f is UserInputFieldDef => f != null);
  if (fields.length === 0) return null;

  return {
    requestId,
    replyId: pickString(data, 'replyId', 'reply_id'),
    toolCallId: pickString(data, 'toolCallId', 'tool_call_id'),
    runId: pickString(data, 'runId', 'run_id'),
    sessionId: pickString(data, 'sessionId', 'session_id', 'sessionNum', 'session_num'),
    title:
      pickString(data, 'title', 'formTitle', 'form_title') ??
      (form ? pickString(form, 'title', 'formTitle', 'form_title') : undefined),
    description:
      pickString(data, 'description', 'message', 'prompt') ??
      (form ? pickString(form, 'description', 'message', 'prompt') : undefined),
    fields,
  };
}

export function userInputRejectedMessage(data: unknown): string | undefined {
  if (!isRecord(data)) return undefined;
  return pickString(data, 'message', 'reason', 'error');
}

function contentBlocks(message: Record<string, unknown>): Record<string, unknown>[] {
  const content = message.content;
  if (!Array.isArray(content)) return [];
  return content.filter(isRecord);
}

/**
 * AGENT_RESULT 是否表示运行中挂起（AgentScope external / SchemaOnly 工具待回传）。
 * <p>
 * 此类帧后通常还有控制帧（如 {@code user_input.required}），客户端<strong>不得</strong>据此 abort SSE；
 * 真正结束仍以 {@code run_finished}（或非挂起态的最终 AGENT_RESULT）为准。
 * <p>
 * 判定（任一命中即可，兼容字段裁剪）：
 * <ol>
 *   <li>{@code message.metadata.agentscope_generate_reason === "TOOL_SUSPENDED"}</li>
 *   <li>content 含 {@code tool_result.metadata.agentscope_suspended === true}
 *       （AgentScope 挂起帧常把该标记放在与 tool_use 同帧的 tool_result 上）</li>
 *   <li>content 含 {@code tool_use.name === "request_user_input"}
 *       （平台表单 HITL；tool_use.state 多为 {@code pending}，不是 {@code running}）</li>
 * </ol>
 */
export function isSuspendedAgentResult(evt: unknown): boolean {
  if (!isRecord(evt)) return false;

  // streamEvents：RequireExternalExecutionEvent
  if (evt.type === 'REQUIRE_EXTERNAL_EXECUTION') {
    const calls = evt.toolCalls;
    if (Array.isArray(calls)) {
      for (const c of calls) {
        if (isRecord(c) && c.name === REQUEST_USER_INPUT_TOOL) return true;
      }
    }
    return false;
  }

  if (evt.type !== 'AGENT_RESULT') return false;
  // v1 Event.message；v2 AgentResultEvent.result
  const message = isRecord(evt.message) ? evt.message : isRecord(evt.result) ? evt.result : null;
  if (!message) return false;

  const meta = isRecord(message.metadata) ? message.metadata : undefined;
  const reason =
    pickString(meta ?? {}, 'agentscope_generate_reason', 'generateReason') ??
    pickString(message, 'generateReason', 'generate_reason');
  if (reason === 'TOOL_SUSPENDED') {
    return true;
  }

  const blocks = contentBlocks(message);
  for (const block of blocks) {
    if (block.type === 'tool_result') {
      const blockMeta = isRecord(block.metadata) ? block.metadata : undefined;
      if (blockMeta?.agentscope_suspended === true) {
        return true;
      }
    }
    if (block.type === 'tool_use' && block.name === REQUEST_USER_INPUT_TOOL) {
      return true;
    }
  }
  return false;
}

/**
 * @deprecated 使用 {@link isSuspendedAgentResult}；保留别名以免外部引用断裂。
 */
export function isUserFormSuspendedAgentResult(evt: unknown): boolean {
  return isSuspendedAgentResult(evt);
}

export type UserFormSegment = Extract<AssistantSegment, { kind: 'user_form' }>;

/**
 * 把已结算表单插到挂起时刻的段位置。续跑内容若已追加在末尾，表单仍留在锚点处。
 * 同一 requestId 已存在则不重复插入。
 */
export function insertUserFormSegment(
  segs: AssistantSegment[],
  anchor: number,
  segment: UserFormSegment,
): AssistantSegment[] {
  if (
    segs.some(
      (s) => s.kind === 'user_form' && s.form.requestId === segment.form.requestId,
    )
  ) {
    return segs;
  }
  const at = Math.max(0, Math.min(anchor, segs.length));
  return [...segs.slice(0, at), segment, ...segs.slice(at)];
}
