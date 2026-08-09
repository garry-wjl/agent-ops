/**
 * Agent 配置模式编辑器 — `/agent/manage/editor/:num` —— Agent 配置优化（2026-06-11）
 *
 * 取代原 5 步 StepsForm Drawer（components/CreateForm.tsx），改为单页分区表单 + sticky footer，
 * 对齐工具 / Skill 编辑器的整页形态。
 *
 * 模式：
 *  - 新建：num='new' → POST /create，成功跳详情页。
 *  - 编辑草稿：num=agentNum + ?versionId=DRAFT 版本 id → POST /version/edit（保存草稿）；
 *    「发布」走 POST /publish（弹备注 Modal）。
 *
 * 资产化：模型 / 工具 / 沙箱改为从对应资产模块「可用状态」列表下拉关联，仅存引用标识：
 *  - modelId（单选必填，已启用模型 num）
 *  - toolNums（多选，已发布工具 num）
 *  - sandboxRef（单选可空，在线沙箱 num）
 *  - skillNums（多选，已发布 Skill num）
 *
 * 分区：基本信息 → 关联模型 → 工具与上下文（Skills/工具/沙箱/知识库/记忆）。
 * 详见 PRD §7 / §8.1、技术方案 §9 fe。
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Spin,
  Typography,
  message,
} from 'antd';
import {
  ApiOutlined,
  AppstoreAddOutlined,
} from '@ant-design/icons';
import { agentApi } from '@/services/agent';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import { useModelSelectableQuery } from '@/services/model';
import { useSkillPageQuery } from '@/services/skill';
import { useToolPageQuery } from '@/services/tool';
import { useSandboxPageQuery } from '@/services/sandbox';
import type {
  AgentCreateParam,
  AgentType,
  ConfigSnapshot,
  ModelSelectableVO,
} from '@/types';
import ToolsContextSection, {
  type ToolsContextValue,
} from '../components/ToolsContextSection';
import PromptPickerModal from '../components/PromptPickerModal';
import A2aCreateForm from '../components/A2aCreateForm';
import { RequiredLabel, ModelMetaCard } from '../components/EditorShared';
import type { AssetOption } from '../components/AssetPickerModal';

const { Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  bgInfo: '#EFF6FF',
  borderInfo: '#DBEAFE',
} as const;

/** 单页表单内部草稿（基本信息 + 模型 + 工具与上下文）。 */
interface AgentDraft {
  name: string;
  description?: string;
  agentType: AgentType;
  systemPrompt?: string;
  userPrompt?: string;
  modelId?: string;
  temperature?: number;
  /** 2026-06-17 模型管理优化:Plan 模式开关(仅持久化/展示,运行时不消费) */
  enablePlan?: boolean;
  /** 最大迭代轮次（ReAct 循环次数），默认 10 */
  maxIters?: number;
  ctx: ToolsContextValue;
}

function emptyDraft(): AgentDraft {
  return {
    name: '',
    description: '',
    agentType: 'NORMAL',
    systemPrompt: '',
    userPrompt: '',
    modelId: undefined,
    temperature: 0.7,
    enablePlan: false,
    maxIters: 10,
    ctx: {
      skillNums: [],
      skillRefs: [],
      toolNums: [],
      sandboxRef: undefined,
      memoryConfig: {
        shortTermStrategy: 'RECENT_N',
        shortTermN: 10,
        longTermStrategy: 'NONE',
      },
      qps: 10,
      dailyBudget: 100,
    },
  };
}

