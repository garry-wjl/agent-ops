/**
 * 历史回放 / 时间线：把 user_form 与旧版 request_user_input tool_use 统一成可渲染的表单段。
 */
import type { AssistantSegment, PendingUserInput, UserInputFieldDef } from '@/types';
import { REQUEST_USER_INPUT_TOOL } from '@/hooks/userInputSse';

export type UserFormSegment = Extract<AssistantSegment, { kind: 'user_form' }>;

function asRecord(v: unknown): Record<string, unknown> | null {
  return v != null && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, unknown>)
    : null;
}

function normalizeFields(raw: unknown): UserInputFieldDef[] {
  if (!Array.isArray(raw)) return [];
  const out: UserInputFieldDef[] = [];
  for (const item of raw) {
    const row = asRecord(item);
    if (!row || typeof row.name !== 'string' || !row.name) continue;
    out.push({
      name: row.name,
      label: typeof row.label === 'string' ? row.label : undefined,
      type: (typeof row.type === 'string' ? row.type : 'string') as UserInputFieldDef['type'],
      required: row.required === true,
      description: typeof row.description === 'string' ? row.description : undefined,
      placeholder: typeof row.placeholder === 'string' ? row.placeholder : undefined,
      default: row.default,
      options: Array.isArray(row.options) ? (row.options as UserInputFieldDef['options']) : undefined,
    });
  }
  return out;
}

/** 把 API / 落库形态规整成 FE PendingUserInput */
export function coercePendingForm(
  form: unknown,
  fallbackRequestId: string,
  topLevelFields?: unknown,
): PendingUserInput | null {
  const rec = asRecord(form);
  const fields = normalizeFields(rec?.fields ?? topLevelFields);
  if (fields.length === 0 && !rec) return null;
  const requestId =
    (typeof rec?.requestId === 'string' && rec.requestId) ||
    (typeof rec?.request_id === 'string' && rec.request_id) ||
    fallbackRequestId;
  return {
    requestId,
    title: typeof rec?.title === 'string' ? rec.title : undefined,
    description: typeof rec?.description === 'string' ? rec.description : undefined,
    fields,
  };
}

function pendingFromToolUse(
  seg: Extract<AssistantSegment, { kind: 'tool_use' }>,
): UserFormSegment | null {
  let source: Record<string, unknown> | null = asRecord(seg.input);
  if (!source && seg.argsBuffer) {
    try {
      source = asRecord(JSON.parse(seg.argsBuffer));
    } catch {
      source = null;
    }
  }
  if (!source) return null;
  const pending = coercePendingForm(source, seg.toolCallId || `tool-${seg.toolName}`);
  if (!pending || pending.fields.length === 0) return null;

  let values: Record<string, unknown> | undefined;
  let status: UserFormSegment['status'] = 'submitted';
  if (typeof seg.output === 'string') {
    try {
      const parsed = JSON.parse(seg.output) as Record<string, unknown>;
      if (parsed.status === 'cancelled') status = 'cancelled';
      if (asRecord(parsed.values)) values = asRecord(parsed.values) ?? undefined;
    } catch {
      /* ignore */
    }
  } else if (asRecord(seg.output)) {
    const out = asRecord(seg.output)!;
    if (out.status === 'cancelled') status = 'cancelled';
    if (asRecord(out.values)) values = asRecord(out.values) ?? undefined;
  }

  return { kind: 'user_form', status, form: pending, values };
}

/**
 * 供时间线渲染：
 * - 正规 user_form 保留（并补齐 form）
 * - 旧历史里的 request_user_input tool_use 转成只读表单卡
 * - 若已有 user_form，则丢弃同轮的 request_user_input tool_use，避免再显示 JSON 工具卡
 */
export function normalizeSegmentsForDisplay(segments: AssistantSegment[]): AssistantSegment[] {
  const hasUserForm = segments.some((s) => s.kind === 'user_form');
  const out: AssistantSegment[] = [];

  for (const seg of segments) {
    if (seg.kind === 'user_form') {
      const pending = coercePendingForm(
        seg.form,
        seg.form?.requestId ?? `form-${out.length}`,
        (seg as { fields?: unknown }).fields,
      );
      if (!pending) continue;
      out.push({
        kind: 'user_form',
        status: seg.status === 'cancelled' ? 'cancelled' : 'submitted',
        form: pending,
        values: seg.values,
      });
      continue;
    }
    if (seg.kind === 'tool_use' && seg.toolName === REQUEST_USER_INPUT_TOOL) {
      if (hasUserForm) continue;
      const formSeg = pendingFromToolUse(seg);
      if (formSeg) out.push(formSeg);
      continue;
    }
    out.push(seg);
  }
  return out;
}
