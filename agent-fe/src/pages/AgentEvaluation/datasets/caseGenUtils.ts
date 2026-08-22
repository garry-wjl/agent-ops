/**
 * 自动生成 Case UI 纯函数：状态展示 / 调试版本值
 */
export function caseGenStatusColor(status?: string): string {
  switch (status) {
    case 'FINISHED':
      return 'success';
    case 'FAILED':
      return 'error';
    case 'RUNNING':
    case 'PENDING':
      return 'processing';
    default:
      return 'default';
  }
}

export function caseGenStatusLabel(status?: string): string {
  switch (status) {
    case 'PENDING':
      return '排队中';
    case 'RUNNING':
      return '生成中';
    case 'FINISHED':
      return '已完成';
    case 'FAILED':
      return '失败';
    case 'CANCELLED':
      return '已取消';
    default:
      return status || '-';
  }
}

export function caseGenDebugVersionValue(v: {
  status?: string;
  versionNum?: string | null;
}): string {
  return v.status === 'DRAFT' ? 'DRAFT' : v.versionNum ?? '';
}
