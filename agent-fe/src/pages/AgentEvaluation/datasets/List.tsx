/**
 * 评测集列表 — Tab「评测集」
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import { agentApi } from '@/services/agent';
import type { AgentVO, EvalDatasetVO } from '@/types';
import PermissionGate from '@/components/PermissionGate';
import { COLOR, EVAL_BASE, TABLE_STYLE, DATASET_STATUS_LABEL, DATASET_TYPE_LABEL, enumLabel } from '../constants';

const { Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
};

export default function DatasetListPage() {
  const navigate = useNavigate();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [type, setType] = useState<string | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [agentNum, setAgentNum] = useState<string | undefined>();
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [list, setList] = useState<EvalDatasetVO[]>([]);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    void agentApi
      .pageList({ pageNo: 1, pageSize: 100 })
      .then((ag) => setAgents(ag?.list ?? []))
      .catch(() => undefined);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await evalApi.pageDatasets({
        pageNo,
        pageSize,
        keyword: keyword || undefined,
        type,
        status,
        agentNum,
      });
      setList(page?.list ?? []);
      setTotal(page?.total ?? 0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, keyword, type, status, agentNum]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = (r: EvalDatasetVO) => {
    Modal.confirm({
      title: '删除评测集',
      content: `确认删除「${r.name}」？删除后不可恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await evalApi.deleteDataset(r.num);
        message.success('已删除');
        void load();
      },
    });
  };

  const agentOptions = useMemo(
    () =>
      agents.map((a) => ({
        value: a.num,
        label: `${a.name || a.num} (${a.num})`,
      })),
    [agents],
  );

  const columns: TableColumnsType<EvalDatasetVO> = useMemo(
    () => [
      {
        title: '编号',
        dataIndex: 'num',
        key: 'num',
        width: 200,
        render: (num: string, r) => (
          <a
            onClick={() => navigate(`${EVAL_BASE}/datasets/${r.num}`)}
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
            }}
          >
            {num}
          </a>
        ),
      },
      {
        title: '名称',
        dataIndex: 'name',
        key: 'name',
        width: 180,
        ellipsis: true,
        render: (n: string) => (
          <Text
            ellipsis={{ tooltip: n }}
            style={{ color: COLOR.textPrimary, maxWidth: '100%' }}
          >
            {n}
          </Text>
        ),
      },
      {
        title: '类型',
        dataIndex: 'type',
        key: 'type',
        width: 100,
        render: (t: string) => <Tag>{enumLabel(DATASET_TYPE_LABEL, t)}</Tag>,
      },
      {
        title: 'Agent',
        dataIndex: 'agentNum',
        key: 'agentNum',
        width: 160,
        ellipsis: true,
        render: (n?: string) =>
          n ? (
            <Text ellipsis={{ tooltip: n }} style={{ fontSize: 12, maxWidth: '100%' }}>
              {n}
            </Text>
          ) : (
            <Text type="secondary">—</Text>
          ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 110,
        render: (s: string) => (
          <Tag color={STATUS_COLOR[s] ?? 'default'}>
            {enumLabel(DATASET_STATUS_LABEL, s)}
          </Tag>
        ),
      },
      {
        title: '最新版本',
        dataIndex: 'latestVersion',
        key: 'latestVersion',
        width: 100,
        render: (v?: number) => (v != null && v > 0 ? `v${v}` : '—'),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        key: 'updateTime',
        width: 170,
        render: (t?: string) => (
          <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>{t || '—'}</Text>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 180,
        fixed: 'right',
        render: (_, r) => (
          <Space size={12}>
            <a onClick={() => navigate(`${EVAL_BASE}/datasets/${r.num}`)}>详情</a>
            <PermissionGate anyOf={['evaluation:dataset:update']}>
              <a onClick={() => navigate(`${EVAL_BASE}/datasets/${r.num}/edit`)}>
                编辑
              </a>
            </PermissionGate>
            <PermissionGate anyOf={['evaluation:dataset:delete']}>
              <a style={{ color: '#DC2626' }} onClick={() => handleDelete(r)}>
                删除
              </a>
            </PermissionGate>
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [navigate],
  );

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <Space wrap>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="关联 Agent"
            style={{ width: 220 }}
            value={agentNum}
            options={agentOptions}
            onChange={(v) => {
              setAgentNum(v);
              setPageNo(1);
            }}
          />
          <Select
            allowClear
            placeholder="类型"
            style={{ width: 120 }}
            value={type}
            onChange={(v) => {
              setType(v);
              setPageNo(1);
            }}
            options={[
              { value: 'AGENT', label: enumLabel(DATASET_TYPE_LABEL, 'AGENT') },
              { value: 'CUSTOM', label: enumLabel(DATASET_TYPE_LABEL, 'CUSTOM') },
            ]}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPageNo(1);
            }}
            options={[
              { value: 'DRAFT', label: enumLabel(DATASET_STATUS_LABEL, 'DRAFT') },
              {
                value: 'PUBLISHED',
                label: enumLabel(DATASET_STATUS_LABEL, 'PUBLISHED'),
              },
            ]}
          />
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
            placeholder="搜索名称 / 编号…"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onPressEnter={() => {
              setKeyword(keywordInput.trim());
              setPageNo(1);
            }}
            onBlur={() => {
              setKeyword(keywordInput.trim());
              setPageNo(1);
            }}
            style={{ width: 240 }}
          />
        </Space>
        <PermissionGate anyOf={['evaluation:dataset:create']}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate(`${EVAL_BASE}/datasets/new`)}
          >
            创建评测集
          </Button>
        </PermissionGate>
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
          background: '#fff',
        }}
      >
        <Table<EvalDatasetVO>
          rowKey="num"
          columns={columns}
          dataSource={list}
          loading={loading}
          size="middle"
          scroll={{ x: 1260 }}
          rowClassName={() => 'eval-list-row'}
          locale={{
            emptyText: (
              <Empty description="还没有评测集" style={{ padding: 32 }} />
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, ps) => {
              setPageNo(p);
              setPageSize(ps);
            },
          }}
        />
      </div>
      <style>{TABLE_STYLE}</style>
    </>
  );
}
