/**
 * 表单/表头用：文案 + 问号 Tooltip
 */
import type { ReactNode } from 'react';
import { Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { COLOR } from './constants';

export function LabelWithTip({
  label,
  tip,
}: {
  label: ReactNode;
  /** 支持多行说明（string 或 ReactNode） */
  tip: ReactNode;
}) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      {label}
      <Tooltip
        title={
          typeof tip === 'string' ? (
            tip
          ) : (
            <div style={{ maxWidth: 360 }}>{tip}</div>
          )
        }
        styles={{ root: { maxWidth: 400 } }}
      >
        <QuestionCircleOutlined
          style={{ color: COLOR.textMuted, fontSize: 12, cursor: 'help' }}
          onClick={(e) => e.preventDefault()}
        />
      </Tooltip>
    </span>
  );
}
