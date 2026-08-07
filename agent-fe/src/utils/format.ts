import dayjs from 'dayjs';

export function formatTime(t?: string | number | null, pattern = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!t) return '-';
  const d = dayjs(t);
  return d.isValid() ? d.format(pattern) : '-';
}

export function relativeTime(t?: string | number | null): string {
  if (!t) return '-';
  const ms = Date.now() - dayjs(t).valueOf();
  if (ms < 60_000) return '刚刚';
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)} 分钟前`;
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)} 小时前`;
  return formatTime(t, 'YYYY-MM-DD HH:mm');
}

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export function safeJsonParse<T = unknown>(s: string): T | null {
  try {
    return JSON.parse(s) as T;
  } catch {
    return null;
  }
}

export function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/** 256KB 截断（与后端约束一致） */
export function truncate(text: string, max = 256 * 1024): string {
  if (text.length <= max) return text;
  return text.slice(0, max) + '\n... (truncated)';
}
