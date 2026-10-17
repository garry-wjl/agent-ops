/**
 * 调试台用户表单 HITL — 对齐 request_user_input / debug-console user-input respond。
 */

export type UserInputFieldType =
  | 'string'
  | 'textarea'
  | 'number'
  | 'boolean'
  | 'date'
  | 'enum'
  | 'multi_enum';

export interface UserInputFieldOption {
  value: string;
  label?: string;
}

export interface UserInputFieldDef {
  name: string;
  label?: string;
  type: UserInputFieldType;
  required?: boolean;
  description?: string;
  placeholder?: string;
  default?: unknown;
  /** enum / multi_enum 选项 */
  options?: UserInputFieldOption[] | string[];
}

/** SSE user_input.required 解析后的待填表单 */
export interface PendingUserInput {
  requestId: string;
  replyId?: string;
  toolCallId?: string;
  runId?: string;
  sessionId?: string;
  title?: string;
  description?: string;
  fields: UserInputFieldDef[];
}

export type UserInputRespondAction = 'submit' | 'cancel';

/** POST /api/v1/debug-console/user-input/respond */
export interface UserInputRespondParam {
  session_num: string;
  run_id?: string;
  request_id: string;
  reply_id?: string;
  tool_call_id?: string;
  action: UserInputRespondAction;
  values?: Record<string, unknown>;
}
