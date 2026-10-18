/**
 * HITL SSE 保活集成：模拟真实挂起帧形态，验证「勿因挂起 AGENT_RESULT abort」。
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { invokeStream } from '@/utils/sse';
import {
  isSuspendedAgentResult,
  parseUserInputRequiredPayload,
} from '../userInputSse';

function sseBlock(opts: {
  id?: string;
  event?: string;
  data: unknown;
}): string {
  const lines: string[] = [];
  if (opts.id) lines.push(`id: ${opts.id}`);
  if (opts.event) lines.push(`event: ${opts.event}`);
  lines.push(`data: ${JSON.stringify(opts.data)}`);
  return `${lines.join('\n')}\n\n`;
}

/** 对齐 AgentScope buildSuspendedMsg 线上 JSON */
const SUSPEND_AGENT_RESULT = {
  type: 'AGENT_RESULT',
  isLast: true,
  message: {
    id: 'mH',
    role: 'ASSISTANT',
    metadata: { agentscope_generate_reason: 'TOOL_SUSPENDED' },
    content: [
      {
        type: 'tool_use',
        id: 'call_u1',
        name: 'request_user_input',
        input: {
          title: '补充信息',
          fields: [{ name: 'city', label: '城市', type: 'string', required: true }],
        },
        state: 'pending',
        metadata: {},
      },
      {
        type: 'tool_result',
        id: 'call_u1',
        name: 'request_user_input',
        output: [{ type: 'text', text: '[Awaiting external execution]' }],
        metadata: { agentscope_suspended: true },
        state: 'running',
      },
    ],
  },
};

const SUSPEND_WITHOUT_REASON = {
  type: 'AGENT_RESULT',
  isLast: true,
  message: {
    id: 'mH2',
    role: 'ASSISTANT',
    content: SUSPEND_AGENT_RESULT.message.content,
  },
};

describe('HITL SSE keep-alive integration', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubFetchWithController(signalCapture: { current?: AbortSignal }) {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => store.set(k, v),
      removeItem: (k: string) => store.delete(k),
    });

    let streamCtrl: ReadableStreamDefaultController<Uint8Array> | null = null;
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        streamCtrl = controller;
      },
    });

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
        signalCapture.current = init?.signal;
        return Promise.resolve({
          ok: true,
          body: stream,
          headers: {
            get: (name: string) =>
              name.toLowerCase() === 'content-type'
                ? 'text/event-stream;charset=UTF-8'
                : null,
          },
        });
      }),
    );

    const enc = new TextEncoder();
    return {
      push(text: string) {
        streamCtrl?.enqueue(enc.encode(text));
      },
      close() {
        try {
          streamCtrl?.close();
        } catch {
          /* already closed */
        }
      },
    };
  }

  it('does not finishStream after real TOOL_SUSPENDED AGENT_RESULT + user_input.required', async () => {
    const signalCapture: { current?: AbortSignal } = {};
    const sse = stubFetchWithController(signalCapture);

    let awaitingExternal = false;
    let finished = false;
    let pendingRequestId: string | undefined;
    const events: string[] = [];

    const ctrl = new AbortController();
    const donePromise = invokeStream(
      { url: '/api/v1/debug-console/invoke', body: {}, signal: ctrl.signal },
      {
        onEvent: (parsed) => {
          events.push(parsed.event ?? 'message');
          if (parsed.event === 'user_input.required') {
            const p = parseUserInputRequiredPayload(parsed.data);
            expect(p?.requestId).toBe('UIR1');
            awaitingExternal = true;
            pendingRequestId = p?.requestId;
            return;
          }
          const evt = parsed.data as { type?: string };
          if (evt?.type === 'AGENT_RESULT') {
            if (!isSuspendedAgentResult(evt) && !awaitingExternal) {
              finished = true;
              ctrl.abort();
            }
          }
        },
      },
    );

    // 等 fetch 订阅上
    await new Promise((r) => setTimeout(r, 20));
    sse.push(
      sseBlock({
        id: 'RUN1:1',
        event: 'run_started',
        data: { runId: 'RUN1', sessionNum: 'SES1' },
      }),
    );
    sse.push(sseBlock({ id: 'RUN1:2', data: SUSPEND_AGENT_RESULT }));
    sse.push(
      sseBlock({
        id: 'RUN1:3',
        event: 'user_input.required',
        data: {
          runId: 'RUN1',
          sessionId: 'SES1',
          replyId: 'mH',
          requestId: 'UIR1',
          toolCallId: 'call_u1',
          toolName: 'request_user_input',
          form: {
            title: '补充信息',
            fields: [{ name: 'city', label: '城市', type: 'string', required: true }],
          },
        },
      }),
    );

    for (let i = 0; i < 40 && !pendingRequestId; i++) {
      await new Promise((r) => setTimeout(r, 10));
    }
    expect(pendingRequestId).toBe('UIR1');
    expect(finished).toBe(false);
    expect(signalCapture.current?.aborted).toBe(false);
    expect(events).toEqual(
      expect.arrayContaining(['run_started', 'message', 'user_input.required']),
    );

    // 关闭流结束 invokeStream，模拟服务端仍在挂起等待（客户端未主动 finish）
    sse.close();
    await donePromise;
    expect(finished).toBe(false);
  });

  it('still recognizes suspend when generate_reason metadata is stripped', () => {
    expect(isSuspendedAgentResult(SUSPEND_WITHOUT_REASON)).toBe(true);
  });

  it('finishes on normal final AGENT_RESULT (non-HITL)', async () => {
    const signalCapture: { current?: AbortSignal } = {};
    const sse = stubFetchWithController(signalCapture);

    let finished = false;
    const ctrl = new AbortController();
    const donePromise = invokeStream(
      { url: '/api/v1/debug-console/invoke', body: {}, signal: ctrl.signal },
      {
        onEvent: (parsed) => {
          const evt = parsed.data as { type?: string };
          if (evt?.type === 'AGENT_RESULT') {
            if (!isSuspendedAgentResult(evt)) {
              finished = true;
              ctrl.abort();
            }
          }
        },
      },
    );

    await new Promise((r) => setTimeout(r, 20));
    sse.push(
      sseBlock({
        id: 'RUN2:1',
        event: 'run_started',
        data: { runId: 'RUN2', sessionNum: 'SES2' },
      }),
    );
    sse.push(
      sseBlock({
        id: 'RUN2:2',
        data: {
          type: 'AGENT_RESULT',
          isLast: true,
          message: {
            content: [{ type: 'text', text: 'done' }],
            usage: { inputTokens: 1, outputTokens: 1, totalTokens: 2 },
          },
        },
      }),
    );
    sse.close();
    await donePromise;
    expect(finished).toBe(true);
  });
});
