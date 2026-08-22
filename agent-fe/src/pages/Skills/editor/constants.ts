/**
 * Skill 编辑器 / 检测 共享样式常量。
 * 与 list / detail 页保持同一套扁平化配色（白底 + #E2E8F0 边框 + radius 8）。
 */
export const COLOR = {
  border: '#E2E8F0',
  headerBg: '#ffffff',
  textPrimary: '#0F172B',
  textBody: '#1D293D',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  primaryBlue: '#2563EB',
  selfBg: '#EFF6FF',
  selfText: '#2563EB',
  success: '#10B981',
  successBg: '#ECFDF5',
  warning: '#D97706',
  warningBg: '#FFFBEB',
  danger: '#DC2626',
  dangerBg: '#FEF2F2',
  bgTag: '#F1F5F9',
} as const;

/** Monaco 编辑器通用 options（与 Tools 编辑器一致）。 */
export const MONACO_OPTIONS = {
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 13,
  tabSize: 2,
  wordWrap: 'on',
  lineNumbers: 'on',
  folding: true,
  automaticLayout: true,
} as const;
