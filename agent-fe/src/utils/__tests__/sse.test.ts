import { afterEach, describe, expect, it, vi } from 'vitest';
import { invokeStream } from '../sse';

describe('invokeStream SSE heartbeat comments', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('ignores :heartbeat comments and only delivers data events', async () => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => {
        store.set(k, v);
      },
      removeItem: (k: string) => {
        store.delete(k);
      },
    });

    const payload =
      ':heartbeat\n\ndata: {"type":"agent_message","text":"hi"}\n\n:heartbeat\n\n';
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(payload));
        controller.close();
      },
    });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body: stream,
        headers: {
          get: (name: string) =>
            name.toLowerCase() === 'content-type'
              ? 'text/event-stream;charset=UTF-8'
              : null,
        },
      }),
    );

    const events: Array<{ event: string; data: unknown }> = [];
    let error: Error | undefined;
    await invokeStream({ url: '/api/v1/debug-console/invoke', body: {} }, {
      onEvent: (e) => events.push(e),
      onError: (e) => {
        error = e;
      },
    });

    expect(error).toBeUndefined();
    expect(events).toHaveLength(1);
    expect(events[0].data).toEqual({ type: 'agent_message', text: 'hi' });
  });

  it('surfaces business Result JSON when response is not event-stream', async () => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => {
        store.set(k, v);
      },
      removeItem: (k: string) => {
        store.delete(k);
      },
    });

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body: {},
        headers: {
          get: (name: string) =>
            name.toLowerCase() === 'content-type' ? 'application/json' : null,
        },
        json: async () => ({
          code: 1003,
          message: '无权限操作该会话',
        }),
      }),
    );

    let error: Error | undefined;
    await invokeStream({ url: '/api/v1/debug-console/invoke', body: {} }, {
      onEvent: () => undefined,
      onError: (e) => {
        error = e;
      },
    });

    expect(error?.message).toBe('无权限操作该会话');
  });
});