export default function AgentEditorPage() {
  const navigate = useNavigate();
  const { num } = useParams<{ num: string }>();
  const [searchParams] = useSearchParams();
  const versionId = searchParams.get('versionId') ?? undefined;

  const isNew = !num || num === 'new';
  const agentNum = isNew ? '' : num!;

  // 创建方式：仅新建态可选（CONFIG 配置模式 / A2A 接入）；编辑态恒为 CONFIG。
  // 参考工具编辑器：选择器内联在页面顶部，不再用弹窗。
  const [creationMethod, setCreationMethod] = useState<'CONFIG' | 'A2A'>(
    'CONFIG',
  );

  const [draft, setDraft] = useState<AgentDraft>(emptyDraft());
  useBreadcrumbName(isNew ? undefined : draft.name);
  // A2A 操作按钮 portal 目标（顶部右侧容器）
  const [a2aActionsEl, setA2aActionsEl] = useState<HTMLDivElement | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [promptOpen, setPromptOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishRemark, setPublishRemark] = useState('');

  // —— 资产候选（按可用状态筛选；一次拉 200 条，前端检索）——
  // 2026-06-17 scope 优化：模型改用 selectable 接口（系统启用 + 当前空间启用合集，不含 Key），
  // 展示「系统 / 空间」tag，让 Agent 可选系统模型。
  const { data: selectableModels } = useModelSelectableQuery();
  const { data: skillPage } = useSkillPageQuery({
    pageNo: 1,
    pageSize: 200,
    status: 'PUBLISHED',
  });
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
  const skillOptions: AssetOption[] = useMemo(
    () =>
      (skillPage?.list ?? []).map((s) => ({
        num: s.num,
        name: s.name,
        meta: s.description,
      })),
    [skillPage],
  );
  const toolOptions: AssetOption[] = useMemo(
    () =>
      (toolPage?.list ?? []).map((t) => ({
        num: t.num,
        name: t.name,
        meta: `${t.type === 'MCP' ? 'MCP' : 'FunctionCall'} · ${t.description}`,
      })),
    [toolPage],
  );
  const sandboxOptions: AssetOption[] = useMemo(
    () =>
      (sandboxPage?.list ?? []).map((s) => ({
        num: s.num,
        name: s.name,
        meta: `${s.type} · ${s.cpu}核 / ${s.memoryMb}MB`,
      })),
    [sandboxPage],
  );

  // —— 编辑态：拉取草稿版本快照回填 ——
  useEffect(() => {
    if (isNew) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const detail = await agentApi.detail(agentNum);
        // 优先用 versionId 在版本列表里找 DRAFT 行的 configSnapshot；
        // 退化为 detail.currentVersion.configSnapshot
        let snap: ConfigSnapshot | undefined =
          detail.currentVersion?.configSnapshot;
        if (versionId) {
          const versions = await agentApi.versionList(agentNum);
          const row = versions.find((v) => v.num === versionId);
          if (row?.configSnapshot) snap = row.configSnapshot;
        }
        if (cancelled) return;
        setDraft({
          name: snap?.name ?? detail.name ?? '',
          description: snap?.description ?? detail.description ?? '',
          agentType: (snap?.agentType ?? detail.agentType ?? 'NORMAL') as AgentType,
          systemPrompt: snap?.systemPrompt ?? '',
          userPrompt: snap?.userPrompt ?? '',
          modelId: snap?.modelId,
          temperature: snap?.temperature ?? 0.7,
          enablePlan: snap?.enablePlan ?? false,
          maxIters: snap?.maxIters ?? 10,
          ctx: {
            skillNums: snap?.skillNums ?? [],
            // 优先用已钉版的 skillRefs 回填；旧数据仅有 skillNums 时用空 versionNum 占位，
            // 由编辑器内版本下拉加载后默认补最新在线版。
            skillRefs:
              snap?.skillRefs ??
              (snap?.skillNums ?? []).map((n) => ({
                skillNum: n,
                versionNum: '',
              })),
            toolNums: snap?.toolNums ?? [],
            sandboxRef: snap?.sandboxRef,
            memoryConfig: {
              shortTermStrategy:
                snap?.memoryConfig?.shortTermStrategy ?? 'RECENT_N',
              shortTermN: snap?.memoryConfig?.shortTermN ?? 10,
              longTermStrategy:
                snap?.memoryConfig?.longTermStrategy ?? 'NONE',
            },
            qps: snap?.qps ?? 10,
            dailyBudget: snap?.dailyBudget ?? 100,
          },
        });
      } catch {
        // 拦截器已 toast
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isNew, agentNum, versionId]);

  const patch = (p: Partial<AgentDraft>) => setDraft((d) => ({ ...d, ...p }));
  const selectedModel = models.find((m) => m.num === draft.modelId);

  /** 整页校验：返回首个错误信息（null=通过）。 */
  const validate = (): string | null => {
    if (!draft.name.trim()) return '请输入名称';
    if (draft.name.trim().length > 64) return '名称不超过 64 字符';
    if (!draft.systemPrompt?.trim()) return '请输入系统提示词';
    if (!draft.modelId) return '请选择关联模型';
    return null;
  };

  /** 组装提交快照 / 入参。 */
  const buildSnapshot = (): ConfigSnapshot => ({
    name: draft.name.trim(),
    description: draft.description?.trim(),
    creationMode: 'CONFIG',
    agentType: draft.agentType,
    systemPrompt: draft.systemPrompt,
    userPrompt: draft.userPrompt,
    modelId: draft.modelId,
    temperature: draft.temperature,
    enablePlan: draft.enablePlan ?? false,
    maxIters: draft.maxIters ?? 10,
    skillNums: draft.ctx.skillNums,
    skillRefs: draft.ctx.skillRefs,
    toolNums: draft.ctx.toolNums,
    sandboxRef: draft.ctx.sandboxRef,
    memoryConfig: draft.ctx.memoryConfig,
    qps: draft.ctx.qps,
    dailyBudget: draft.ctx.dailyBudget,
  });

  const buildCreateParam = (): AgentCreateParam => ({
    name: draft.name.trim(),
    description: draft.description?.trim(),
    agentType: draft.agentType,
    systemPrompt: draft.systemPrompt,
    userPrompt: draft.userPrompt,
    modelId: draft.modelId,
    temperature: draft.temperature,
    enablePlan: draft.enablePlan ?? false,
    maxIters: draft.maxIters ?? 10,
    skillNums: draft.ctx.skillNums,
    skillRefs: draft.ctx.skillRefs,
    toolNums: draft.ctx.toolNums,
    sandboxRef: draft.ctx.sandboxRef,
    memoryConfig: draft.ctx.memoryConfig,
    qps: draft.ctx.qps,
    dailyBudget: draft.ctx.dailyBudget,
  });

  /** 新建：create → 跳详情。编辑：editDraftVersion → 跳详情。 */
  const handleSave = async () => {
    const err = validate();
    if (err) {
      message.error(err);
      return;
    }
    setSaving(true);
    try {
      if (isNew) {
        const res = await agentApi.create(buildCreateParam());
        message.success(`已创建 Agent ${res.agentNum}（v1.0.0）`);
        navigate(`/agent/manage/detail/${res.agentNum}`);
      } else {
        if (!versionId) {
          message.error('缺少 versionId（DRAFT 版本 id），无法保存草稿');
          return;
        }
        await agentApi.editDraftVersion(versionId, buildSnapshot());
        message.success('草稿已保存');
        navigate(`/agent/manage/detail/${agentNum}?tab=versions`);
      }
    } catch {
      // 拦截器已 toast
    } finally {
      setSaving(false);
    }
  };

  /** 发布（仅编辑态）：先保存草稿再 publish；新建态不直接发布（须先创建出 Agent）。 */
  const handlePublish = async () => {
    const err = validate();
    if (err) {
      message.error(err);
      return;
    }
    if (publishRemark.trim().length < 10) {
      message.error('发布备注至少 10 字');
      return;
    }
    if (!versionId) {
      message.error('缺少 versionId，无法发布');
      return;
    }
    setSaving(true);
    try {
      await agentApi.editDraftVersion(versionId, buildSnapshot());
      await agentApi.publish({ versionId, agentNum, remark: publishRemark.trim() });
      message.success('已发布新版本');
      setPublishOpen(false);
      navigate(`/agent/manage/detail/${agentNum}`);
    } catch {
      // 拦截器已 toast
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin />
      </div>
    );
  }

  // 配置模式操作按钮（对齐工具编辑器：放在顶部操作栏右侧，无取消按钮）
  // 新建态主操作「创建」用 primary（与创建工具的「发布」按钮同色）。
  const showConfigActions = !(isNew && creationMethod === 'A2A');
  const configActions = showConfigActions ? (
    <Space>
      <Button
        type={isNew ? 'primary' : 'default'}
        loading={saving}
        onClick={handleSave}
      >
        {isNew ? '创建' : '保存草稿'}
      </Button>
      {!isNew && (
        <Button
          type="primary"
          loading={saving}
          onClick={() => {
            const err = validate();
            if (err) {
              message.error(err);
              return;
            }
            setPublishRemark('');
            setPublishOpen(true);
          }}
        >
          发布
        </Button>
      )}
    </Space>
  ) : null;

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath="/agent/manage"
        moduleName="Agent 管理"
        current={
          isNew
            ? `新建 Agent · ${creationMethod === 'A2A' ? 'A2A 接入' : '配置模式'}`
            : '编辑 Agent · 配置模式'
        }
        actions={
          showConfigActions ? configActions : <div ref={setA2aActionsEl} />
        }
      />

      {/* 表单主体 */}
      <div>
        {/* 创建方式选择（仅新建态；参考工具编辑器，内联在页面顶部，不用弹窗） */}
        {isNew && (
          <Form layout="vertical" style={{ maxWidth: 880 }}>
            <Form.Item label="创建方式" required>
              <Radio.Group
                value={creationMethod}
                onChange={(e) => setCreationMethod(e.target.value)}
                style={{ display: 'flex', gap: 12, width: '100%' }}
              >
                <Radio.Button
                  value="CONFIG"
                  style={{
                    flex: 1,
                    height: 'auto',
                    padding: '12px 16px',
                    textAlign: 'left',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <AppstoreAddOutlined
                      style={{ fontSize: 22, color: COLOR.primary }}
                    />
                    <div>
                      <div style={{ fontWeight: 600 }}>配置模式</div>
                      <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                        在平台从零搭建：模型 / Skills / 工具 / 沙箱 / 记忆
                      </div>
                    </div>
                  </div>
                </Radio.Button>
                <Radio.Button
                  value="A2A"
                  style={{
                    flex: 1,
                    height: 'auto',
                    padding: '12px 16px',
                    textAlign: 'left',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <ApiOutlined style={{ fontSize: 22, color: '#16A34A' }} />
                    <div>
                      <div style={{ fontWeight: 600 }}>A2A 模式</div>
                      <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                        接入已注册到 Nacos 的远端 Agent，平台仅订阅同步
                      </div>
                    </div>
                  </div>
                </Radio.Button>
              </Radio.Group>
            </Form.Item>
          </Form>
        )}

        {/* A2A 接入表单（新建 + 选 A2A 时；接入完成跳详情页） */}
        {isNew && creationMethod === 'A2A' ? (
          <div style={{ maxWidth: 880 }}>
            <A2aCreateForm
              actionsContainer={a2aActionsEl}
              onDone={(detail) => {
                if (detail?.num) {
                  navigate(`/agent/manage/detail/${detail.num}`);
                } else {
                  navigate('/agent/manage');
                }
              }}
            />
          </div>
        ) : (
          <>
            <Form layout="vertical" style={{ maxWidth: 880 }}>
              <div style={{ display: 'flex', gap: 16 }}>
                <Form.Item label={<RequiredLabel text="名称" />} style={{ flex: 1 }}>
                  <Input
                    placeholder="如 客服助手"
                    maxLength={64}
                    showCount
                    value={draft.name}
                    onChange={(e) => patch({ name: e.target.value })}
                  />
                </Form.Item>
                <Form.Item label="温度" style={{ width: 160 }}>
                  <InputNumber
                    min={0}
                    max={2}
                    step={0.1}
                    precision={2}
                    style={{ width: '100%' }}
                    value={draft.temperature}
                    onChange={(v) => patch({ temperature: v ?? undefined })}
                  />
                </Form.Item>
                <Form.Item label="最大迭代轮次" style={{ width: 200 }}>
                  <InputNumber
                    min={1}
                    max={100}
                    step={1}
                    precision={0}
                    style={{ width: '100%' }}
                    value={draft.maxIters}
                    onChange={(v) => patch({ maxIters: v ?? undefined })}
                  />
                </Form.Item>
              </div>
              <Form.Item label="描述">
                <Input.TextArea
                  placeholder="Agent 用途描述，≤500 字"
                  maxLength={500}
                  showCount
                  rows={2}
                  value={draft.description}
                  onChange={(e) => patch({ description: e.target.value })}
                />
              </Form.Item>

              {/* 关联模型：放在描述之后（2026-06-17：selectable 合集，展示系统/空间 tag） */}
              <Form.Item label={<RequiredLabel text="模型" />}>
                {models.length === 0 ? (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      <span style={{ color: COLOR.textMuted }}>
                        暂无可用模型，请先到「模型管理」新建并启用模型
                      </span>
                    }
                  >
                    <Button
                      type="primary"
                      onClick={() => navigate('/model/manage')}
                    >
                      前往模型管理
                    </Button>
                  </Empty>
                ) : (
                  <Select
                    showSearch
                    placeholder="选择已启用模型（系统 / 空间）"
                    style={{ maxWidth: 480 }}
                    value={draft.modelId}
                    onChange={(v) => patch({ modelId: v })}
                    optionFilterProp="label"
                    options={models.map((m) => ({
                      value: m.num,
                      label:
                        m.scope === 'PLATFORM'
                          ? `[系统] ${m.name}（${m.modelId}）`
                          : `[空间] ${m.name}（${m.modelId}）`,
                    }))}
                  />
                )}
              </Form.Item>
              {selectedModel && (
                <ModelMetaCard model={selectedModel} />
              )}

              {/* 2026-06-17 模型管理优化：Plan 模式开关（仅持久化/展示，运行时不消费） */}
              <Form.Item
                label="Plan 模式"
                required
                rules={[{ required: true, message: 'Plan 模式必选' }]}
              >
                <Radio.Group
                  value={draft.enablePlan ? 'ON' : 'OFF'}
                  onChange={(e) => patch({ enablePlan: e.target.value === 'ON' })}
                >
                  <Radio value="OFF">关闭</Radio>
                  <Radio value="ON">开启</Radio>
                </Radio.Group>
              </Form.Item>

              <Form.Item
                label={
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      width: '100%',
                    }}
                  >
                    <RequiredLabel text="系统提示词" />
                    <Space size={12}>
                      <a
                        onClick={() => {
                          const example = [
                            '',
                            '## 默认业务上下文',
                            '当用户未特别说明时，优先使用当前页面上下文：',
                            '- 当前订单：{{orderId}}',
                            '若用户明确提到其他业务主键或无关问题时，按用户意图处理，不要强行绑定上述默认对象。',
                          ].join('\n');
                          const cur = draft.systemPrompt ?? '';
                          patch({
                            systemPrompt: cur
                              ? `${cur.replace(/\s+$/, '')}\n${example}`
                              : example.trimStart(),
                          });
                          message.success('已插入默认上下文示例');
                        }}
                        style={{ fontSize: 13 }}
                      >
                        插入默认上下文示例
                      </a>
                      <a
                        onClick={() => setPromptOpen(true)}
                        style={{ fontSize: 13 }}
                      >
                        + 从提示词中心选择
                      </a>
                    </Space>
                  </div>
                }
                style={{ width: '100%' }}
              >
                <Input.TextArea
                  placeholder="定义 Agent 的角色与行为；支持 {{orderId}} 等变量，调用时由 context 替换"
                  rows={4}
                  value={draft.systemPrompt}
                  onChange={(e) => patch({ systemPrompt: e.target.value })}
                />
                <div
                  style={{
                    marginTop: 8,
                    padding: '8px 12px',
                    background: COLOR.bgInfo,
                    border: `1px solid ${COLOR.borderInfo}`,
                    borderRadius: 6,
                    fontSize: 12,
                    color: COLOR.textSecondary,
                    lineHeight: 1.6,
                  }}
                >
                  <div style={{ fontWeight: 500, marginBottom: 4, color: COLOR.textPrimary }}>
                    变量替换说明
                  </div>
                  <div>
                    业务变量：调用方传入的 context 键，如 {'{{orderId}}'}（缺失则保留原文）。
                  </div>
                  <div>
                    内置变量（全大写）：{'{{SESSION_NUM}}'} · {'{{AGENT_NUM}}'} ·{' '}
                    {'{{AGENT_VERSION_NUM}}'} · {'{{WORKSPACE_NUM}}'} ·{' '}
                    {'{{OPERATOR_ID}}'}
                  </div>
                </div>
              </Form.Item>
              <Form.Item label="用户提示词模板">
                <Input.TextArea
                  placeholder="可空；支持 {{变量}} 占位"
                  rows={2}
                  value={draft.userPrompt}
                  onChange={(e) => patch({ userPrompt: e.target.value })}
                />
              </Form.Item>
            </Form>

            {/* ▣ 工具与上下文 */}
            <SectionTitle>工具与上下文</SectionTitle>
            <div style={{ maxWidth: 880 }}>
              <ToolsContextSection
                value={draft.ctx}
                onChange={(ctx) => patch({ ctx })}
                skillOptions={skillOptions}
                toolOptions={toolOptions}
                sandboxOptions={sandboxOptions}
              />
            </div>
          </>
        )}
      </div>

      {/* 从提示词中心选择 */}
      <PromptPickerModal
        open={promptOpen}
        onPick={(content) => {
          patch({ systemPrompt: content });
          setPromptOpen(false);
        }}
        onCancel={() => setPromptOpen(false)}
      />

      {/* 发布备注 Modal（仅编辑态） */}
      <Modal
        open={publishOpen}
        title="发布新版本"
        okText="发布"
        confirmLoading={saving}
        onOk={handlePublish}
        onCancel={() => setPublishOpen(false)}
        destroyOnHidden
      >
        <Text type="secondary" style={{ fontSize: 13 }}>
          发布前将保存当前草稿并升版本（patch+1）。
        </Text>
        <Input.TextArea
          rows={3}
          placeholder="发布备注（≥10 字），说明本次变更内容"
          value={publishRemark}
          onChange={(e) => setPublishRemark(e.target.value)}
          style={{ marginTop: 12 }}
        />
      </Modal>
    </div>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        fontSize: 15,
        fontWeight: 600,
        color: COLOR.textPrimary,
        margin: '24px 0 12px',
      }}
    >
      {children}
    </div>
  );
}
