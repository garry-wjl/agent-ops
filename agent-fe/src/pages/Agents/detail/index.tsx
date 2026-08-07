/**
 * Agent 详情页 — `/agent/manage/detail/:num`
 *
 * v3.0 改造：
 * - 顶部按钮极简：只保留 [调试] / [上线] / [⋯ 更多: 下线 / 删除]
 *   · [+ 创建版本] / [发布] 全部下沉到「版本管理」Tab，由 CfgVersionHistoryTab 负责
 *   · 创建版本二次确认 Modal 移除（创建版本即一次性 POST /version/create）
 * - 编辑草稿 Drawer 与发布 Modal 仍由 detail 持有，由 Tab 通过 onEditDraft / onPublish 回调触发
 * - CONFIG 模式 7 Tab：基本信息 / 模型配置 / Skill 配置 / MCP 配置 / 高级配置 / API 信息 / 版本管理
 * - A2A    模式 5 Tab：基本信息 / Skills / MCP / API 信息 / 版本历史
 * - A2A 顶部按状态分支：
 *     · DRAFT_ONLY        : [📝 继续接入] / [🗑 删除草稿]
 *     · PENDING_SYNC      : [调试 disabled] / [↻ 手动重新同步] / [🚫 取消订阅] + 橙色 Banner
 *     · PUBLISHED/OFFLINE : [调试] / [↻ 手动重新同步] / [🚫 取消订阅]
 *
 * Tab 内容除 BasicTab/A2aBasicTab 外的明细 Tab 暂以骨架/占位实现，依托原有数据源。
 */
import { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Space,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ArrowRightOutlined,
  CopyOutlined,
  EditOutlined,
  FileTextOutlined,
  ReloadOutlined,
  RightOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import {
  useA2aManualResyncMutation,
  useA2aUnsubscribeMutation,
  useAgentDetailQuery,
  useAgentOfflineMutation,
  useAgentPublishMutation,
} from '@/services/agent';
import { skillApi, skillQueryKeys } from '@/services/skill';
import { useModelSelectableQuery } from '@/services/model';
import { useToolPageQuery } from '@/services/tool';
import { useSandboxPageQuery } from '@/services/sandbox';
import { useQueries } from '@tanstack/react-query';
import type {
  A2aSourceVO,
  AgentDetailVO,
  ModelSelectableVO,
  RemoteMcp,
  RemoteSkill,
  SandboxVO,
  SkillVO,
  ToolVO,
} from '@/types';
import { formatTime, prettyJson } from '@/utils/format';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import ApiInfoTab from './ApiInfoTab';
import ApiKeyTab from './ApiKeyTab';
import CfgVersionHistoryTab from './CfgVersionHistoryTab';
import A2aVersionHistoryTab from './A2aVersionHistoryTab';
import SessionHistoryTab from './SessionHistoryTab';
import { ModelMetaCard } from '../components/EditorShared';

const COLOR = {
  border: '#E2E8F0',
  divider: '#E5E7EB',
  textPrimary: '#0F172B',
  textBody: '#1D293D',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  primaryDeep: '#2563EB',
  bgInfo: '#EFF6FF',
  borderInfo: '#DBEAFE',
  bgTag: '#F1F5F9',
  bgPanel: '#fff',
  // PENDING_SYNC banner（橙色）
  bgPending: '#FFF7ED',
  borderPending: '#FED7AA',
  textPending: '#EA580C',
} as const;

/** v2.6 Tab 集 */
type CfgTabKey =
  | 'basic'
  | 'modelCfg'
  | 'skillCfg'
  | 'mcpCfg'
  | 'sandboxCfg'
  | 'advancedCfg'
  | 'apiInfo'
  | 'apiKey'
  | 'versions'
  | 'sessions';
type A2aTabKey = 'basic' | 'skills' | 'mcp' | 'apiInfo' | 'apiKey' | 'versions' | 'sessions';
type TabKey = CfgTabKey | A2aTabKey;

const { Paragraph } = Typography;

export default function AgentDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const [searchParams] = useSearchParams();
  const num = params.num!;
  const { data: detail, refetch: refetchDetail } = useAgentDetailQuery(num);
  useBreadcrumbName(detail?.name);
  const [activeTab, setActiveTab] = useState<TabKey>(
    (searchParams.get('tab') as TabKey | null) ?? 'basic',
  );
  const [publishOpen, setPublishOpen] = useState(false);
  /** v3.0：版本管理 Tab 触发的发布 — 当前正在发布的 DRAFT versionId */
  const [publishingVersionId, setPublishingVersionId] = useState<string | undefined>();
  const [agentCardOpen, setAgentCardOpen] = useState(false);
  const [resyncing, setResyncing] = useState(false);
  const [form] = Form.useForm();

  const offlineMutation = useAgentOfflineMutation();
  const a2aResyncMutation = useA2aManualResyncMutation();
  const a2aUnsubscribeMutation = useA2aUnsubscribeMutation();
  const publishMutation = useAgentPublishMutation();

  const isA2A = detail?.creationMode === 'A2A';

  // 2026-06-11 配置优化：CONFIG 模式详情按引用反查资产元信息展示。
  // 2026-06-17 scope 优化：模型改用 selectable（系统启用 + 当前空间启用合集，不含 Key）。
  const { data: selectableModels } = useModelSelectableQuery();
  const { data: toolPage } = useToolPageQuery({
    pageNo: 1,
    pageSize: 200,
    status: 'PUBLISHED',
  });
  const { data: sandboxPage } = useSandboxPageQuery({
    pageNo: 1,
    pageSize: 200,
    status: 'ONLINE',
  });
  const models: ModelSelectableVO[] = selectableModels ?? [];
  const tools: ToolVO[] = toolPage?.list ?? [];
  const sandboxes: SandboxVO[] = sandboxPage?.list ?? [];

  // CONFIG 模式：按 skillNums 拉取 Skill 详情；A2A 模式不走 Skill 详情接口。
  // 注意：保持与原实现一致的 hook-in-map 模式（同一 detail 内 skillNums 列表稳定，
  // React 18 实际可工作；后续 v2.x 迁移到批量接口时再修正）。
  // CONFIG 模式:按 skillNums 拉取 Skill 详情;A2A 模式不走 Skill 详情接口。
  // 使用 useQueries 批量(不能在 map 里调 useSkillDetailQuery — 违反 Rules of Hooks,
  // 数组长度变化会让 react-query 内部 cache 状态崩 'Cannot read properties of undefined')
  const skillNums = (
    !isA2A ? detail?.currentVersion?.configSnapshot?.skillNums : undefined
  ) ?? [];
  const skillsResults = useQueries({
    queries: skillNums.map((sn: string) => ({
      queryKey: skillQueryKeys.detail(sn),
      queryFn: () => skillApi.detail(sn),
      enabled: !!sn,
    })),
  });
  // v2.11: SkillApi.detail 返回嵌套结构 { skill, currentVersion, reuseCount },解一层取 skill
  const loadedSkills = skillsResults
    .map((r) => r.data?.skill)
    .filter(Boolean) as SkillVO[];

  const handlePublish = async () => {
    const v = await form.validateFields();
    if (!publishingVersionId) {
      message.error('缺少 versionId，请回到版本管理 Tab 重新点击「发布」');
      return;
    }
    await publishMutation.mutateAsync({
      versionId: publishingVersionId,
      // 兼容期同时带 agentNum，方便 BE 兜底；未来可只传 versionId
      agentNum: num,
      remark: v.remark,
    });
    message.success('已发布新版本');
    setPublishOpen(false);
    setPublishingVersionId(undefined);
    form.resetFields();
    refetchDetail();
  };

  const handleOffline = () => {
    if (!detail) return;
    Modal.confirm({
      title: `下线 ${detail.name}？`,
      content: '下线后调试台与挂载下拉中将不再展示。',
      onOk: async () => {
        await offlineMutation.mutateAsync(num);
        message.success('已下线');
        refetchDetail();
      },
    });
  };

  const handleManualResync = async () => {
    if (!detail) return;
    setResyncing(true);
    try {
      await a2aResyncMutation.mutateAsync(num);
      message.success('已请求重新同步，最近同步时间已刷新');
    } catch {
      message.error('同步失败，请稍后重试');
    } finally {
      setResyncing(false);
    }
  };

  const handleUnsubscribeA2a = () => {
    if (!detail) return;
    Modal.confirm({
      title: `取消订阅 ${detail.name}？`,
      content:
        '取消后该 A2A Agent 将从平台移除，远端 Nacos 注册不受影响。如需重新接入，请回到列表使用「新建 Agent」入口。',
      okType: 'danger',
      okText: '取消订阅',
      onOk: async () => {
        await a2aUnsubscribeMutation.mutateAsync(num);
        message.success('已取消订阅');
        navigate('/agent/manage');
      },
    });
  };

  const handleContinueA2aDraft = () => {
    // 草稿继续接入：直接跳回列表并提示用户在列表里点"继续接入"
    // （Drawer 在列表组件内，详情页直接复用即可）
    navigate(`/agent/manage?continueDraft=${encodeURIComponent(num)}`);
    message.info('已跳回列表，请在该 Agent 行点击"继续接入"');
  };

  const cfgTabs: { key: CfgTabKey; label: string }[] = useMemo(
    () => [
      { key: 'basic', label: '基本信息' },
      { key: 'modelCfg', label: '模型配置' },
      { key: 'skillCfg', label: 'Skill 配置' },
      { key: 'mcpCfg', label: '工具配置' },
      { key: 'sandboxCfg', label: '沙箱配置' },
      { key: 'advancedCfg', label: '记忆配置' },
      { key: 'apiInfo', label: 'API 信息' },
      { key: 'apiKey', label: '秘钥管理' },
      { key: 'sessions', label: '会话历史' },
      { key: 'versions', label: '版本管理' },
    ],
    [],
  );

  const a2aTabs: { key: A2aTabKey; label: string; count?: number }[] =
    useMemo(() => {
      const a2aSource = detail?.a2aSource;
      const remoteSkillCount = a2aSource?.remoteSkills?.length ?? 0;
      const remoteMcpCount = a2aSource?.remoteMcps?.length ?? 0;
      return [
        { key: 'basic', label: '基本信息' },
        { key: 'skills', label: 'Skills', count: remoteSkillCount },
        { key: 'mcp', label: 'MCP', count: remoteMcpCount },
        { key: 'apiInfo', label: 'API 信息' },
        { key: 'apiKey', label: '秘钥管理' },
        { key: 'sessions', label: '会话历史' },
        { key: 'versions', label: '版本历史' },
      ];
    }, [detail?.a2aSource]);

  if (!detail) return <div style={{ padding: 32 }}>加载中...</div>;

  const a2aSource = detail.a2aSource;
  const remoteSkillCount = a2aSource?.remoteSkills?.length ?? 0;
  const remoteMcpCount = a2aSource?.remoteMcps?.length ?? 0;
  const skillCount = isA2A ? remoteSkillCount : loadedSkills.length;
  const isPendingSync = detail.status === 'PENDING_SYNC';
  const isDraftOnly = detail.status === 'DRAFT_ONLY';

  // 2026-06-11 配置优化：CONFIG 顶部指标按 modelId 反查模型名称展示。
  const cfgSnapshot = !isA2A ? detail.currentVersion?.configSnapshot : undefined;
  const resolvedModelName = cfgSnapshot?.modelId
    ? (models.find((m) => m.num === cfgSnapshot.modelId)?.name ??
        cfgSnapshot.modelId)
    : undefined;
  const toolCount = cfgSnapshot?.toolNums?.length ?? 0;

  const tabs = isA2A ? a2aTabs : cfgTabs;
  const serviceKey = a2aSource
    ? `${a2aSource.nacosGroup}@@${a2aSource.nacosService}`
    : '';

  return (
    <div
      style={{
        padding: '32px',
        background: '#fff',
        minHeight: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
      }}
    >
      {/* 面包屑 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 12,
          color: COLOR.textMuted,
        }}
      >
        <a
          onClick={() => navigate('/agent/manage')}
          style={{ color: COLOR.textMuted }}
        >
          智能体管理
        </a>
        <RightOutlined style={{ fontSize: 9 }} />
        <span style={{ color: COLOR.textBody }}>{detail.name}</span>
      </div>

      {/* PENDING_SYNC：橙色 Banner */}
      {isA2A && isPendingSync && (
        <div
          style={{
            background: COLOR.bgPending,
            border: `1px solid ${COLOR.borderPending}`,
            color: COLOR.textPending,
            borderRadius: 8,
            padding: '10px 14px',
            fontSize: 13,
          }}
        >
          ⏳ 等待 Nacos 首次推送 AgentCard…接入已成功，远端首次同步完成后将进入「在线」状态。
        </div>
      )}

      {/* A2A 在线 / 已下线：保留只读 Banner */}
      {isA2A && !isPendingSync && a2aSource && (
        <Alert
          type="warning"
          showIcon
          message={
            <span>
              🔒 该 Agent 来源于 Nacos 注册中心 (<strong>{serviceKey}</strong>)，
              所有信息由远端管理，平台不可修改。最近同步：
              {formatTime(a2aSource.lastSyncedAt) || '-'}
            </span>
          }
          style={{ borderRadius: 8 }}
        />
      )}

      {/* 标题行 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <h1
            style={{
              margin: 0,
              fontSize: 24,
              fontWeight: 700,
              color: COLOR.textPrimary,
            }}
          >
            {detail.name}
          </h1>
          <StatusChip status={detail.status} hasDraft={!isA2A && detail.hasDraft} />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {/* 公共：调试 */}
          <Tooltip
            title={isPendingSync ? '等待首次同步完成后再调试' : undefined}
          >
            <Button
              type="primary"
              icon={<ArrowRightOutlined />}
              disabled={isPendingSync}
              onClick={() =>
                navigate(`/agent/debug?agent=${encodeURIComponent(num)}`)
              }
              style={{
                background: isPendingSync ? undefined : COLOR.primary,
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              调试
            </Button>
          </Tooltip>

          {isA2A ? (
            isDraftOnly ? (
              <>
                <Button
                  icon={<EditOutlined />}
                  onClick={handleContinueA2aDraft}
                >
                  继续接入
                </Button>
                <Button danger onClick={handleUnsubscribeA2a}>
                  删除草稿
                </Button>
              </>
            ) : (
              <>
                <Button
                  icon={<ReloadOutlined />}
                  loading={resyncing}
                  onClick={handleManualResync}
                >
                  手动重新同步
                </Button>
                <Button
                  danger
                  icon={<StopOutlined />}
                  onClick={handleUnsubscribeA2a}
                >
                  取消订阅
                </Button>
              </>
            )
          ) : (
            <>
              {/* v3.0：CONFIG 顶部仅保留「下线」操作；[+ 创建版本] / [发布] 已下沉到版本管理 Tab */}
              {detail.status === 'PUBLISHED' && (
                <Button danger onClick={handleOffline}>
                  下线
                </Button>
              )}
            </>
          )}
        </div>
      </div>

      {/* 指标行 */}
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        {isA2A ? (
          <>
            <Metric label="creation" value="A2A · Nacos" />
            {a2aSource?.remoteVersion ? (
              <Metric label="remote version" value={a2aSource.remoteVersion} />
            ) : null}
            <Metric label="skills" value={String(skillCount)} />
            <Metric label="mcps" value={String(remoteMcpCount)} />
          </>
        ) : (
          <>
            <Metric label="num" value={detail.num} mono />
            <Metric
              label="version"
              value={
                detail.currentVersionNum
                  ? `v${String(detail.currentVersionNum).replace(/^v/, '')}`
                  : '-'
              }
            />
            <Metric label="skills" value={String(skillCount)} />
            <Metric label="tools" value={String(toolCount)} />
            {resolvedModelName ? (
              <Metric label="model" value={resolvedModelName} />
            ) : null}
          </>
        )}
      </div>

      {/* Tab 栏 */}
      <div
        style={{
          display: 'flex',
          gap: 0,
          borderBottom: `1px solid ${COLOR.border}`,
        }}
      >
        {tabs.map((t) => {
          const active = (t.key as TabKey) === activeTab;
          return (
            <button
              key={t.key}
              onClick={() => setActiveTab(t.key as TabKey)}
              style={{
                padding: '12px 16px',
                background: 'none',
                border: 'none',
                borderBottom: `2px solid ${
                  active ? COLOR.primary : 'transparent'
                }`,
                color: active ? COLOR.textPrimary : COLOR.textSecondary,
                fontWeight: active ? 500 : 400,
                fontSize: 14,
                cursor: 'pointer',
                marginBottom: -1,
              }}
            >
              {t.label}
              {'count' in t && typeof t.count === 'number'
                ? ` (${t.count})`
                : ''}
            </button>
          );
        })}
      </div>

      {/* Tab 内容 */}
      <div style={{ paddingTop: 8 }}>
        {/* 基本信息 */}
        {activeTab === 'basic' &&
          (isA2A ? (
            <A2aBasicTab
              detail={detail}
              onViewAgentCard={() => setAgentCardOpen(true)}
            />
          ) : (
            <BasicTab detail={detail} />
          ))}

        {/* CONFIG：模型 / Skill / 工具 / 沙箱 / 记忆 */}
        {!isA2A && activeTab === 'modelCfg' && (
          <ModelCfgTab detail={detail} models={models} />
        )}
        {!isA2A && activeTab === 'skillCfg' && (
          <SkillsTab skills={loadedSkills} />
        )}
        {!isA2A && activeTab === 'mcpCfg' && (
          <ToolCfgTab detail={detail} tools={tools} />
        )}
        {!isA2A && activeTab === 'sandboxCfg' && (
          <SandboxCfgTab detail={detail} sandboxes={sandboxes} />
        )}
        {!isA2A && activeTab === 'advancedCfg' && (
          <AdvancedCfgTab detail={detail} />
        )}

        {/* A2A：Skills / MCP */}
        {isA2A && activeTab === 'skills' && (
          <RemoteSkillsTab skills={a2aSource?.remoteSkills ?? []} />
        )}
        {isA2A && activeTab === 'mcp' && (
          <McpTab mcps={a2aSource?.remoteMcps ?? []} />
        )}

        {/* 公共：API 信息 */}
        {activeTab === 'apiInfo' && <ApiInfoTab agentNum={num} />}

        {/* 公共：秘钥管理（对外调用 Bearer 秘钥）；CONFIG / A2A 均支持 */}
        {activeTab === 'apiKey' && <ApiKeyTab agentNum={num} />}

        {/* 公共：版本历史 */}
        {activeTab === 'versions' &&
          (isA2A ? (
            <A2aVersionHistoryTab agentNum={num} />
          ) : (
            <CfgVersionHistoryTab
              agentNum={num}
              onEditDraft={(versionId) => {
                // 2026-06-11 配置优化：编辑草稿改为跳转整页编辑器
                navigate(
                  `/agent/manage/editor/${num}?versionId=${encodeURIComponent(versionId)}`,
                );
              }}
              onPublish={(versionId) => {
                setPublishingVersionId(versionId);
                setPublishOpen(true);
              }}
            />
          ))}

        {/* 会话历史（CONFIG + A2A 均支持） */}
        {activeTab === 'sessions' && <SessionHistoryTab agentNum={num} />}
      </div>

      {/* 发布 Modal（仅 CONFIG）— 由版本管理 Tab 通过 onPublish(versionId) 触发 */}
      {!isA2A && (
        <Modal
          open={publishOpen}
          title="发布新版本"
          onOk={handlePublish}
          onCancel={() => {
            setPublishOpen(false);
            setPublishingVersionId(undefined);
          }}
        >
          <Form form={form} layout="vertical">
            <Form.Item
              label="发布备注（≥ 10 字）"
              name="remark"
              rules={[
                { required: true, message: '必填' },
                { min: 10, message: '至少 10 字' },
              ]}
            >
              <Input.TextArea rows={3} placeholder="说明本次变更内容" />
            </Form.Item>
          </Form>
        </Modal>
      )}

      {/* 原始 Agent Card 查看 Modal（仅 A2A） */}
      {isA2A && (
        <Modal
          open={agentCardOpen}
          title="原始 Agent Card"
          width={760}
          footer={null}
          onCancel={() => setAgentCardOpen(false)}
          destroyOnHidden
        >
          {a2aSource?.agentCardJson ? (
            <SyntaxHighlighter
              language="json"
              customStyle={{
                margin: 0,
                padding: 16,
                background: '#F8FAFC',
                fontSize: 12,
                maxHeight: '60vh',
                overflow: 'auto',
              }}
            >
              {safePrettyJson(a2aSource.agentCardJson)}
            </SyntaxHighlighter>
          ) : (
            <Empty description="远端未提供 Agent Card 原文" />
          )}
        </Modal>
      )}
    </div>
  );
}

/** 尝试将 JSON 字符串美化输出；失败则原样返回 */
function safePrettyJson(text: string): string {
  try {
    return prettyJson(JSON.parse(text));
  } catch {
    return text;
  }
}

/* =================== sub-components =================== */

function StatusChip(props: { status: string; hasDraft?: boolean }) {
  const { status, hasDraft } = props;
  const map: Record<
    string,
    { label: string; bg: string; border: string; color: string }
  > = {
    PUBLISHED: {
      label: '● 运行中',
      bg: COLOR.bgInfo,
      border: COLOR.borderInfo,
      color: COLOR.primary,
    },
    OFFLINE: {
      label: '● 已下线',
      bg: '#FEF2F2',
      border: '#FECACA',
      color: '#DC2626',
    },
    DRAFT_ONLY: {
      label: '● 仅草稿',
      bg: '#FEF3C7',
      border: '#FDE68A',
      color: '#D97706',
    },
    PENDING_SYNC: {
      label: '● 待同步',
      bg: COLOR.bgPending,
      border: COLOR.borderPending,
      color: COLOR.textPending,
    },
  };
  const cfg = map[status] ?? map.DRAFT_ONLY!;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <span
        style={{
          background: cfg.bg,
          border: `1px solid ${cfg.border}`,
          color: cfg.color,
          fontSize: 12,
          fontWeight: 500,
          padding: '3px 10px',
          borderRadius: 999,
        }}
      >
        {cfg.label}
      </span>
      {hasDraft && (
        <span
          style={{
            background: '#FEF3C7',
            border: '1px solid #FDE68A',
            color: '#D97706',
            fontSize: 12,
            fontWeight: 500,
            padding: '3px 10px',
            borderRadius: 999,
          }}
        >
          有草稿
        </span>
      )}
    </div>
  );
}

function Metric(props: {
  label: string;
  value: string;
  mono?: boolean;
  valueColor?: string;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span
        style={{ fontSize: 13, fontWeight: 400, color: COLOR.textMuted }}
      >
        {props.label}
      </span>
      <span
        style={{
          fontSize: 13,
          fontWeight: 500,
          color: props.valueColor ?? '#314158',
          fontFamily: props.mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
        }}
      >
        {props.value}
      </span>
    </div>
  );
}

function BasicTab(props: { detail: AgentDetailVO }) {
  const { detail } = props;
  const cfg = detail.currentVersion?.configSnapshot;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <Field label="名称" value={detail.name ?? '-'} />
      <Field label="描述" value={detail.description ?? '-'} />
      <TagsBlock tags={detail.tags} />
      <Field
        label="Plan 模式"
        value={cfg?.enablePlan ? '已开启' : '关闭'}
      />
      <Field
        label="最大迭代轮次"
        value={cfg?.maxIters != null ? String(cfg.maxIters) : '10'}
      />
      <PromptBlock
        title="系统提示词"
        content={cfg?.systemPrompt?.trim() || '（无系统提示词）'}
      />
      <PromptBlock
        title="用户提示词模板"
        content={cfg?.userPrompt?.trim() || '（无用户提示词）'}
      />
    </div>
  );
}

