/** 评测模块共用扁平化 token（对齐 Prompt / Tool 列表） */
export const COLOR = {
  border: '#E2E8F0',
  headerBg: '#ffffff',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

export const EVAL_BASE = '/agent/evaluation';

/** 评测集状态：接口枚举 → 中文 */
export const DATASET_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
};

/** 评测集类型：接口枚举 → 中文 */
export const DATASET_TYPE_LABEL: Record<string, string> = {
  AGENT: '关联 Agent',
  CUSTOM: '自定义',
};

/** 评测任务状态：接口枚举 → 中文 */
export const TASK_STATUS_LABEL: Record<string, string> = {
  PENDING: '待运行',
  RUNNING: '运行中',
  FINISHED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
};

/** 任务行执行状态 */
export const TASK_ITEM_STATUS_LABEL: Record<string, string> = {
  PENDING: '待跑',
  RUNNING: '执行中',
  PASSED: '通过',
  FAILED: '未通过',
  ERROR: '异常',
  CANCELLED: '已取消',
};

export function enumLabel(
  map: Record<string, string>,
  code?: string | null,
  fallback = '—',
): string {
  if (code == null || code === '') return fallback;
  return map[code] ?? map[String(code).toUpperCase()] ?? code;
}

export const TABLE_STYLE = `
  .eval-list-row > td {
    padding: 14px 16px !important;
    border-bottom: 1px solid ${COLOR.border} !important;
  }
  .ant-table-thead > tr > th {
    background: ${COLOR.headerBg} !important;
    color: ${COLOR.textMuted} !important;
    font-size: 11px !important;
    font-weight: 700 !important;
    letter-spacing: 0.06em !important;
    text-transform: uppercase;
    padding: 10px 16px !important;
    border-bottom: 1px solid ${COLOR.border} !important;
    white-space: nowrap !important;
  }
  .ant-table-thead > tr > th::before { display: none !important; }
  .ant-table-cell-fix-left,
  .ant-table-cell-fix-right {
    background: #fff !important;
  }
  .ant-table-thead .ant-table-cell-fix-left,
  .ant-table-thead .ant-table-cell-fix-right {
    background: ${COLOR.headerBg} !important;
  }
`;

export function passRateText(
  passed?: number,
  total?: number,
): string {
  if (total == null || total <= 0) return '—';
  const p = passed ?? 0;
  return `${((p / total) * 100).toFixed(1)}% (${p}/${total})`;
}
