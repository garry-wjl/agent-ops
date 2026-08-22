/**
 * 评测集 / 评测任务创建页：生成不易重复的默认名称。
 */

function pad(n: number, width = 2): string {
  return String(n).padStart(width, '0');
}

/** yyyyMMdd-HHmmss */
export function formatEvalNameStamp(now = new Date()): string {
  return (
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  );
}

/**
 * 生成唯一展示名：`{prefix} {stamp}`，若冲突则追加 -2、-3…
 * @param prefix 如「评测集」「评测任务」或「客服助手 评测集」
 * @param existing 同空间已有名称
 * @param maxLen 与表单 maxLength 对齐
 */
export function buildUniqueEvalName(
  prefix: string,
  existing: Iterable<string> = [],
  options?: { now?: Date; maxLen?: number },
): string {
  const now = options?.now ?? new Date();
  const maxLen = options?.maxLen ?? 64;
  const taken = new Set(
    [...existing].map((s) => s.trim()).filter(Boolean),
  );
  const stamp = formatEvalNameStamp(now);
  const basePrefix = prefix.trim() || '未命名';

  const fit = (raw: string) =>
    raw.length <= maxLen ? raw : raw.slice(0, maxLen);

  let candidate = fit(`${basePrefix} ${stamp}`);
  if (!taken.has(candidate)) return candidate;

  for (let i = 2; i < 1000; i++) {
    const suffix = `-${i}`;
    const headMax = maxLen - suffix.length;
    const head = `${basePrefix} ${stamp}`.slice(0, Math.max(1, headMax));
    candidate = `${head}${suffix}`;
    if (!taken.has(candidate)) return candidate;
  }
  return fit(`${basePrefix} ${stamp}-${Date.now()}`);
}

/** 是否仍为系统自动填充的「评测集 …」类名称（用户未改成业务名） */
export function isAutoDatasetName(name?: string): boolean {
  if (!name?.trim()) return true;
  return /评测集\s+\d{8}-\d{6}/.test(name) || /^评测集\s+\d{8}-\d{6}/.test(name);
}

/** 是否仍为系统自动填充的「评测任务 …」类名称 */
export function isAutoTaskName(name?: string): boolean {
  if (!name?.trim()) return true;
  return /评测任务\s+\d{8}-\d{6}/.test(name) || /·\s*\d{8}-\d{6}/.test(name);
}