/** 2026-06-11 配置优化：CONFIG 模式 - 模型配置 Tab（按 modelId 反查模型管理元信息） */
function ModelCfgTab({
  detail,
  models,
}: {
  detail: AgentDetailVO;
  models: ModelSelectableVO[];
}) {
  const cfg = detail.currentVersion?.configSnapshot;
  if (!cfg) return <Empty description="无在线版本" />;
  const model = models.find((m) => m.num === cfg.modelId);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {model ? (
        <ModelMetaCard model={model} />
      ) : cfg.modelId ? (
        <Alert
          type="warning"
          showIcon
          message="关联模型已禁用或删除，请进入编辑重新选择可用模型。"
        />
      ) : (
        <Field label="关联模型" value="-" />
      )}
      <Field
        label="温度"
        value={typeof cfg.temperature === 'number' ? String(cfg.temperature) : '-'}
      />
    </div>
  );
}

/** 2026-06-11 配置优化：CONFIG 模式 - 工具配置 Tab（按 toolNums 反查工具管理） */
function ToolCfgTab({
  detail,
  tools,
}: {
  detail: AgentDetailVO;
  tools: ToolVO[];
}) {
  const cfg = detail.currentVersion?.configSnapshot;
  const toolNums = cfg?.toolNums ?? [];
  if (toolNums.length === 0) {
    return <Empty description="该 Agent 未挂载工具" />;
  }
  const byNum = new Map(tools.map((t) => [t.num, t]));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {toolNums.map((n) => {
        const t = byNum.get(n);
        const invalid = !t;
        return (
          <div
            key={n}
            style={{
              border: `1px solid ${invalid ? '#FECACA' : COLOR.border}`,
              background: invalid ? '#FEF2F2' : '#fff',
              borderRadius: 8,
              padding: '14px 16px',
              display: 'flex',
              flexDirection: 'column',
              gap: 4,
            }}
          >
            <div
              style={{
                fontSize: 14,
                fontWeight: 500,
                color: invalid ? '#DC2626' : COLOR.textPrimary,
              }}
            >
              {t?.name ?? n}
              {t && (
                <Tag
                  color={t.type === 'MCP' ? 'blue' : 'green'}
                  style={{ marginLeft: 8 }}
                >
                  {t.type === 'MCP' ? 'MCP' : 'FunctionCall'}
                </Tag>
              )}
              {invalid && (
                <Tag color="error" style={{ marginLeft: 8 }}>
                  已失效
                </Tag>
              )}
            </div>
            {t?.description && (
              <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                {t.description}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

/** 2026-06-11 配置优化：CONFIG 模式 - 沙箱配置 Tab（按 sandboxRef 反查沙箱管理） */
function SandboxCfgTab({
  detail,
  sandboxes,
}: {
  detail: AgentDetailVO;
  sandboxes: SandboxVO[];
}) {
  const cfg = detail.currentVersion?.configSnapshot;
  const ref = cfg?.sandboxRef;
  if (!ref) return <Empty description="该 Agent 未关联沙箱" />;
  const sb = sandboxes.find((s) => s.num === ref);
  if (!sb) {
    return (
      <Alert
        type="warning"
        showIcon
        message={`关联沙箱（${ref}）已下线或删除，请进入编辑重新选择在线沙箱。`}
      />
    );
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <Field label="沙箱名称" value={sb.name} />
      <Field label="沙箱编号" value={sb.num} mono />
      <Field label="类型" value={sb.type} />
      <Field label="规格" value={`${sb.cpu} 核 / ${sb.memoryMb} MB`} />
      <Field label="状态" value={sb.status} />
    </div>
  );
}

/** v2.6：CONFIG 模式 - 高级配置 Tab */
function AdvancedCfgTab({ detail }: { detail: AgentDetailVO }) {
  const cfg = detail.currentVersion?.configSnapshot;
  if (!cfg) return <Empty description="无在线版本" />;
  const m = cfg.memoryConfig;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <Field
        label="短期记忆策略"
        value={m?.shortTermStrategy ?? (m?.shortTermEnabled ? 'RECENT_N' : 'NONE')}
      />
      {typeof m?.shortTermN === 'number' && (
        <Field label="短期记忆 N" value={String(m.shortTermN)} />
      )}
      <Field
        label="长期记忆策略"
        value={m?.longTermStrategy ?? (m?.longTermEnabled ? 'VECTOR_RECALL' : 'NONE')}
      />
      <Field label="QPS" value={cfg.qps != null ? String(cfg.qps) : '-'} />
      <Field
        label="每日预算"
        value={cfg.dailyBudget != null ? String(cfg.dailyBudget) : '-'}
      />
    </div>
  );
}

/** A2A 模式的「基本信息」Tab：隐藏 Prompt 卡片，展示 A2A 来源信息卡片 */
function A2aBasicTab(props: {
  detail: AgentDetailVO;
  onViewAgentCard: () => void;
}) {
  const { detail, onViewAgentCard } = props;
  const a2a = detail.a2aSource;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <Field label="编码" value={detail.num} mono />
      <Field label="NacosAgent 名称" value={a2a?.nacosService ?? '-'} mono />
      <Field label="Agent 名称" value={detail.name ?? '-'} />
      <Field label="描述" value={detail.description ?? '-'} />
      <TagsBlock tags={detail.tags} />
      {a2a ? (
        <A2aSourceCard a2a={a2a} onViewAgentCard={onViewAgentCard} />
      ) : (
        <Empty description="尚未拉取到 A2A 来源信息" />
      )}
    </div>
  );
}

function A2aSourceCard({
  a2a,
  onViewAgentCard,
}: {
  a2a: A2aSourceVO;
  onViewAgentCard: () => void;
}) {
  return (
    <div
      style={{
        background: COLOR.bgPanel,
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <span
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: COLOR.textPrimary,
          }}
        >
          A2A 来源信息
        </span>
        <Button
          size="small"
          icon={<FileTextOutlined />}
          onClick={onViewAgentCard}
        >
          查看原始 Agent Card
        </Button>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0,1fr))',
          gap: '10px 24px',
        }}
      >
        <KV label="来源服务" value={`${a2a.nacosGroup}@@${a2a.nacosService}`} mono />
        <KV
          label="实例"
          value={
            a2a.instanceIp || a2a.instancePort
              ? `${a2a.instanceIp ?? '-'}:${a2a.instancePort ?? '-'}`
              : '-'
          }
          mono
        />
        <KV label="endpoint" value={a2a.endpointPath ?? '-'} mono />
        <KV label="远端版本号" value={a2a.remoteVersion ?? '-'} />
        <KV
          label="最近同步时间"
          value={formatTime(a2a.lastSyncedAt) || '-'}
        />
        <KV label="同步事件来源" value={a2a.lastSyncEventType ?? '-'} />
      </div>
    </div>
  );
}

/** 标签块：读真实 tags，空时展示占位。 */
function TagsBlock({ tags }: { tags?: string[] }) {
  const list = tags ?? [];
  return (
    <div>
      <div
        style={{
          color: COLOR.textMuted,
          fontSize: 12,
          fontWeight: 500,
          marginBottom: 6,
        }}
      >
        标签
      </div>
      {list.length === 0 ? (
        <span style={{ color: COLOR.textMuted, fontSize: 13 }}>-</span>
      ) : (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {list.map((t, i) => (
            <span
              key={`${t}-${i}`}
              style={{
                background: COLOR.bgTag,
                color: COLOR.textSecondary,
                fontSize: 12,
                padding: '2px 8px',
                borderRadius: 4,
              }}
            >
              {t}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function KV(props: { label: string; value: string; mono?: boolean }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ color: COLOR.textMuted, fontSize: 12 }}>{props.label}</span>
      <span
        style={{
          fontSize: 13,
          color: COLOR.textBody,
          fontFamily: props.mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
          wordBreak: 'break-all',
        }}
      >
        {props.value}
      </span>
    </div>
  );
}

function Field({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ color: COLOR.textMuted, fontSize: 12, fontWeight: 500 }}>
        {label}
      </span>
      <span
        style={{
          fontSize: 14,
          fontWeight: 400,
          color: COLOR.textBody,
          fontFamily: mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
        }}
      >
        {value}
      </span>
    </div>
  );
}

function SkillsTab({ skills }: { skills: SkillVO[] }) {
  if (skills.length === 0) return <Empty description="该 Agent 未挂载 Skill" />;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {skills.map((s) => (
        <div
          key={s.num}
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            padding: '14px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 8,
              background: COLOR.bgInfo,
              color: COLOR.primary,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 14,
              fontWeight: 600,
              flexShrink: 0,
            }}
          >
            {s.name?.slice(0, 2).toUpperCase()}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                fontSize: 14,
                fontWeight: 500,
                color: COLOR.textPrimary,
              }}
            >
              {s.name}
            </div>
            <div
              style={{
                fontSize: 12,
                color: COLOR.textMuted,
                marginTop: 2,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {s.description ?? '-'}
            </div>
          </div>
          <Tag color={s.source === 'COMPANY' ? 'purple' : 'blue'}>
            {s.source === 'COMPANY' ? '公司库' : '自建'}
          </Tag>
        </div>
      ))}
    </div>
  );
}

