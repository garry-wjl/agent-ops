/**
 * Agent 列表页 — `/agent/manage`
 *
 * 对齐 PRD §6.1 / §7.1
 *  - v2.1 精简列、v2.2 新增 agentSource、v2.3 不影响列
 *  - v2.4：启用「+ 新建 Agent」按钮 → 弹 CreateMethodPickerModal
 *      · 选 CONFIG → 5 步表单 Drawer（CreateForm）
 *      · 选 A2A   → A2A 接入表单 Drawer（A2aCreateForm）
 *  - v2.6：状态胶囊新增 PENDING_SYNC（橙色"● 待同步"）；
 *      A2A 行操作按状态分支：[继续接入] / [删除草稿]（v3.x：[取消订阅] 已收敛到详情页头部）
 */
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Drawer,
  Input,
  Modal,
  Space,
  Table,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useA2aUnsubscribeMutation,
  useAgentPageQuery,
  agentApi,
} from '@/services/agent';
import type { AgentVO } from '@/types';
import A2aCreateForm from '../components/A2aCreateForm';
import PermissionGate from '@/components/PermissionGate';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  headerBg: '#F8FAFC',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  // 状态胶囊
  statusOnlineBg: '#ECFDF5',
  statusOnlineBorder: '#A7F3D0',
  statusOnlineText: '#059669',
  statusOfflineBg: '#FEF2F2',
  statusOfflineBorder: '#FECACA',
  statusOfflineText: '#DC2626',
  statusDraftBg: '#FEF3C7',
  statusDraftBorder: '#FDE68A',
  statusDraftText: '#D97706',
  // v2.6 新增 PENDING_SYNC（待同步）
  statusPendingBg: '#FFF7ED',
  statusPendingBorder: '#FED7AA',
  statusPendingText: '#EA580C',
  // A2A 徽标
  a2aBg: '#EFF6FF',
  a2aBorder: '#DBEAFE',
  a2aText: '#2563EB',
} as const;

const STATUS_MAP: Record<
  AgentVO['status'],
  { label: string; bg: string; border: string; color: string }
> = {
  PUBLISHED: {
    label: '在线',
    bg: COLOR.statusOnlineBg,
    border: COLOR.statusOnlineBorder,
    color: COLOR.statusOnlineText,
  },
  OFFLINE: {
    label: '已下线',
    bg: COLOR.statusOfflineBg,
    border: COLOR.statusOfflineBorder,
    color: COLOR.statusOfflineText,
  },
  DRAFT_ONLY: {
    label: '草稿',
    bg: COLOR.statusDraftBg,
    border: COLOR.statusDraftBorder,
    color: COLOR.statusDraftText,
  },
  PENDING_SYNC: {
    label: '● 待同步',
    bg: COLOR.statusPendingBg,
    border: COLOR.statusPendingBorder,
    color: COLOR.statusPendingText,
  },
};

function formatDateTime(s?: string): string {
  if (!s) return '-';
  const d = dayjs(s);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-';
}

function SkillsCell({ count, names }: { count: number; names: string[] }) {
  if (!count) {
    return <Text style={{ color: COLOR.textMuted }}>0</Text>;
  }
  const head = names.slice(0, 8);
  const rest = Math.max(0, names.length - head.length);
  const tip = (
    <div style={{ maxWidth: 240 }}>
      {head.map((n) => (
        <div key={n} style={{ fontSize: 12, lineHeight: '18px' }}>
          {n}
        </div>
      ))}
      {rest > 0 && (
        <div style={{ fontSize: 12, color: COLOR.textMuted, marginTop: 4 }}>
          ……还有 {rest} 个
        </div>
      )}
    </div>
  );
  return (
    <Tooltip title={tip} placement="topRight">
      <span
        style={{
          color: COLOR.textPrimary,
          fontWeight: 500,
          cursor: 'help',
        }}
      >
        {count}
      </span>
    </Tooltip>
  );
}

