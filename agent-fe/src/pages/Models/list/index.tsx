/**
 * 模型列表页(支持空间/系统两种 scope,2026-06-17 scope 优化)
 *
 * - scope=SPACE(默认):走 `/api/v1/model/*`,查询当前空间模型;API Key 脱敏展示
 * - scope=PLATFORM:走 `/api/v1/system/model/*`,查询系统模型;API Key 列对系统模型不展示(后端 PLATFORM 不组装脱敏串)
 * - 列表新增 scope Tag(系统/空间);其余三态操作矩阵不变
 * - antd Table 套用 Sandbox / Skill 列表工程风格
 */
import { useMemo, useState } from 'react';
import { Button, Empty, Input, Modal, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import {
  useModelPageQuery,
  useModelDeleteMutation,
  useModelEnableMutation,
  useModelDisableMutation,
  useSystemModelPageQuery,
  useSystemModelDeleteMutation,
  useSystemModelEnableMutation,
  useSystemModelDisableMutation,
} from '@/services/model';
import type { ModelPageQueryParam, ModelScope, ModelStatus, ModelVO } from '@/types';
import { MODEL_SCOPE_META, MODEL_STATUS_META } from '../constants';
import ModelFormDrawer from './ModelFormDrawer';
import ModelDetailDrawer from './ModelDetailDrawer';
import PermissionGate from '@/components/PermissionGate';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  headerBg: '#ffffff',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

const MONO_FONT = 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace';

interface ModelListPageProps {
  /** 归属范围;默认 SPACE(空间模型) */
  scope?: ModelScope;
}

export default function ModelListPage({ scope = 'SPACE' }: ModelListPageProps) {
  const isSystem = scope === 'PLATFORM';

  // 权限码前缀随 scope 切换：空间模型用 model:*，系统模型用 system:model_*
  const P = isSystem
    ? { create: 'system:model_create', update: 'system:model_update', delete: 'system:model_delete' }
    : { create: 'model:create',        update: 'model:update',        delete: 'model:delete' };

  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState<string>('');
  const [keywordInput, setKeywordInput] = useState<string>('');

  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<ModelVO | undefined>();
  const [detailNum, setDetailNum] = useState<string | undefined>();

  const query: ModelPageQueryParam = useMemo(
    () => ({ pageNo, pageSize, keyword: keyword || undefined }),
    [pageNo, pageSize, keyword]
  );

  // 按 scope 切换查询 / 写 hooks，用 enabled 避免触发非当前 scope 的接口请求
  const spacePage = useModelPageQuery(query, !isSystem);
  const systemPage = useSystemModelPageQuery(query, isSystem);
  const page = isSystem ? systemPage.data : spacePage.data;
  const isFetching = isSystem ? systemPage.isFetching : spacePage.isFetching;

  const enableMut = isSystem ? useSystemModelEnableMutation() : useModelEnableMutation();
  const disableMut = isSystem ? useSystemModelDisableMutation() : useModelDisableMutation();
  const deleteMut = isSystem ? useSystemModelDeleteMutation() : useModelDeleteMutation();

  const list = page?.list ?? [];
  const total = page?.total ?? 0;

  const openCreate = () => {
    setEditTarget(undefined);
    setFormOpen(true);
  };
  const openEdit = (r: ModelVO) => {
    setEditTarget(r);
    setFormOpen(true);
  };

  const doSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const handleEnable = async (r: ModelVO) => {
    await enableMut.mutateAsync({ num: r.num });
    message.success('已启用');
  };

  const handleDisable = (r: ModelVO) => {
    Modal.confirm({
      title: '禁用模型',
      content: `确认禁用「${r.name}」?禁用后该模型将不可被引用。`,
      okText: '禁用',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await disableMut.mutateAsync({ num: r.num });
        message.success('已禁用');
      },
    });
  };

  const handleDelete = (r: ModelVO) => {
    Modal.confirm({
      title: '删除模型',
      content: `确认删除草稿「${r.name}」?该操作不可恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await deleteMut.mutateAsync({ num: r.num });
        message.success('已删除');
      },
    });
  };

  const columns: TableColumnsType<ModelVO> = useMemo(
    () => [
      {
        title: '编号',
        dataIndex: 'num',
        key: 'num',
        width: 180,
        fixed: 'left',
        render: (num: string, r: ModelVO) => (
          <a
            onClick={() => setDetailNum(r.num)}
            style={{ fontFamily: MONO_FONT, fontSize: 13, color: COLOR.textPrimary, whiteSpace: 'nowrap' }}
          >
            {num}
          </a>
        ),
      },
      {
        title: '归属',
        dataIndex: 'scope',
        key: 'scope',
        width: 80,
        render: (s?: ModelScope) => {
          const meta = MODEL_SCOPE_META[s ?? 'SPACE'];
          return <Tag color={meta.tagColor}>{meta.label}</Tag>;
        },
      },
      {
        title: '名称',
        dataIndex: 'name',
        key: 'name',
        width: 160,
        render: (name: string) => (
          <Text style={{ color: COLOR.textPrimary, fontWeight: 500 }}>{name}</Text>
        ),
      },
      {
        title: '模型标识',
        dataIndex: 'modelId',
        key: 'modelId',
        width: 160,
        render: (modelId: string) => (
          <Text style={{ fontFamily: MONO_FONT, fontSize: 13 }}>{modelId}</Text>
        ),
      },
      {
        title: 'API Key',
        dataIndex: 'apiKeyMasked',
        key: 'apiKeyMasked',
        width: 140,
        render: (masked?: string) => (
          <Text style={{ fontFamily: MONO_FONT, color: COLOR.textMuted }}>{masked || '—'}</Text>
        ),
      },
      {
        title: 'Base URL',
        dataIndex: 'baseUrl',
        key: 'baseUrl',
        width: 220,
        ellipsis: true,
        render: (url: string) => <Text style={{ color: COLOR.textSecondary }}>{url}</Text>,
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 90,
        render: (st: ModelStatus) => {
          const meta = MODEL_STATUS_META[st] ?? { color: COLOR.textMuted, label: st ?? '-' };
          return (
            <span style={{ color: meta.color, fontSize: 12, fontWeight: 500, whiteSpace: 'nowrap' }}>
              ● {meta.label}
            </span>
          );
        },
      },
      {
        title: '备注',
        dataIndex: 'remark',
        key: 'remark',
        ellipsis: true,
        render: (remark?: string) => (
          <Text style={{ color: COLOR.textSecondary }}>{remark || '—'}</Text>
        ),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        key: 'updateTime',
        width: 170,
        render: (t: string) => <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>{t}</Text>,
      },
      {
        title: '操作',
        key: 'action',
        width: 200,
        fixed: 'right',
        render: (_: unknown, r: ModelVO) => (
          <Space size={12} wrap>
            <a onClick={() => setDetailNum(r.num)}>详情</a>
            <PermissionGate anyOf={[P.update]}>
              <a onClick={() => openEdit(r)}>编辑</a>
            </PermissionGate>
            {r.status === 'DRAFT' || r.status === 'DISABLED' ? (
              <PermissionGate anyOf={[P.update]}>
                <a onClick={() => handleEnable(r)}>启用</a>
              </PermissionGate>
            ) : null}
            {r.status === 'ENABLED' ? (
              <PermissionGate anyOf={[P.update]}>
                <a onClick={() => handleDisable(r)}>禁用</a>
              </PermissionGate>
            ) : null}
            {r.status === 'DRAFT' ? (
              <PermissionGate anyOf={[P.delete]}>
                <a style={{ color: '#DC2626' }} onClick={() => handleDelete(r)}>
                  删除
                </a>
              </PermissionGate>
            ) : (
              <Tooltip title='仅草稿状态可删除'>
                <span style={{ color: COLOR.textMuted, cursor: 'not-allowed' }}>删除</span>
              </Tooltip>
            )}
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isSystem]
  );

  const pageTitle = '模型管理';
  const pageDesc = isSystem
    ? '纳管全平台共享的系统 LLM 模型;草稿 → 启用 ⇄ 禁用,API Key 加密托管且前端不展示'
    : '纳管 LLM 模型接入;草稿 → 启用 ⇄ 禁用 生命周期,API Key 加密托管,供 Agent 与调试台引用';

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
          <Title level={2} style={{ margin: 0, color: COLOR.textPrimary, fontSize: 24, fontWeight: 700 }}>
            {pageTitle}
          </Title>
          <Text style={{ color: COLOR.textSecondary, fontSize: 14, marginTop: 4, display: 'block' }}>
            {pageDesc}
          </Text>
        </div>
        <PermissionGate anyOf={[P.create]}>
          <Button type='primary' icon={<PlusOutlined />} onClick={openCreate}>
            新建模型
          </Button>
        </PermissionGate>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', marginBottom: 16 }}>
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder='搜索 编号 / 名称 / 模型标识 / 备注…'
          value={keywordInput}
          onChange={e => setKeywordInput(e.target.value)}
          onPressEnter={doSearch}
          onBlur={doSearch}
          style={{ width: 320 }}
        />
      </div>

      <div style={{ border: `1px solid ${COLOR.border}`, borderRadius: 8, overflow: 'hidden', background: '#fff' }}>
        <Table<ModelVO>
          rowKey='num'
          columns={columns}
          dataSource={list}
          loading={isFetching}
          size='middle'
          scroll={{ x: 1360 }}
          locale={{ emptyText: <Empty description='还没有模型' style={{ padding: 32 }} /> }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: t => `共 ${t} 条`,
            onChange: (p, ps) => {
              setPageNo(p);
              setPageSize(ps);
            },
          }}
          rowClassName={() => 'model-list-row'}
        />
      </div>

      <ModelFormDrawer
        scope={scope}
        open={formOpen}
        target={editTarget}
        onClose={() => setFormOpen(false)}
        onSaved={() => setFormOpen(false)}
      />
      <ModelDetailDrawer
        scope={scope}
        num={detailNum}
        open={!!detailNum}
        onClose={() => setDetailNum(undefined)}
      />

      <style>{`
        .model-list-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
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
    </div>
  );
}
