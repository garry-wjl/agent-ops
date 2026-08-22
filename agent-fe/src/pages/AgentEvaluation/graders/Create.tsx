/**
 * 创建 / 编辑评估器 — `/agent/evaluation/graders/new` | `.../:num/edit`
 * 创建：类型以卡片选择；编辑：类型与预置只读，可改名称/描述/配置；试跑在右上角。
 */
import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  CodeOutlined,
  PlayCircleOutlined,
  QuestionCircleOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import { modelApi } from '@/services/model';
import type { EvalGraderVO, GraderPresetVO, ModelSelectableVO } from '@/types';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import { COLOR, EVAL_BASE } from '../constants';
import { LabelWithTip } from '../LabelWithTip';
import {
  BuiltinConfigTable,
  parseBuiltinConfig,
  serializeBuiltinConfig,
} from './BuiltinConfigTable';
import GraderTrialModal from './GraderTrialModal';

const { Title, Text } = Typography;

type GraderKindUi = 'builtin' | 'llm' | 'code';

const KIND_META: Record<
  GraderKindUi,
  {
    label: string;
    desc: string;
    color: string;
    icon: ReactNode;
    tip: ReactNode;
  }
> = {
  builtin: {
    label: '内置预置',
    desc: '关键词、精确匹配、工具调用等平台模板',
    color: '#2563EB',
    icon: <ThunderboltOutlined />,
    tip: (
      <>
        <div style={{ fontWeight: 600, marginBottom: 6 }}>内置预置 · 适用场景</div>
        <div>
          确定性规则、格式/关键词检查，便宜稳定。适合：输出是否非空、是否包含指定词、是否与
          reference 精确匹配、是否调用了工具等。
        </div>
      </>
    ),
  },
  llm: {
    label: 'LLM 评估器',
    desc: '模型 + Prompt 模板，输出结构化得分',
    color: '#7C3AED',
    icon: <RobotOutlined />,
    tip: (
      <>
        <div style={{ fontWeight: 600, marginBottom: 6 }}>LLM 评估器 · 适用场景</div>
        <div>
          语义质量、礼貌、是否答对意思——字面很难精确匹配时用。由模型按 Prompt
          对 response / reference 等打分并判定 Pass。
        </div>
      </>
    ),
  },
  code: {
    label: 'Code 评估器',
    desc: 'SpEL 脚本自定义评分逻辑',
    color: '#0F766E',
    icon: <CodeOutlined />,
    tip: (
      <>
        <div style={{ fontWeight: 600, marginBottom: 6 }}>Code 评估器 · 适用场景</div>
        <div>
          内置模板盖不住的业务规则，用 SpEL
          写确定性逻辑（长度、包含关系、JSON 字段校验等），不依赖大模型。
        </div>
      </>
    ),
  },
};

const KIND_TYPE_TIP = (
  <>
    <div style={{ fontWeight: 600, marginBottom: 8 }}>怎么选评估器类型？</div>
    <div style={{ marginBottom: 8 }}>
      <b>内置预置：</b>格式 / 关键词 / 是否调工具等确定性检查，稳定、便宜。
    </div>
    <div style={{ marginBottom: 8 }}>
      <b>LLM：</b>语义对不对、语气好不好等主观/语义质量，适合问答类主尺子。
    </div>
    <div>
      <b>Code：</b>自定义确定性规则，用 SpEL 表达内置模板覆盖不到的逻辑。
    </div>
    <div style={{ marginTop: 8, opacity: 0.85 }}>
      同一任务可挂多个评估器；全部 Pass 该行才算综合通过。悬停各类型卡片上的问号可看适用场景。
    </div>
  </>
);

/** 内置预置：悬停选项时展示适用场景 */
const PRESET_SCENARIO: Record<string, string> = {
  NON_EMPTY: '适合做兜底检查：Agent 是否产生了有效输出。例如拦截空回复、超时无结果。',
  EXACT_MATCH:
    '适合答案唯一、可字面对比的场景。例如固定短码、枚举值、必须与 reference 一字不差（可配置 trim/忽略大小写）。',
  CONTAINS:
    '适合必须出现关键措辞或要素的场景。例如客服回复须含「已退款」「工单号」等关键词（全部关键词都需出现）。',
  JSON_VALID:
    '适合要求输出为结构化 JSON 的场景。例如 Agent 须返回对象/数组，便于下游解析。',
  TOOL_CALLED:
    '适合必须走工具链路的场景。例如要求 Agent 实际调用了工具，而不只是口头描述。',
  TOOL_NAME_CONTAINS:
    '适合校验调用了特定工具的场景。例如轨迹中须出现含某关键词的工具名（如 search、refund）。',
};

