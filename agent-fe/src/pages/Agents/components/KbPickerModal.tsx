/**
 * 知识库多选弹窗 — Agent 绑定：每项可配置 retrievalMode / topK / minScore
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Checkbox,
  Empty,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import type {
  KnowledgeBaseBindingParam,
  MountableKbItem,
  RetrievalMode,
} from '@/types';
import {
  DEFAULT_RETRIEVAL,
  KB_TYPE_META,
  RETRIEVAL_MODE_LABEL,
} from '@/pages/KnowledgeBases/constants';

const { Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textMuted: '#90A1B9',
  textSecondary: '#45556C',
} as const;

export interface KbPickerModalProps {
  open: boolean;
  options: MountableKbItem[];
  value: KnowledgeBaseBindingParam[];
  emptyGuide: string;
  emptyTo?: string;
  onOk: (bindings: KnowledgeBaseBindingParam[]) => void;
  onCancel: () => void;
}

type RowState = KnowledgeBaseBindingParam & {
  name: string;
  kbType: string;
  selected: boolean;
};

export default function KbPickerModal({
  open,
  options,
  value,
  emptyGuide,
  emptyTo,
  onOk,
  onCancel,
}: KbPickerModalProps) {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [rows, setRows] = useState<RowState[]>([]);

  useEffect(() => {
    if (!open) return;
    setKeyword('');
    const map = new Map(value.map((b) => [b.kbNum, b]));
    setRows(
      options.map((o) => {
        const prev = map.get(o.kbNum);
        return {
          kbNum: o.kbNum,
          name: o.name,
          kbType: o.kbType,
          selected: !!prev,
          retrievalMode:
            prev?.retrievalMode ?? DEFAULT_RETRIEVAL.retrievalMode,
          topK: prev?.topK ?? DEFAULT_RETRIEVAL.topK,
          minScore: prev?.minScore ?? DEFAULT_RETRIEVAL.minScore,
        };
      }),
    );
  }, [open, options, value]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return rows;
    return rows.filter(
      (r) =>
        r.name.toLowerCase().includes(kw) ||
        r.kbNum.toLowerCase().includes(kw) ||
        r.kbType.toLowerCase().includes(kw),
    );
  }, [rows, keyword]);

  const patchRow = (kbNum: string, patch: Partial<RowState>) => {
    setRows((prev) =>
      prev.map((r) => (r.kbNum === kbNum ? { ...r, ...patch } : r)),
    );
  };

  const columns: ColumnsType<RowState> = [
    {
      title: '',
      key: 'select',
      width: 48,
      render: (_: unknown, row) => (
        <Checkbox
          checked={row.selected}
          onChange={(e) => patchRow(row.kbNum, { selected: e.target.checked })}
        />
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row) => (
        <div>
          <Text style={{ fontWeight: 500, color: COLOR.textPrimary }}>{name}</Text>
          <div style={{ fontSize: 12, color: COLOR.textMuted }}>{row.kbNum}</div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'kbType',
      key: 'kbType',
      width: 110,
      render: (t: string) =>
        KB_TYPE_META[t as 'SIMPLE' | 'RAG_FLOW']?.label ?? t,
    },
    {
      title: '检索模式',
      key: 'retrievalMode',
      width: 130,
      render: (_: unknown, row) => (
        <Select
          size="small"
          disabled={!row.selected}
          value={row.retrievalMode ?? 'AUTO'}
          onChange={(v) =>
            patchRow(row.kbNum, { retrievalMode: v as RetrievalMode })
          }
          options={Object.entries(RETRIEVAL_MODE_LABEL).map(([k, label]) => ({
            value: k,
            label,
          }))}
        />
      ),
    },
    {
      title: 'Top K',
      key: 'topK',
      width: 90,
      render: (_: unknown, row) => (
        <InputNumber
          size="small"
          min={1}
          max={50}
          disabled={!row.selected}
          value={row.topK}
          onChange={(v) => patchRow(row.kbNum, { topK: v ?? undefined })}
        />
      ),
    },
    {
      title: '最低分',
      key: 'minScore',
      width: 90,
      render: (_: unknown, row) => (
        <InputNumber
          size="small"
          min={0}
          max={1}
          step={0.05}
          disabled={!row.selected}
          value={row.minScore}
          onChange={(v) => patchRow(row.kbNum, { minScore: v ?? undefined })}
        />
      ),
    },
  ];

  const handleOk = () => {
    const bindings = rows
      .filter((r) => r.selected)
      .map((r) => ({
        kbNum: r.kbNum,
        retrievalMode: r.retrievalMode,
        topK: r.topK,
        minScore: r.minScore,
      }));
    onOk(bindings);
  };

  return (
    <Modal
      open={open}
      title="选择知识库"
      width={880}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
      okText="确认"
      cancelText="取消"
    >
      {options.length === 0 ? (
        <Empty description={emptyGuide}>
          {emptyTo && (
            <Button type="primary" onClick={() => navigate(emptyTo)}>
              前往知识库管理
            </Button>
          )}
        </Empty>
      ) : (
        <>
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
            placeholder="搜索名称 / 编号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          <Table<RowState>
            size="small"
            rowKey="kbNum"
            pagination={false}
            dataSource={filtered}
            columns={columns}
            scroll={{ y: 360 }}
          />
          <Text style={{ fontSize: 12, color: COLOR.textSecondary, marginTop: 8 }}>
            已选 {rows.filter((r) => r.selected).length} 个知识库
          </Text>
        </>
      )}
    </Modal>
  );
}
