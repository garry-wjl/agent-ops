import type {
  PendingUserInput,
  UserInputFieldDef,
  UserInputFieldOption,
  UserInputFieldType,
} from '@/types';

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

/**
 * AGENT_RESULT 是否为表单 HITL 挂起（TOOL_SUSPENDED + request_user_input）。
 * 此类终态帧后还会紧跟 user_input.required，前端不得 abort SSE。
 */
export function isUserFormSuspendedAgentResult(evt: unknown): boolean {
  if (!isRecord(evt) || evt.type !== 'AGENT_RESULT') return false;
  const message = evt.message;
  if (!isRecord(message)) return false;

  const meta = isRecord(message.metadata) ? message.metadata : undefined;
  const reason =
    pickString(meta ?? {}, 'agentscope_generate_reason', 'generateReason') ??
    pickString(message, 'generateReason', 'generate_reason');
  if (reason === 'TOOL_SUSPENDED') {
    const content = message.content;
    if (Array.isArray(content)) {
      for (const block of content) {
        if (
          isRecord(block) &&
          block.type === 'tool_use' &&
          block.name === 'request_user_input'
        ) {
          return true;
        }
      }
    }
    // 无 content 时仍信任 generate_reason（兼容字段裁剪）
    return true;
  }

  const content = message.content;
  if (!Array.isArray(content)) return false;
  for (const block of content) {
    if (!isRecord(block) || block.type !== 'tool_use') continue;
    if (block.name !== 'request_user_input') continue;
    const blockMeta = isRecord(block.metadata) ? block.metadata : undefined;
    if (blockMeta?.agentscope_suspended === true || block.state === 'running') {
      return true;
    }
  }
  return false;
}
