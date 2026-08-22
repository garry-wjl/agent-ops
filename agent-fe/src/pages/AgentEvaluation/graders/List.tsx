/**
 * 评估器列表 — Tab「评估器」
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Empty,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import type { EvalGraderVO } from '@/types';
import PermissionGate from '@/components/PermissionGate';
import { COLOR, EVAL_BASE, TABLE_STYLE } from '../constants';

const { Text } = Typography;

export default function GraderListPage() {
  const navigate = useNavigate();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [list, setList] = useState<EvalGraderVO[]>([]);
  const [total, setTotal] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await evalApi.pageGraders({
        pageNo,
        pageSize,
        keyword: keyword || undefined,
      });
      setList(page?.list ?? []);
      setTotal(page?.total ?? 0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = (r: EvalGraderVO) => {
    Modal.confirm({
      title: '删除评估器',
      content: `确认删除「${r.name}」？若已被任务引用可能失败。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await evalApi.deleteGrader(r.num);
        message.success('已删除');
        void load();
      },
    });
  };

  const columns: TableColumnsType<EvalGraderVO> = useMemo(
    () => [
      {
        title: '编号',
        dataIndex: 'num',
        width: 200,
        render: (n: string, r) => (
          <a
            onClick={() => navigate(`${EVAL_BASE}/graders/${r.num}`)}
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
            }}
          >
            {n}
          </a>
        ),
      },
      {
        title: '名称',
        dataIndex: 'name',
        width: 180,
        ellipsis: true,
        render: (n: string) => (
          <Text ellipsis={{ tooltip: n }} style={{ maxWidth: '100%' }}>
            {n}
          </Text>
        ),
      },
      {
        title: '类型',
        dataIndex: 'kind',
        width: 100,
        render: (k: string) => <Tag>{k}</Tag>,
      },
      {
        title: '预置码',
        dataIndex: 'builtinCode',
        width: 140,
        render: (c?: string) =>
          c ? (
            <Text code style={{ fontSize: 12 }}>
              {c}
            </Text>
          ) : (
            '—'
          ),
      },
      {
        title: '版本',
        dataIndex: 'version',
        width: 80,
        render: (v?: number) => (v != null ? `v${v}` : '—'),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
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
            <a onClick={() => navigate(`${EVAL_BASE}/graders/${r.num}`)}>详情</a>
            <PermissionGate anyOf={['evaluation:grader:update']}>
              <a onClick={() => navigate(`${EVAL_BASE}/graders/${r.num}/edit`)}>
                编辑
              </a>
            </PermissionGate>
            <PermissionGate anyOf={['evaluation:grader:delete']}>
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
          style={{ width: 280 }}
        />
        <PermissionGate anyOf={['evaluation:grader:create']}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate(`${EVAL_BASE}/graders/new`)}
          >
            创建评估器
          </Button>
        </PermissionGate>
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table<EvalGraderVO>
          rowKey="num"
          columns={columns}
          dataSource={list}
          loading={loading}
          size="middle"
          scroll={{ x: 1040 }}
          rowClassName={() => 'eval-list-row'}
          locale={{
            emptyText: (
              <Empty description="还没有评估器" style={{ padding: 32 }} />
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
