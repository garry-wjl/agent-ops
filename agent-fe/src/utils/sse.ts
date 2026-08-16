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
    // 身份与 REST 一致：依赖 cookie JWT；本地 disable-auth 时由后端回落默认用户。
    // 切勿硬编码 X-User-Id（曾用 mock-user 导致「先建会话、再 invoke」归属校验 500）。
    const workspaceNum =
      typeof localStorage !== 'undefined'
        ? localStorage.getItem('currentWorkspaceNum')
        : null;
    const resp = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(workspaceNum ? { 'X-Workspace-Num': workspaceNum } : {}),
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
    // 连接结束时刷掉尾部未以空行收尾的最后一帧（半关闭场景常见）
    const trailing = buffer.trim();
    if (trailing) {
      const parsed = parseSseBlock(trailing);
      if (parsed) handlers.onEvent(parsed);
    }
    handlers.onDone?.();
  } catch (e) {
    if ((e as any)?.name === 'AbortError') {
      handlers.onDone?.();
      return;
    }
    // 对端半关闭 chunked（curl 18 / undici terminated）时，视为流结束而非业务失败
    const msg = String((e as Error)?.message ?? e);
    if (/terminated|network|Failed to fetch|other side closed/i.test(msg)) {
      handlers.onDone?.();
      return;
    }
    handlers.onError?.(e as Error);
  }
}