export default function AgentListPage() {
  const navigate = useNavigate();
  // v2.6：A2A Drawer（仅「继续接入草稿」场景）
  const [a2aDrawerOpen, setA2aDrawerOpen] = useState(false);
  /** A2A Drawer 内传入的草稿编号（继续接入草稿场景） */
  const [a2aDraftAgentNum, setA2aDraftAgentNum] = useState<string | undefined>();
  const [a2aInitialValues, setA2aInitialValues] = useState<
    | {
        nacosAgentName?: string;
        displayName?: string;
        description?: string;
        remark?: string;
      }
    | undefined
  >();
  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const { data: agentPage, refetch: refetchAgents } = useAgentPageQuery({
    pageNo,
    pageSize,
    keyword: keyword || undefined,
  });

  const unsubscribeMutation = useA2aUnsubscribeMutation();

  const data = agentPage?.list ?? [];
  const total = agentPage?.total ?? data.length;

  /** 搜索：回第 1 页（query 变化触发 react-query 重新拉取）。 */
  const handleSearch = () => setPageNo(1);

  /** 草稿继续接入：拉详情把已有字段回填到 A2aCreateForm */
  const handleContinueDraft = async (r: AgentVO) => {
    try {
      const detail = await agentApi.detail(r.num);
      setA2aDraftAgentNum(r.num);
      setA2aInitialValues({
        // a2aSource 在草稿态可能尚未拉到 AgentCard，所以 nacosAgentName 兜底用 detail.name
        nacosAgentName:
          detail.a2aSource?.nacosService ??
          (detail as any).nacosAgentName ??
          undefined,
        displayName: detail.name,
        description: detail.description,
        remark: undefined,
      });
      setA2aDrawerOpen(true);
    } catch {
      // 拦截器已 toast
    }
  };

  /** 草稿删除：复用 unsubscribe 接口（后端语义统一为删除尚未接入完成的草稿条目） */
  const handleDeleteDraft = (r: AgentVO) => {
    Modal.confirm({
      title: `删除草稿 ${r.name}？`,
      content: '草稿删除后不可恢复。',
      okType: 'danger',
      okText: '删除',
      onOk: async () => {
        await unsubscribeMutation.mutateAsync(r.num);
        message.success('草稿已删除');
        refetchAgents();
      },
    });
  };

  const columns: TableColumnsType<AgentVO> = useMemo(
    () => [
      {
        title: '编码',
        dataIndex: 'num',
        key: 'num',
        width: 160,
        fixed: 'left',
        render: (num: string, r) => (
          <a
            onClick={() => navigate(`/agent/manage/detail/${r.num}`)}
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
              whiteSpace: 'nowrap',
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
        width: 200,
        ellipsis: true,
        render: (name: string) => (
          <Tooltip title={name} placement="topLeft">
            <Text
              style={{
                color: COLOR.textPrimary,
                fontWeight: 500,
                display: 'inline-block',
                maxWidth: '100%',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {name}
            </Text>
          </Tooltip>
        ),
      },
      {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
        render: (desc?: string) => {
          const text = desc?.trim() || '-';
          return (
            <Tooltip title={desc?.trim() ? desc : undefined} placement="topLeft">
              <Text
                style={{
                  color: COLOR.textSecondary,
                  display: 'inline-block',
                  maxWidth: '100%',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {text}
              </Text>
            </Tooltip>
          );
        },
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 110,
        render: (s: AgentVO['status']) => {
          const cfg = STATUS_MAP[s] ?? STATUS_MAP.DRAFT_ONLY;
          return (
            <span
              style={{
                background: cfg.bg,
                border: `1px solid ${cfg.border}`,
                color: cfg.color,
                padding: '2px 10px',
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              {cfg.label}
            </span>
          );
        },
      },
      {
        title: 'Skills',
        key: 'skills',
        width: 80,
        align: 'right',
        render: (_, r) => (
          <SkillsCell count={r.skillNum ?? 0} names={r.skillNames ?? []} />
        ),
      },
      {
        title: '创建方式',
        dataIndex: 'creationMode',
        key: 'creationMode',
        width: 140,
        render: (m: AgentVO['creationMode']) =>
          m === 'A2A' ? (
            <span
              style={{
                background: COLOR.a2aBg,
                border: `1px solid ${COLOR.a2aBorder}`,
                color: COLOR.a2aText,
                padding: '2px 10px',
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 500,
                whiteSpace: 'nowrap',
              }}
            >
              🔵 A2A·Nacos
            </span>
          ) : (
            <Text style={{ color: COLOR.textSecondary, whiteSpace: 'nowrap' }}>
              人工配置
            </Text>
          ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 150,
        render: (t?: string) => (
          <Text style={{ color: COLOR.textSecondary, fontSize: 13 }}>
            {formatDateTime(t)}
          </Text>
        ),
      },
      {
        title: '最后更新时间',
        dataIndex: 'updateTime',
        key: 'updateTime',
        width: 150,
        render: (t?: string) => (
          <Text style={{ color: COLOR.textSecondary, fontSize: 13 }}>
            {formatDateTime(t)}
          </Text>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 220,
        fixed: 'right',
        render: (_, r) => {
          const isA2a = r.creationMode === 'A2A';
          const items: React.ReactNode[] = [
            <a
              key="view"
              onClick={() => navigate(`/agent/manage/detail/${r.num}`)}
            >
              查看
            </a>,
            <PermissionGate key="debug" anyOf={['agent:invoke']}>
              <a
                onClick={() =>
                  navigate(`/agent/debug?agent=${encodeURIComponent(r.num)}`)
                }
              >
                调试
              </a>
            </PermissionGate>,
          ];
          if (isA2a) {
            if (r.status === 'DRAFT_ONLY') {
              items.push(
                <PermissionGate key="continue" anyOf={['agent:update']}>
                  <a onClick={() => handleContinueDraft(r)}>
                    继续接入
                  </a>
                </PermissionGate>,
              );
              items.push(
                <PermissionGate key="del" anyOf={['agent:delete']}>
                  <a
                    style={{ color: '#DC2626' }}
                    onClick={() => handleDeleteDraft(r)}
                  >
                    删除草稿
                  </a>
                </PermissionGate>,
              );
            }
          }
          return <Space size={12}>{items}</Space>;
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [navigate],
  );

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      {/* 标题区 */}
      <div style={{ marginBottom: 24 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
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
              Agent 管理
            </Title>
            <Text
              style={{
                color: COLOR.textSecondary,
                fontSize: 14,
                marginTop: 4,
                display: 'block',
              }}
            >
              新建、调试、发布 Agent；支持配置模式与 A2A 远端接入，纳管模型/技能/工具/沙箱
            </Text>
          </div>
          <PermissionGate anyOf={['agent:create']}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/agent/manage/editor/new')}
            >
              新建 Agent
            </Button>
          </PermissionGate>
        </div>
      </div>

      {/* 工具栏：统计 + 搜索 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Text style={{ color: COLOR.textSecondary, fontSize: 14 }}>
          全部 {total} 个 agent
        </Text>
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder="搜索 agent 名称 / 描述..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={handleSearch}
          onBlur={handleSearch}
          style={{ width: 320, height: 36, borderRadius: 8 }}
        />
      </div>

      {/* 表格 */}
      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
          background: '#fff',
        }}
      >
        <Table<AgentVO>
          rowKey="num"
          columns={columns}
          dataSource={data}
          loading={false}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => {
              setPageNo(page);
              setPageSize(size);
            },
          }}
          size="middle"
          style={{ background: '#fff' }}
          rowClassName={() => 'agent-list-row'}
          scroll={{ x: 'max-content' }}
        />
      </div>

      {/* 内联样式：扁平化表头/行（避开新增 less 文件，控制在本组件） */}
      <style>{`
        .agent-list-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
          white-space: nowrap;
        }
        .ant-table-thead > tr > th {
          background: ${COLOR.headerBg} !important;
          color: ${COLOR.textMuted} !important;
          font-size: 11px !important;
          font-weight: 700 !important;
          letter-spacing: 0.06em !important;
          text-transform: uppercase;
          padding: 10px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
        }
        .ant-table-thead > tr > th::before {
          display: none !important;
        }
      `}</style>

      {/* A2A 模式 Drawer（仅「继续接入草稿」场景使用；新建入口已并入整页编辑器） */}
      <Drawer
        title={a2aDraftAgentNum ? '继续接入 A2A Agent' : '新建 Agent · A2A 接入'}
        placement="right"
        width={560}
        open={a2aDrawerOpen}
        onClose={() => setA2aDrawerOpen(false)}
        destroyOnHidden
      >
        <A2aCreateForm
          draftAgentNum={a2aDraftAgentNum}
          initialValues={a2aInitialValues}
          onDone={(detail) => {
            setA2aDrawerOpen(false);
            refetchAgents();
            if (detail?.num) {
              navigate(`/agent/manage/detail/${detail.num}`);
            }
          }}
        />
      </Drawer>
    </div>
  );
}
