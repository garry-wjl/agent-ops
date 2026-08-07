/**
 * mock 公共工具
 * - ok / fail：包装为后端 Result<T>
 * - paginate：根据 query.pageNo/pageSize 切片
 */

export const ok = <T>(data: T) => ({
  code: 0,
  message: 'ok',
  traceId: 'mock-' + Date.now().toString(36),
  data,
});

export const fail = (code: number, message: string) => ({
  code,
  message,
  traceId: 'mock-' + Date.now().toString(36),
  data: null,
});

export function paginate<T>(
  list: T[],
  pageNo: number = 1,
  pageSize: number = 20,
) {
  const start = (pageNo - 1) * pageSize;
  return {
    total: list.length,
    list: list.slice(start, start + pageSize),
    pageNo,
    pageSize,
  };
}

/** 半小时前的 ISO 字符串 — 用于打散 mock 时间戳 */
export function isoMinutesAgo(min: number) {
  return new Date(Date.now() - min * 60_000).toISOString();
}
