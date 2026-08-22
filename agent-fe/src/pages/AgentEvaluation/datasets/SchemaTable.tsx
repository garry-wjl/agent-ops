/**
 * 评测集 Schema 多层表格展示（只读）。
 */
import { Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  flattenSchemaToTableRows,
  parseSchemaNodes,
  type SchemaTableRow,
} from '@/utils/datasetSchema';
import { COLOR } from '../constants';

const { Text } = Typography;

export default function SchemaTable({ schemaJson }: { schemaJson?: string }) {
  const rows = flattenSchemaToTableRows(parseSchemaNodes(schemaJson));

  const columns: ColumnsType<SchemaTableRow> = [
    {
      title: '字段路径',
      dataIndex: 'path',
      width: '36%',
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
      width: 140,
      render: (t: string) => (
        <Text style={{ fontSize: 13, color: COLOR.textSecondary }}>{t}</Text>
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      render: (d?: string) => (
        <Text style={{ fontSize: 13, color: COLOR.textMuted }}>{d || '—'}</Text>
      ),
    },
  ];

  return (
    <Table<SchemaTableRow>
      size="small"
      rowKey="key"
      pagination={false}
      columns={columns}
      dataSource={rows}
      style={{ border: `1px solid ${COLOR.border}`, borderRadius: 8 }}
    />
  );
}