/** A2A 远端 Skill：来自 Agent Card，无平台 Skill 详情可关联 */
function RemoteSkillsTab({ skills }: { skills: RemoteSkill[] }) {
  if (skills.length === 0) {
    return <Empty description="远端 Agent Card 未声明 skills" />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {skills.map((s, i) => (
        <div
          key={`${s.name}-${i}`}
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            padding: '14px 16px',
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
          }}
        >
          <div
            style={{
              fontSize: 14,
              fontWeight: 500,
              color: COLOR.textPrimary,
            }}
          >
            {s.name}
          </div>
          <div style={{ fontSize: 12, color: COLOR.textMuted }}>
            {s.description?.trim() || '远端未提供描述'}
          </div>
        </div>
      ))}
    </div>
  );
}

/** v2.3：A2A 远端 MCP 接入 Tab */
function McpTab({ mcps }: { mcps: RemoteMcp[] }) {
  if (mcps.length === 0) {
    return <Empty description="远端 Agent Card 未声明 MCP 接入" />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {mcps.map((m, i) => (
        <div
          key={`${m.name}-${i}`}
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            padding: '14px 16px',
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
          }}
        >
          <div
            style={{
              fontSize: 14,
              fontWeight: 500,
              color: COLOR.textPrimary,
            }}
          >
            {m.name}
          </div>
          {m.description?.trim() ? (
            <div style={{ fontSize: 12, color: COLOR.textMuted }}>
              {m.description}
            </div>
          ) : null}
          {m.serverUrl ? (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12,
                color: COLOR.textSecondary,
              }}
            >
              <span style={{ color: COLOR.textMuted }}>serverUrl：</span>
              <Paragraph
                copyable={{
                  text: m.serverUrl,
                  icon: [
                    <CopyOutlined key="copy" />,
                    <CopyOutlined key="copied" />,
                  ],
                }}
                style={{
                  margin: 0,
                  fontFamily:
                    'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                  wordBreak: 'break-all',
                }}
              >
                {m.serverUrl}
              </Paragraph>
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
}

function PromptBlock(props: { title: string; content: string }) {
  return (
    <div
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        padding: '14px 16px',
      }}
    >
      <div
        style={{
          fontSize: 13,
          fontWeight: 500,
          color: COLOR.textPrimary,
          marginBottom: 8,
        }}
      >
        {props.title}
      </div>
      <pre
        style={{
          margin: 0,
          fontSize: 12,
          color: '#314158',
          whiteSpace: 'pre-wrap',
          fontFamily:
            'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
        }}
      >
        {props.content}
      </pre>
    </div>
  );
}

// 兼容旧 import：保留 Space 引用避免 tree-shaking 警告
void Space;