/** 各类型表单字段说明 */
const FIELD_TIPS = {
  builtin: {
    presetCode:
      '选择平台预置规则模板。展开下拉后，将鼠标悬停在某一项上可查看该模板适用场景；选定后会带出默认名称与配置。',
    name: '评估器在列表与任务配置中的展示名称，建议能看出用途（如「必须含工单号」）。',
    description: '可选说明：用途、适用场景、注意事项等，仅作文档用。',
    config:
      '按预置类型填写稳定参数。表格中「用途」「取值含义」说明各字段；无参预置无需填写。',
  },
  llm: {
    name: '评估器展示名称，建议能看出评分维度（如「语义是否答对」）。',
    description: '可选说明：评分标准摘要、适用任务等，仅作文档用。',
    modelNum:
      '用于打分的已启用模型。建议选稳定、便宜的中小模型；成本与延迟会随评测集行数放大。',
    promptTemplate: (
      <>
        <div style={{ marginBottom: 6 }}>
          只写评分标准与上下文即可。用 {'{{变量名}}'} 引用变量；未映射的占位符会变成空字符串。
        </div>
        <div style={{ marginBottom: 6 }}>
          <b>输出格式由系统强制注入</b>：会按下方「最低分～最高分」动态追加 JSON 要求（
          {'{"score","reason"}'}），无需在 Prompt 里手写分数格式。
        </div>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>系统约定变量（推荐直接用）</div>
        <div>• {'{{response}}'}：Agent 实际输出（任务默认映射自 $actual_output）</div>
        <div>• {'{{reference}}'}：评测集行内参考答案（任务默认映射自 $row.reference）</div>
        <div style={{ marginTop: 6, marginBottom: 4 }}>
          <b>mapping 为空时</b>引擎还会注入：response、actual_output、reference（行里有才有）、row、trace。
        </div>
        <div>
          自定义变量名：在创建评测任务的「变量映射」中配置；解析到的 score 再与通过阈值比较判定 Pass/Fail。
        </div>
      </>
    ),
    scoreMin:
      '评分区间下界（通常 0）。系统会按此区间生成输出格式注入，并在解析后把分数钳制到该下界。',
    scoreMax:
      '评分区间上界（通常 100）。系统注入的 JSON 要求与解析钳制都使用该上界；请与通过阈值同一量纲。',
    passThreshold:
      '得分 ≥ 该阈值视为 Pass，否则 Fail。需落在最低分～最高分之间（如 60）。',
    variableNames: (
      <>
        <div style={{ marginBottom: 6 }}>
          逗号分隔，须与 Prompt 中 {'{{...}}'} 占位符一致。此处主要为配置记录；真正取值在创建任务的「变量映射」里配置。
        </div>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>默认约定</div>
        <div>• response ← 实际输出（$actual_output）</div>
        <div>• reference ← 行字段 reference（$row.reference）</div>
        <div style={{ marginTop: 6 }}>
          建议至少包含 response,reference，与默认 Prompt 一致。若改用其它名字，创建任务时在「变量映射」中同步配置。
        </div>
      </>
    ),
  },
  code: {
    name: '评估器展示名称，建议能看出规则意图（如「response 含 reference」）。',
    description: '可选说明：脚本逻辑摘要、适用场景等，仅作文档用。',
    script: (
      <>
        <div style={{ marginBottom: 6 }}>
          SpEL 表达式，须返回 Map，至少含 score（数值）与 passed（布尔）。
        </div>
        <div>
          可用变量：#response、#reference、#row、#actualOutput、#trace。确定性规则优先用 Code，避免不必要的 LLM 成本。
        </div>
      </>
    ),
    timeoutMs: '单次脚本执行超时（毫秒）。超时记为失败；一般 1000～5000 即可。',
  },
} as const;

const DEFAULT_LLM_PROMPT = `请根据 reference 评估 response 的质量与正确性，给出合理分数与简短理由。

评分要点：
- 是否覆盖 reference 中的关键信息
- 表述是否清晰、无明显错误

response: {{response}}
reference: {{reference}}`;

const DEFAULT_CODE_SCRIPT = `# SpEL 脚本示例：返回 Map，需含 score（数值）与 passed（布尔）
# 可用变量：response, reference, row（行数据 Map）, actualOutput, trace
{
  'score': #response != null && #response.length() > 0 ? 100 : 0,
  'passed': #response != null && #response.length() > 0,
  'explanation': '输出非空则通过'
}`;

