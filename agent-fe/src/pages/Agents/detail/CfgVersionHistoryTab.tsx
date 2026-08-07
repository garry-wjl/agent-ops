/**
 * CONFIG 模式版本管理 Tab — v3.0 全量重写
 *
 * 数据来源：单一接口 GET /api/v1/agents/versions
 * 行类型由 status 决定：
 *   · DRAFT     —— 草稿（最多 1 行）；操作 [编辑] / [发布] / [删除]
 *   · PUBLISHED —— 已发布；current=true 视为「在线」；操作 [查看 snapshot]（在线行只读）
 *   · ARCHIVED  —— 历史归档；操作 [查看 snapshot] / [对比 M3] / [回滚 M3]
 *
 * 头部右上角：[+ 创建版本] —— 走 POST /version/create；后端约束每 Agent 只能有 1 份 DRAFT。
 *
 * 父组件协作：
 *   · onPublish(versionId)  —— 由父组件展示 PublishModal 并发起 /publish
 *   · onEditDraft(versionId)—— 由父组件展示 5 步表单 Drawer 编辑该 DRAFT
 */
import { useMemo, useState } from 'react';
import {
  Button,
  Empty,
  Modal,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import {
  agentApi,
  useAgentVersionListQuery,
  useCreateVersionMutation,
  useDeleteDraftVersionMutation,
} from '@/services/agent';
import type {
  AgentVersionDetailVO,
  AgentVersionVO,
} from '@/types';
import { formatTime, prettyJson } from '@/utils/format';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

export interface CfgVersionHistoryTabProps {
  agentNum: string;
  /** 父组件触发发布 Modal 的回调，接 DRAFT 版本的 versionId */
  onPublish: (versionId: string) => void;
  /** 父组件触发编辑 Drawer 的回调，接 DRAFT 版本的 versionId */
  onEditDraft: (versionId: string) => void;
}

export default function CfgVersionHistoryTab({
  agentNum,
  onPublish,
  onEditDraft,
}: CfgVersionHistoryTabProps) {
  const {
    data: versions,
    isLoading,
    refetch,
  } = useAgentVersionListQuery(agentNum);
  const createVersionMutation = useCreateVersionMutation();
  const deleteDraftMutation = useDeleteDraftVersionMutation();

  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotVO, setSnapshotVO] = useState<AgentVersionDetailVO | null>(null);

  /** [+ 创建版本] —— 后端冲突时返回 4xxx 业务错误，axios 拦截器已 toast；前端再补一层 message.warning */
  const handleCreateVersion = async () => {
    try {
      await createVersionMutation.mutateAsync(agentNum);
      message.success('已创建草稿版本');
      refetch();
    } catch (err: any) {
      // 拦截器已 toast；这里仅在文案中包含「草稿」/ 4xxx 时再提示一次以提升可见性
      const msg: string =
        err?.response?.data?.message || err?.message || '创建草稿失败';
      if (/draft|草稿/i.test(msg)) {
        message.warning('当前 Agent 已存在一份草稿，请先编辑或删除后再创建');
      }
    }
  };

  /** [查看 snapshot] —— 优先用行内 configSnapshot；回退到 versionDetail 接口 */
  const handleViewSnapshot = async (row: AgentVersionVO) => {
    if (row.configSnapshot) {
      setSnapshotVO({
        ...row,
        configSnapshot: row.configSnapshot,
      } as AgentVersionDetailVO);
      setSnapshotOpen(true);
      return;
    }
    if (!row.versionNum) {
      message.info('该行暂无 snapshot 信息');
      return;
    }
    try {
      const detail = await agentApi.versionDetail(agentNum, row.versionNum);
      setSnapshotVO(detail);
      setSnapshotOpen(true);
    } catch {
      // 拦截器已 toast
    }
  };

  /** [删除] —— DRAFT 行专属 */
  const handleDeleteDraft = (versionId: string) => {
    Modal.confirm({
      title: '删除草稿？',
      content: '草稿删除后无法恢复，将从版本管理列表中移除。',
      okType: 'danger',
      okText: '删除',
      onOk: async () => {
        try {
          await deleteDraftMutation.mutateAsync(versionId);
          message.success('草稿已删除');
          refetch();
        } catch {
          // 拦截器已 toast
        }
      },
    });
  };

  const handleRollback = () => message.info('版本回滚功能将在 M3 上线');

  /** 排序：DRAFT 永远置顶；其后 current=true（在线）；再后按 publishedAt 倒序 */
  const rows: AgentVersionVO[] = useMemo(() => {
    const list = [...(versions ?? [])];
    return list.sort((a, b) => {
      if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
      if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
      if (a.current && !b.current) return -1;
      if (b.current && !a.current) return 1;
      const ta = a.publishedAt ? new Date(a.publishedAt).getTime() : 0;
      const tb = b.publishedAt ? new Date(b.publishedAt).getTime() : 0;
      return tb - ta;
    });
  }, [versions]);

  const columns: TableColumnsType<AgentVersionVO> = [
    {
      title: '版本号',
      key: 'version',
      width: 200,
      render: (_, r) => {
        const text =
          r.status === 'DRAFT'
            ? '草稿（未发布）'
            : r.versionNum || r.version || '-';
        return (
          <span
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
              fontWeight:
                r.status === 'DRAFT' || r.current ? 600 : 400,
            }}
          >
            {text}
          </span>
        );
      },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      ellipsis: true,
      render: (txt?: string) =>
        txt?.trim() ? (
          <Tooltip title={txt} placement="topLeft">
            <span style={{ color: COLOR.textSecondary }}>{txt}</span>
          </Tooltip>
        ) : (
          <span style={{ color: COLOR.textMuted }}>-</span>
        ),
    },
    {
      title: '发布人 / 编辑者',
      key: 'who',
      width: 140,
      render: (_, r) => (
        <span style={{ color: COLOR.textSecondary }}>
          {r.status === 'DRAFT'
            ? r.editorUserId || '-'
            : r.publishedBy || '-'}
        </span>
      ),
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 170,
      render: (t?: string) => (
        <span style={{ color: COLOR.textSecondary, fontSize: 13 }}>
          {t ? (
            formatTime(t)
          ) : (
            <span style={{ color: COLOR.textMuted }}>-</span>
          )}
        </span>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_, r) => {
        if (r.status === 'DRAFT') return <Tag color="warning">草稿</Tag>;
        if (r.status === 'PUBLISHED' && r.current)
          return <Tag color="success">在线</Tag>;
        return <Tag>历史</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, r) => {
        if (r.status === 'DRAFT') {
          return (
            <Space size={12}>
              <a onClick={() => onEditDraft(r.num)}>编辑</a>
              <a onClick={() => onPublish(r.num)}>发布</a>
              <a onClick={() => handleDeleteDraft(r.num)}>删除</a>
            </Space>
          );
        }
        if (r.status === 'PUBLISHED' && r.current) {
          return (
            <Space size={12}>
              <a onClick={() => handleViewSnapshot(r)}>查看 snapshot</a>
            </Space>
          );
        }
        // ARCHIVED 或 PUBLISHED current=false（历史）
        return (
          <Space size={12}>
            <a onClick={() => handleViewSnapshot(r)}>查看 snapshot</a>
            <a onClick={handleRollback}>回滚</a>
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* Tab 头：右上角 [+ 创建版本] */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
        }}
      >
        <Button
          type="primary"
          icon={<PlusOutlined />}
          loading={createVersionMutation.isPending}
          onClick={handleCreateVersion}
        >
          创建版本
        </Button>
      </div>

      {!isLoading && rows.length === 0 ? (
        <Empty description="暂无版本（点 [创建版本] 生成首份草稿）" />
      ) : (
        <div
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            overflow: 'hidden',
          }}
        >
          <Table<AgentVersionVO>
            rowKey="num"
            loading={isLoading}
            columns={columns}
            dataSource={rows}
            pagination={false}
            size="middle"
          />
        </div>
      )}

      <Modal
        open={snapshotOpen}
        title={
          snapshotVO
            ? `Config Snapshot · ${
                snapshotVO.status === 'DRAFT'
                  ? '草稿'
                  : snapshotVO.versionNum || snapshotVO.version || ''
              }`
            : 'Config Snapshot'
        }
        width={760}
        footer={<Button onClick={() => setSnapshotOpen(false)}>关闭</Button>}
        onCancel={() => setSnapshotOpen(false)}
        destroyOnHidden
      >
        {snapshotVO?.configSnapshot ? (
          <SyntaxHighlighter
            language="json"
            customStyle={{
              margin: 0,
              padding: 16,
              background: '#F8FAFC',
              fontSize: 12,
              maxHeight: '60vh',
              overflow: 'auto',
              borderRadius: 6,
            }}
          >
            {prettyJson(snapshotVO.configSnapshot)}
          </SyntaxHighlighter>
        ) : (
          <Empty description="该版本无 snapshot" />
        )}
        {snapshotVO && snapshotVO.status !== 'DRAFT' ? (
          <div style={{ marginTop: 12, fontSize: 12, color: COLOR.textMuted }}>
            发布人：{snapshotVO.publishedBy ?? '-'} · 发布时间：
            {snapshotVO.publishedAt ? formatTime(snapshotVO.publishedAt) : '-'}{' '}
            · 备注：{snapshotVO.remark || '-'}
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
