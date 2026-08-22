/**
 * 用例数据展平表 — 与 SchemaTable 同款路径表 UI，多一列「值」。
 */
import { Input, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { CaseDataTableRow } from '@/utils/datasetSchema';
import { COLOR } from '../constants';

const { Text } = Typography;

export interface CaseDataTableProps {
  rows: CaseDataTableRow[];
  /** 可编辑时传入；仅 editable 行可改 */
  editable?: boolean;
  edits?: Record<string, string>;
  onEditChange?: (path: string, value: string) => void;
}

export default function CaseDataTable({
  rows,
  editable = false,
  edits,
  onEditChange,
}: CaseDataTableProps) {
  const columns: ColumnsType<CaseDataTableRow> = [
    {
      title: '字段路径',
      dataIndex: 'path',
      width: '28%',
      render: (path: string, row) => (
        <Text
          code
          style={{
            fontSize: 12,
            paddingLeft: Math.max(0, (row.depth - 1) * 12),
            display: 'inline-block',
          }}
        >
          {path}
        </Text>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (t: string) => (
        <Text style={{ fontSize: 13, color: COLOR.textSecondary }}>{t}</Text>
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      width: '18%',
      render: (d?: string) => (
        <Text style={{ fontSize: 13, color: COLOR.textMuted }}>{d || '—'}</Text>
      ),
    },
    {
      title: '值',
      dataIndex: 'value',
      render: (_: string, row) => {
        if (editable && row.editable) {
          const val = edits?.[row.path] ?? row.value;
          const multiline =
            row.type === 'object' ||
            row.type.startsWith('array') ||
            val.includes('\n') ||
            val.length > 60;
          return multiline ? (
            <Input.TextArea
              rows={3}
              value={val}
              onChange={(e) => onEditChange?.(row.path, e.target.value)}
              style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
            />
          ) : (
            <Input
              value={val}
              onChange={(e) => onEditChange?.(row.path, e.target.value)}
              style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
            />
          );
        }
        if (!row.editable && !row.value) {
          return (
            <Text style={{ fontSize: 13, color: COLOR.textMuted }}>—</Text>
          );
        }
        return (
          <Text
            style={{
              fontSize: 12,
              fontFamily: 'ui-monospace, monospace',
              color: COLOR.textSecondary,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            {row.value || '—'}
          </Text>
        );
      },
    },
  ];

  return (
    <Table<CaseDataTableRow>
      size="small"
      rowKey="key"
      pagination={false}
      columns={columns}
      dataSource={rows}
      style={{ border: `1px solid ${COLOR.border}`, borderRadius: 8 }}
    />
  );
}
