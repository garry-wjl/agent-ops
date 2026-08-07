/**
 * 调试台流式 invoke 客户端
 * - 浏览器原生 EventSource 不支持 POST + body，故走 fetch + ReadableStream 自解析
 * - 协议见调试台技术方案 §10 / 总体方案 §10.1
 */
import type { SsePlatformEvent } from '@/types';

export interface SseHandlers {
  onEvent: (event: SsePlatformEvent) => void;
  onDone?: () => void;
  onError?: (err: Error) => void;
}

export interface InvokeStreamOptions {
  url: string;
  body: Record<string, any>;
  signal?: AbortSignal;
  headers?: Record<string, string>;
}

/** 解析 SSE 块。一个块由两个换行结尾，内部 event:/data: 多行。 */
function parseSseBlock(block: string): SsePlatformEvent | null {
  const lines = block.split(/\r?\n/);
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    } else if (line.startsWith(':')) {
      // comment
    }
  }
  if (dataLines.length === 0) return null;
  const raw = dataLines.join('\n');
  let data: any;
  try {
    data = JSON.parse(raw);
  } catch {
    data = raw;
  }
  return { event, data } as SsePlatformEvent;
}

export async function invokeStream(
  options: InvokeStreamOptions,
  handlers: SseHandlers,
): Promise<void> {
  const { url, body, signal, headers = {} } = options;
  try {
    const resp = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'X-User-Id': 'mock-user',
        'X-User-Name': 'mock-user',
        ...headers,
      },
      credentials: 'include',
      body: JSON.stringify(body),
      signal,
    });
    if (!resp.ok || !resp.body) {
      throw new Error(`SSE HTTP ${resp.status}`);
    }
    const reader = resp.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx: number;
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const block = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        const parsed = parseSseBlock(block);
        if (parsed) handlers.onEvent(parsed);
      }
    }
    handlers.onDone?.();
  } catch (e) {
    if ((e as any)?.name === 'AbortError') {
      handlers.onDone?.();
      return;
    }
    handlers.onError?.(e as Error);
  }
}
