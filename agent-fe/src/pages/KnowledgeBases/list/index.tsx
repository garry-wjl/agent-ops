/**
 * 知识库列表 — `/kb/manage`
 */
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Empty,
  Input,
  Segmented,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useKbListQuery } from '@/services/knowledgeBase';
import type { KbListQueryParam, KbStatus, KbType, KbVO } from '@/types';
import { KB_STATUS_META, KB_TYPE_META } from '../constants';
import PermissionGate from '@/components/PermissionGate';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  headerBg: '#ffffff',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

export default function KnowledgeBaseListPage() {
  const navigate = useNavigate();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [typeTab, setTypeTab] = useState<'ALL' | KbType>('ALL');
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');

  const query: KbListQueryParam = useMemo(
    () => ({
      pageNo,
      pageSize,
      kbType: typeTab === 'ALL' ? undefined : typeTab,
      keyword: keyword || undefined,
    }),
    [pageNo, pageSize, typeTab, keyword],
  );

  const { data: page, isFetching } = useKbListQuery(query);
  const list = page?.list ?? [];
  const total = page?.total ?? 0;

  const doSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const columns: TableColumnsType<KbVO> = [
    {
      title: '编号',
      dataIndex: 'kbNum',
      key: 'kbNum',
      width: 200,
      fixed: 'left',
      render: (kbNum: string, r) => (
        <a
          onClick={() => navigate(`/kb/manage/detail/${r.kbNum}`)}
          style={{
            fontFamily:
              'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
            fontSize: 13,
            color: COLOR.textPrimary,
          }}
        >
          {kbNum}
        </a>
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 160,
      render: (name: string) => (
        <Text style={{ color: COLOR.textPrimary, fontWeight: 500 }}>
          {name}
        </Text>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 180,
      render: (d?: string) => (
        <Text
          style={{ color: COLOR.textSecondary, maxWidth: 180 }}
          ellipsis={{ tooltip: d || '—' }}
        >
          {d || '—'}
        </Text>
      ),
    },
    {
      title: '类型',
      dataIndex: 'kbType',
      key: 'kbType',
      width: 120,
      render: (t: KbType) => {
        const meta = KB_TYPE_META[t];
        return (
          <Tag color={meta.color} style={{ background: meta.bg, border: 0 }}>
            {meta.label}
          </Tag>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (st: KbStatus) => {
        const meta = KB_STATUS_META[st] ?? {
          color: COLOR.textMuted,
          label: st,
        };
        return (
          <span style={{ color: meta.color, fontSize: 12, fontWeight: 500 }}>
            ● {meta.label}
          </span>
        );
      },
    },
    {
      title: '文件',
      dataIndex: 'fileCount',
      key: 'fileCount',
      width: 70,
      render: (c?: number) => c ?? 0,
    },
    {
      title: '分块',
      dataIndex: 'chunkCount',
      key: 'chunkCount',
      width: 70,
      render: (c?: number) => c ?? 0,
    },
    {
      title: '绑定 Agent',
      dataIndex: 'agentsBoundCount',
      key: 'agentsBoundCount',
      width: 100,
      render: (c?: number) => c ?? 0,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_: unknown, r) => (
        <a onClick={() => navigate(`/kb/manage/detail/${r.kbNum}`)}>详情</a>
      ),
    },
  ];

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 24,
        }}
      >
        <div>
          <Title
            level={2}
            style={{
              margin: 0,
              color: COLOR.textPrimary,
              fontSize: 24,
              fontWeight: 700,
            }}
          >
            知识库管理
          </Title>
          <Text
            style={{
              color: COLOR.textSecondary,
              fontSize: 14,
              marginTop: 4,
              display: 'block',
            }}
          >
            上传文档、配置索引与检索，供 Agent 挂载增强回复准确性
          </Text>
        </div>
        <PermissionGate anyOf={['knowledge_base:create']}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/kb/manage/editor/new')}
          >
            新建知识库
          </Button>
        </PermissionGate>
      </div>

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
        <Segmented
          value={typeTab}
          onChange={(v) => {
            setTypeTab(v as 'ALL' | KbType);
            setPageNo(1);
          }}
          options={[
            { value: 'ALL', label: '全部' },
            { value: 'SIMPLE', label: '简易' },
            { value: 'RAG_FLOW', label: 'RAG Flow' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder="搜索编号 / 名称 / 描述…"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onPressEnter={doSearch}
          onBlur={doSearch}
          style={{ width: 280 }}
        />
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table<KbVO>
          rowKey="kbNum"
          columns={columns}
          dataSource={list}
          loading={isFetching}
          size="middle"
          scroll={{ x: 1100 }}
          locale={{
            emptyText: (
              <Empty description="还没有知识库" style={{ padding: 32 }} />
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

      <style>{`
        .ant-table-thead > tr > th {
          background: ${COLOR.headerBg} !important;
          color: ${COLOR.textMuted} !important;
          font-size: 11px !important;
          font-weight: 700 !important;
          letter-spacing: 0.06em !important;
          text-transform: uppercase;
        }
      `}</style>
    </div>
  );
}
