import type { ReactNode } from 'react';
import { Tag } from 'antd';

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'block' }}>
      <div style={{ marginBottom: 6, color: '#45556C', fontSize: 12 }}>{label}</div>
      {children}
    </label>
  );
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <div style={{ marginTop: 4, color: '#90A1B9', fontSize: 12, fontWeight: 600, letterSpacing: '0.04em' }}>
      {children}
    </div>
  );
}

const TEAM_STATUS = {
  DRAFT: { color: 'gold', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
} as const;

export function TeamStatusTag({ status }: { status: keyof typeof TEAM_STATUS }) {
  const item = TEAM_STATUS[status];
  return <Tag color={item.color}>{item.label}</Tag>;
}

const RUN_STATUS = {
  running: { color: 'processing', label: '进行中' },
  waiting: { color: 'warning', label: '待审批' },
  succeeded: { color: 'success', label: '已完成' },
  failed: { color: 'error', label: '失败' },
  cancelled: { color: 'default', label: '已取消' },
  pending: { color: 'default', label: '等待' },
  waiting_approval: { color: 'warning', label: '待审批' },
  assigning: { color: 'processing', label: '待分派' },
  working: { color: 'processing', label: '执行中' },
} as const;

export function RunStatusTag({ status }: { status: string }) {
  const item = RUN_STATUS[status as keyof typeof RUN_STATUS] ?? { color: 'default', label: status };
  return <Tag color={item.color}>{item.label}</Tag>;
}
