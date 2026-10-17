import { describe, expect, it } from 'vitest';
import {
  isUserFormSuspendedAgentResult,
  parseUserInputRequiredPayload,
  userInputRejectedMessage,
} from '../userInputSse';

describe('parseUserInputRequiredPayload', () => {
  it('parses camelCase payload with fields', () => {
    const pending = parseUserInputRequiredPayload({
      requestId: 'req-1',
      replyId: 'reply-1',
      toolCallId: 'tool-1',
      title: '补充信息',
      fields: [
        { name: 'email', type: 'string', required: true, label: '邮箱' },
        { name: 'tags', type: 'multi_enum', options: ['a', 'b'] },
      ],
    });
    expect(pending).toMatchObject({
      requestId: 'req-1',
      replyId: 'reply-1',
      toolCallId: 'tool-1',
      title: '补充信息',
    });
    expect(pending?.fields).toHaveLength(2);
    expect(pending?.fields[1].type).toBe('multi_enum');
    expect(pending?.fields[1].options).toEqual([
      { value: 'a', label: 'a' },
      { value: 'b', label: 'b' },
    ]);
  });

  it('parses snake_case nested form.fields and form.title', () => {
    const pending = parseUserInputRequiredPayload({
      request_id: 'req-2',
      run_id: 'run-9',
      session_id: 'sess-9',
      form: {
        title: '请补充',
        description: 'desc',
        fields: [{ name: 'count', type: 'number' }],
      },
    });
    expect(pending?.requestId).toBe('req-2');
    expect(pending?.runId).toBe('run-9');
    expect(pending?.sessionId).toBe('sess-9');
    expect(pending?.title).toBe('请补充');
    expect(pending?.description).toBe('desc');
    expect(pending?.fields[0].type).toBe('number');
  });

  it('returns null without request id or fields', () => {
    expect(parseUserInputRequiredPayload(null)).toBeNull();
    expect(parseUserInputRequiredPayload({ requestId: 'x' })).toBeNull();
    expect(parseUserInputRequiredPayload({ request_id: 'x', fields: [] })).toBeNull();
  });
});

describe('userInputRejectedMessage', () => {
  it('extracts message from payload', () => {
    expect(userInputRejectedMessage({ message: '已超时' })).toBe('已超时');
    expect(userInputRejectedMessage({ reason: '用户取消' })).toBe('用户取消');
  });
});

describe('isUserFormSuspendedAgentResult', () => {
  it('detects TOOL_SUSPENDED metadata with request_user_input', () => {
    expect(
      isUserFormSuspendedAgentResult({
        type: 'AGENT_RESULT',
        isLast: true,
        message: {
          metadata: { agentscope_generate_reason: 'TOOL_SUSPENDED' },
          content: [
            {
              type: 'tool_use',
              name: 'request_user_input',
              id: 'c1',
              metadata: { agentscope_suspended: true },
              state: 'running',
            },
          ],
        },
      }),
    ).toBe(true);
  });

  it('ignores normal AGENT_RESULT', () => {
    expect(
      isUserFormSuspendedAgentResult({
        type: 'AGENT_RESULT',
        isLast: true,
        message: { content: [{ type: 'text', text: 'done' }] },
      }),
    ).toBe(false);
  });
});