function parseKind(raw: string | null): GraderKindUi {
  if (raw === 'llm' || raw === 'code' || raw === 'builtin') return raw;
  return 'builtin';
}

function kindFromVo(kind?: string): GraderKindUi {
  const k = String(kind || '').toUpperCase();
  if (k === 'LLM') return 'llm';
  if (k === 'CODE') return 'code';
  return 'builtin';
}

function parseJsonObject(raw?: string): Record<string, unknown> {
  try {
    const v = JSON.parse((raw || '{}').trim() || '{}');
    return v && typeof v === 'object' && !Array.isArray(v)
      ? (v as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function buildLlmConfigJson(values: {
  modelNum: string;
  promptTemplate: string;
  scoreMin?: number;
  scoreMax?: number;
  passThreshold?: number;
  variableNames?: string;
}): string {
  const variableNames = values.variableNames
    ?.split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  return JSON.stringify({
    modelNum: values.modelNum,
    promptTemplate: values.promptTemplate.trim(),
    scoreMin: values.scoreMin ?? 0,
    scoreMax: values.scoreMax ?? 100,
    passThreshold: values.passThreshold ?? 60,
    ...(variableNames?.length ? { variableNames } : {}),
  });
}

function buildCodeConfigJson(values: {
  script: string;
  timeoutMs?: number;
}): string {
  return JSON.stringify({
    script: values.script.trim(),
    timeoutMs: values.timeoutMs ?? 3000,
  });
}

export default function GraderCreatePage() {
  const navigate = useNavigate();
  const { num: editNum } = useParams<{ num?: string }>();
  const isEdit = Boolean(editNum);
  const [searchParams, setSearchParams] = useSearchParams();
  const [kind, setKind] = useState<GraderKindUi>(() =>
    parseKind(searchParams.get('kind')),
  );
  const [loadingDetail, setLoadingDetail] = useState(isEdit);
  const [detail, setDetail] = useState<EvalGraderVO | null>(null);
  const [trialOpen, setTrialOpen] = useState(false);
  const [editHydrated, setEditHydrated] = useState(false);

  useEffect(() => {
    if (isEdit) return;
    setKind(parseKind(searchParams.get('kind')));
  }, [searchParams, isEdit]);

  const [builtinForm] = Form.useForm();
  const [llmForm] = Form.useForm();
  const [codeForm] = Form.useForm();
  const [presets, setPresets] = useState<GraderPresetVO[]>([]);
  const [models, setModels] = useState<ModelSelectableVO[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const presetCode = Form.useWatch('presetCode', builtinForm) as
    | string
    | undefined;
  useBreadcrumbName(isEdit ? '编辑评估器' : '新建评估器');

  useEffect(() => {
    void evalApi
      .graderPresets()
      .then(setPresets)
      .catch(() => undefined);
    void modelApi
      .selectable()
      .then(setModels)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!isEdit || !editNum) return;
    setLoadingDetail(true);
    void evalApi
      .graderDetail(editNum)
      .then((d) => {
        setDetail(d);
        const uiKind = kindFromVo(d.kind);
        setKind(uiKind);
        const cfg = parseJsonObject(d.configJson);
        if (uiKind === 'builtin') {
          builtinForm.setFieldsValue({
            presetCode: d.builtinCode,
            name: d.name,
            description: d.description,
            config: parseBuiltinConfig(d.builtinCode, d.configJson || '{}'),
            configJson: d.configJson || '{}',
          });
        } else if (uiKind === 'llm') {
          const vars = cfg.variableNames;
          llmForm.setFieldsValue({
            name: d.name,
            description: d.description,
            modelNum: cfg.modelNum,
            promptTemplate: cfg.promptTemplate ?? DEFAULT_LLM_PROMPT,
            scoreMin: cfg.scoreMin ?? 0,
            scoreMax: cfg.scoreMax ?? 100,
            passThreshold: cfg.passThreshold ?? 60,
            variableNames: Array.isArray(vars)
              ? vars.join(',')
              : typeof vars === 'string'
                ? vars
                : 'response,reference',
          });
        } else {
          codeForm.setFieldsValue({
            name: d.name,
            description: d.description,
            script: cfg.script ?? DEFAULT_CODE_SCRIPT,
            timeoutMs: cfg.timeoutMs ?? 3000,
          });
        }
        setEditHydrated(true);
      })
      .catch(() => message.error('加载评估器失败'))
      .finally(() => setLoadingDetail(false));
  }, [isEdit, editNum, builtinForm, llmForm, codeForm]);

  useEffect(() => {
    if (isEdit && editHydrated) return;
    if (!presetCode) return;
    const p = presets.find((x) => x.presetCode === presetCode);
    if (!p) return;
    builtinForm.setFieldsValue({
      name: p.name,
      description: p.description,
      config: parseBuiltinConfig(presetCode, p.defaultConfigJson || '{}'),
      configJson: p.defaultConfigJson || '{}',
    });
  }, [presetCode, presets, builtinForm, isEdit, editHydrated]);

  const handleKindChange = (next: GraderKindUi) => {
    if (isEdit) return;
    setKind(next);
    setSearchParams(next === 'builtin' ? {} : { kind: next }, { replace: true });
  };

  const afterSave = (num: string) => {
    navigate(`${EVAL_BASE}/graders/${num}`);
  };

  const onFinishBuiltin = async (values: {
    presetCode: string;
    name: string;
    description?: string;
    config?: Record<string, unknown>;
    configJson?: string;
  }) => {
    setSubmitting(true);
    try {
      const configJson = serializeBuiltinConfig(
        values.presetCode,
        values.config,
        values.configJson,
      );
      if (isEdit && editNum) {
        await evalApi.updateGrader({
          num: editNum,
          name: values.name.trim(),
          description: values.description?.trim(),
          configJson,
        });
        message.success('已保存');
        afterSave(editNum);
      } else {
        const res = await evalApi.createBuiltinGrader({
          presetCode: values.presetCode,
          name: values.name.trim(),
          description: values.description?.trim(),
          configJson,
        });
        message.success('评估器已创建');
        afterSave(res.num);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const onFinishLlm = async (values: {
    name: string;
    description?: string;
    modelNum: string;
    promptTemplate: string;
    scoreMin?: number;
    scoreMax?: number;
    passThreshold?: number;
    variableNames?: string;
  }) => {
    setSubmitting(true);
    try {
      if (isEdit && editNum) {
        await evalApi.updateGrader({
          num: editNum,
          name: values.name.trim(),
          description: values.description?.trim(),
          configJson: buildLlmConfigJson(values),
        });
        message.success('已保存');
        afterSave(editNum);
      } else {
        const variableNames = values.variableNames
          ?.split(',')
          .map((s) => s.trim())
          .filter(Boolean);
        await evalApi.createLlmGrader({
          name: values.name.trim(),
          description: values.description?.trim(),
          modelNum: values.modelNum,
          promptTemplate: values.promptTemplate.trim(),
          scoreMin: values.scoreMin,
          scoreMax: values.scoreMax,
          passThreshold: values.passThreshold,
          variableNames: variableNames?.length ? variableNames : undefined,
        });
        message.success('LLM 评估器已创建');
        navigate(`${EVAL_BASE}/graders`);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const onFinishCode = async (values: {
    name: string;
    description?: string;
    script: string;
    timeoutMs?: number;
  }) => {
    setSubmitting(true);
    try {
      if (isEdit && editNum) {
        await evalApi.updateGrader({
          num: editNum,
          name: values.name.trim(),
          description: values.description?.trim(),
          configJson: buildCodeConfigJson(values),
        });
        message.success('已保存');
        afterSave(editNum);
      } else {
        await evalApi.createCodeGrader({
          name: values.name.trim(),
          description: values.description?.trim(),
          script: values.script.trim(),
          timeoutMs: values.timeoutMs,
        });
        message.success('Code 评估器已创建');
        navigate(`${EVAL_BASE}/graders`);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loadingDetail) {
    return (
      <div style={{ padding: 32 }}>
        <Text type="secondary">加载中…</Text>
      </div>
    );
  }

  const submitLabel = isEdit ? '保存' : '创建';
  const cancelPath = isEdit && editNum
    ? `${EVAL_BASE}/graders/${editNum}`
    : `${EVAL_BASE}/graders`;

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/graders`}
        moduleName="Agent 评测"
        current={isEdit ? '编辑评估器' : '新建评估器'}
      />
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: 16,
          marginBottom: 24,
          width: '100%',
        }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <Title
            level={3}
            style={{ margin: '0 0 4px', color: COLOR.textPrimary, fontWeight: 700 }}
          >
            {isEdit ? '编辑评估器' : '创建评估器'}
          </Title>
          <Text style={{ color: COLOR.textSecondary, display: 'block' }}>
            {isEdit
              ? '可修改名称、描述与配置；类型与预置模板创建后不可更改'
              : '选择类型后填写对应配置；切换类型时下方表单会随之变化'}
          </Text>
          {isEdit && detail && (
            <Text
              style={{
                fontFamily: 'ui-monospace, monospace',
                fontSize: 13,
                color: COLOR.textMuted,
                display: 'block',
                marginTop: 4,
              }}
            >
              {detail.num}
              {detail.version != null ? ` · v${detail.version}` : ''}
            </Text>
          )}
        </div>
        {isEdit && editNum && (
          <Space style={{ flexShrink: 0 }}>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={() => setTrialOpen(true)}
            >
              试跑
            </Button>
          </Space>
        )}
      </div>

      {isEdit ? (
        <div style={{ marginBottom: 24 }}>
          <Text type="secondary" style={{ marginRight: 8 }}>
            评估器类型
          </Text>
          <Tag color={KIND_META[kind].color}>{KIND_META[kind].label}</Tag>
          {detail?.builtinCode && <Tag color="blue">{detail.builtinCode}</Tag>}
        </div>
      ) : (
        <Form.Item
          label={
            <LabelWithTip label="评估器类型" tip={KIND_TYPE_TIP} />
          }
          required
          labelCol={{ span: 24 }}
          wrapperCol={{ span: 24 }}
          style={{ marginBottom: 24 }}
        >
          <Radio.Group
            value={kind}
            onChange={(e) => handleKindChange(e.target.value as GraderKindUi)}
            style={{ display: 'flex', gap: 12, width: '100%' }}
          >
            {(Object.keys(KIND_META) as GraderKindUi[]).map((k) => {
              const meta = KIND_META[k];
              const selected = kind === k;
              return (
                <Radio.Button
                  key={k}
                  value={k}
                  style={{
                    flex: 1,
                    height: 'auto',
                    padding: '12px 16px',
                    textAlign: 'left',
                    whiteSpace: 'normal',
                    borderColor: selected ? meta.color : undefined,
                    boxShadow: selected
                      ? `0 0 0 1px ${meta.color}`
                      : undefined,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      fontWeight: 600,
                      color: meta.color,
                      marginBottom: 4,
                    }}
                  >
                    {meta.icon}
                    <span style={{ flex: 1 }}>{meta.label}</span>
                    <Tooltip
                      title={<div style={{ maxWidth: 320 }}>{meta.tip}</div>}
                      styles={{ root: { maxWidth: 380 } }}
                    >
                      <QuestionCircleOutlined
                        style={{
                          color: COLOR.textMuted,
                          fontSize: 12,
                          cursor: 'help',
                        }}
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                        }}
                      />
                    </Tooltip>
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      color: COLOR.textMuted,
                      lineHeight: 1.4,
                      whiteSpace: 'normal',
                    }}
                  >
                    {meta.desc}
                  </div>
                </Radio.Button>
              );
            })}
          </Radio.Group>
        </Form.Item>
      )}

      {kind === 'builtin' && (
        <Form form={builtinForm} layout="vertical" onFinish={onFinishBuiltin}>
          <Form.Item
            name="presetCode"
            label={
              <LabelWithTip label="预置模板" tip={FIELD_TIPS.builtin.presetCode} />
            }
            rules={[{ required: true, message: '请选择预置' }]}
          >
            <Select
              placeholder="选择预置"
              disabled={isEdit}
              options={presets.map((p) => ({
                value: p.presetCode,
                label: `${p.name}（${p.presetCode}）`,
                title: PRESET_SCENARIO[p.presetCode],
              }))}
              optionRender={(option) => {
                const code = String(option.value ?? '');
                const tip =
                  PRESET_SCENARIO[code] ??
                  (typeof option.data?.title === 'string'
                    ? option.data.title
                    : undefined);
                return (
                  <Tooltip
                    title={tip}
                    placement="right"
                    mouseEnterDelay={0.15}
                    getPopupContainer={() => document.body}
                    styles={{ root: { maxWidth: 360 } }}
                  >
                    <div style={{ width: '100%' }}>{option.label}</div>
                  </Tooltip>
                );
              }}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label={<LabelWithTip label="名称" tip={FIELD_TIPS.builtin.name} />}
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="description"
            label={
              <LabelWithTip label="描述" tip={FIELD_TIPS.builtin.description} />
            }
          >
            <Input.TextArea rows={2} maxLength={256} />
          </Form.Item>
          <Form.Item
            label={<LabelWithTip label="配置参数" tip={FIELD_TIPS.builtin.config} />}
          >
            <BuiltinConfigTable presetCode={presetCode} />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={submitting}>
              {submitLabel}
            </Button>
            <Button onClick={() => navigate(cancelPath)}>取消</Button>
          </Space>
        </Form>
      )}

      {kind === 'llm' && (
        <Form
          form={llmForm}
          layout="vertical"
          initialValues={{
            promptTemplate: DEFAULT_LLM_PROMPT,
            scoreMin: 0,
            scoreMax: 100,
            passThreshold: 60,
            variableNames: 'response,reference',
          }}
          onFinish={onFinishLlm}
        >
          <Form.Item
            name="name"
            label={<LabelWithTip label="名称" tip={FIELD_TIPS.llm.name} />}
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input maxLength={64} placeholder="如：语义相似度 LLM 评分" />
          </Form.Item>
          <Form.Item
            name="description"
            label={<LabelWithTip label="描述" tip={FIELD_TIPS.llm.description} />}
          >
            <Input.TextArea rows={2} maxLength={256} />
          </Form.Item>
          <Form.Item
            name="modelNum"
            label={<LabelWithTip label="模型" tip={FIELD_TIPS.llm.modelNum} />}
            rules={[{ required: true, message: '请选择模型' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择已启用模型"
              options={models.map((m) => ({
                value: m.num,
                label: `${m.name} (${m.modelId})`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="promptTemplate"
            label={
              <LabelWithTip
                label="Prompt 模板"
                tip={FIELD_TIPS.llm.promptTemplate}
              />
            }
            rules={[{ required: true, message: '请输入 Prompt 模板' }]}
          >
            <Input.TextArea
              rows={8}
              style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
            />
          </Form.Item>
          <Space wrap style={{ width: '100%' }}>
            <Form.Item
              name="scoreMin"
              label={<LabelWithTip label="最低分" tip={FIELD_TIPS.llm.scoreMin} />}
            >
              <InputNumber min={0} max={1000} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item
              name="scoreMax"
              label={<LabelWithTip label="最高分" tip={FIELD_TIPS.llm.scoreMax} />}
            >
              <InputNumber min={0} max={1000} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item
              name="passThreshold"
              label={
                <LabelWithTip
                  label="通过阈值"
                  tip={FIELD_TIPS.llm.passThreshold}
                />
              }
            >
              <InputNumber min={0} max={1000} style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="variableNames"
            label={
              <LabelWithTip
                label="变量名列表"
                tip={FIELD_TIPS.llm.variableNames}
              />
            }
          >
            <Input placeholder="response,reference" />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={submitting}>
              {submitLabel}
            </Button>
            <Button onClick={() => navigate(cancelPath)}>取消</Button>
          </Space>
        </Form>
      )}

      {kind === 'code' && (
        <Form
          form={codeForm}
          layout="vertical"
          initialValues={{ script: DEFAULT_CODE_SCRIPT, timeoutMs: 3000 }}
          onFinish={onFinishCode}
        >
          <Form.Item
            name="name"
            label={<LabelWithTip label="名称" tip={FIELD_TIPS.code.name} />}
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input maxLength={64} placeholder="如：自定义非空校验" />
          </Form.Item>
          <Form.Item
            name="description"
            label={
              <LabelWithTip label="描述" tip={FIELD_TIPS.code.description} />
            }
          >
            <Input.TextArea rows={2} maxLength={256} />
          </Form.Item>
          <Form.Item
            name="script"
            label={
              <LabelWithTip label="SpEL 脚本" tip={FIELD_TIPS.code.script} />
            }
            rules={[{ required: true, message: '请输入脚本' }]}
          >
            <Input.TextArea
              rows={12}
              style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
            />
          </Form.Item>
          <Form.Item
            name="timeoutMs"
            label={
              <LabelWithTip label="超时（毫秒）" tip={FIELD_TIPS.code.timeoutMs} />
            }
          >
            <InputNumber min={100} max={60000} step={100} style={{ width: 160 }} />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={submitting}>
              {submitLabel}
            </Button>
            <Button onClick={() => navigate(cancelPath)}>取消</Button>
          </Space>
        </Form>
      )}

      {isEdit && editNum && (
        <GraderTrialModal
          graderNum={editNum}
          open={trialOpen}
          onClose={() => setTrialOpen(false)}
        />
      )}
    </div>
  );
}
