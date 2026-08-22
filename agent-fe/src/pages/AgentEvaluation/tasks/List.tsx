/**
 * 评测任务列表 — Tab「评测任务」（默认 Tab）
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
import {
  DiffOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import { agentApi } from '@/services/agent';
import type { AgentVO, EvalTaskVO } from '@/types';
import PermissionGate from '@/components/PermissionGate';
import { COLOR, EVAL_BASE, TABLE_STYLE, passRateText, TASK_STATUS_LABEL, enumLabel } from '../constants';

const { Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  FINISHED: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
};

export default function TaskListPage() {
  const navigate = useNavigate();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [agentNum, setAgentNum] = useState<string | undefined>();
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [list, setList] = useState<EvalTaskVO[]>([]);
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
      const page = await evalApi.pageTasks({
        pageNo,
        pageSize,
        keyword: keyword || undefined,
        status,
        agentNum,
      });
      setList(page?.list ?? []);
      setTotal(page?.total ?? 0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, keyword, status, agentNum]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = (r: EvalTaskVO) => {
    Modal.confirm({
      title: '删除评测任务',
      content: `确认删除「${r.name}」？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await evalApi.deleteTask(r.num);
        message.success('已删除');
        void load();
      },
    });
  };

  const handleCancel = (r: EvalTaskVO) => {
    Modal.confirm({
      title: '取消任务',
      content: `确认取消运行中的任务「${r.name}」？`,
      okText: '取消任务',
      cancelText: '返回',
      onOk: async () => {
        await evalApi.cancelTask(r.num);
        message.success('已取消');
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

  const columns: TableColumnsType<EvalTaskVO> = useMemo(
    () => [
      {
        title: '编号',
        dataIndex: 'num',
        width: 200,
        fixed: 'left',
        render: (n: string, r) => (
          <a
            onClick={() => navigate(`${EVAL_BASE}/tasks/${r.num}`)}
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
              whiteSpace: 'nowrap',
            }}
          >
            {n}
          </a>
        ),
      },
      {
        title: '名称',
        dataIndex: 'name',
        width: 160,
        ellipsis: true,
        render: (n: string) => (
          <Text ellipsis={{ tooltip: n }} style={{ maxWidth: '100%' }}>
            {n}
          </Text>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (s: string) => (
          <Tag color={STATUS_COLOR[s] ?? 'default'}>
            {enumLabel(TASK_STATUS_LABEL, s)}
          </Tag>
        ),
      },
      {
        title: '评估器',
        key: 'graders',
        width: 220,
        render: (_, r) => {
          const graders = r.graders ?? [];
          if (graders.length === 0) {
            return <Text type="secondary">—</Text>;
          }
          return (
            <Space size={[4, 4]} wrap>
              {graders.map((g) => (
                <Tag
                  key={g.graderNum}
                  style={{ marginInlineEnd: 0, maxWidth: 200 }}
                  title={g.graderNum}
                >
                  <span
                    style={{
                      display: 'inline-block',
                      maxWidth: 160,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      verticalAlign: 'bottom',
                    }}
                  >
                    {g.name || g.graderNum}
                  </span>
                  {g.kind ? (
                    <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
                      {g.kind}
                    </Text>
                  ) : null}
                </Tag>
              ))}
            </Space>
          );
        },
      },
      {
        title: '评测集',
        key: 'dataset',
        width: 180,
        render: (_, r) => (
          <Text style={{ fontSize: 12, color: COLOR.textSecondary }}>
            {r.datasetNum}
            {r.datasetVersion != null ? ` @v${r.datasetVersion}` : ''}
          </Text>
        ),
      },
      {
        title: 'Agent',
        key: 'agent',
        width: 160,
        render: (_, r) =>
          r.bindMode === 'AGENT' ? (
            <Text style={{ fontSize: 12 }} ellipsis>
              {r.agentNum || '—'}
            </Text>
          ) : (
            <Text type="secondary">NONE</Text>
          ),
      },
      {
        title: '通过率',
        key: 'passRate',
        width: 140,
        render: (_, r) => passRateText(r.passedCount, r.totalCount),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
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
          <Space size={12} wrap>
            <a onClick={() => navigate(`${EVAL_BASE}/tasks/${r.num}`)}>详情</a>
            {r.status === 'RUNNING' && (
              <PermissionGate anyOf={['evaluation:task:execute']}>
                <a onClick={() => handleCancel(r)}>取消</a>
              </PermissionGate>
            )}
            <PermissionGate anyOf={['evaluation:task:delete']}>
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
            placeholder="状态"
            style={{ width: 140 }}
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPageNo(1);
            }}
            options={[
              'PENDING',
              'RUNNING',
              'FINISHED',
              'FAILED',
              'CANCELLED',
            ].map((s) => ({
              value: s,
              label: enumLabel(TASK_STATUS_LABEL, s),
            }))}
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
        <Space>
          <Button
            icon={<DiffOutlined />}
            onClick={() => navigate(`${EVAL_BASE}/compare`)}
          >
            对比
          </Button>
          <PermissionGate anyOf={['evaluation:task:create']}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate(`${EVAL_BASE}/tasks/new`)}
            >
              创建任务
            </Button>
          </PermissionGate>
        </Space>
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table<EvalTaskVO>
          rowKey="num"
          columns={columns}
          dataSource={list}
          loading={loading}
          size="middle"
          scroll={{ x: 1420 }}
          rowClassName={() => 'eval-list-row'}
          locale={{
            emptyText: (
              <Empty description="还没有评测任务" style={{ padding: 32 }} />
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
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
