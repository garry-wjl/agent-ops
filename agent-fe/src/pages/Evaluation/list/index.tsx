/**
 * 评测任务列表 — `/skill/evaluation`
 * 像素级还原 Figma 节点 116:2（AgentSphere · Eval - Tasks）
 * 视觉：标题 + 筛选胶囊（全部/进行中/已完成/失败）+ 表格
 * 顶部按钮：导入 case（白底） + + 新建评测任务（蓝底，跳 New 页）
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Steps,
  Table,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  CloudUploadOutlined,
  ExperimentOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { EvalApi, AgentApi, SkillApi } from '@/services';
import type {
  AgentVO,
  EvaluationVO,
  EvalStatus,
  SkillVO,
} from '@/types';
import { formatTime } from '@/utils/format';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E5E7EB',
  borderHard: '#E2E8F0',
  headerBg: '#F8FAFC',
  textPrimary: '#0F172B',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  primary: '#3B82F6',
  primaryDeep: '#2563EB',
  bgInfo: '#EFF6FF',
  textSuccess: '#10B981',
  bgSuccess: '#ECFDF5',
  textError: '#DC2626',
  bgError: '#FEF2F2',
  textRunning: '#2563EB',
  bgRunning: '#EFF6FF',
  textPending: '#D97706',
  bgPending: '#FEF3C7',
} as const;

const STATUS_CHIP: Record<EvalStatus, { label: string; bg: string; color: string }> = {
  PENDING: { label: '排队中', bg: COLOR.bgPending, color: COLOR.textPending },
  RUNNING: { label: '运行中', bg: COLOR.bgRunning, color: COLOR.textRunning },
  SUCCESS: { label: '已完成', bg: COLOR.bgSuccess, color: COLOR.textSuccess },
  FAILED: { label: '失败', bg: COLOR.bgError, color: COLOR.textError },
};

type FilterKey = 'ALL' | EvalStatus;

export default function EvaluationListPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<EvaluationVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState<FilterKey>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const reqSeq = useRef(0);

  const reload = async () => {
    const seq = ++reqSeq.current;
    setLoading(true);
    try {
      const res = await EvalApi.pageList({ pageNo: 1, pageSize: 200 });
      if (seq === reqSeq.current) setData(res.list);
    } finally {
      if (seq === reqSeq.current) setLoading(false);
    }
  };

  useEffect(() => {
    reload();
  }, []);

  const counts = useMemo(() => {
    const out: Record<FilterKey, number> = {
      ALL: data.length,
      PENDING: 0,
      RUNNING: 0,
      SUCCESS: 0,
      FAILED: 0,
    };
    data.forEach((d) => {
      out[d.status] = (out[d.status] ?? 0) + 1;
    });
    return out;
  }, [data]);

  const filtered = useMemo(
    () => (filter === 'ALL' ? data : data.filter((d) => d.status === filter)),
    [data, filter],
  );

  const columns: TableColumnsType<EvaluationVO> = useMemo(
    () => [
      {
        title: '任务名',
        dataIndex: 'num',
        key: 'num',
        width: 240,
        render: (num: string) => (
          <a
            onClick={() => navigate(`/skill/evaluation/detail/${num}`)}
            style={{ color: COLOR.textPrimary, fontWeight: 500 }}
          >
            {num}
          </a>
        ),
      },
      {
        title: 'TARGET',
        key: 'target',
        width: 220,
        render: (_, r) => (
          <Text style={{ color: COLOR.textSecondary, fontSize: 12 }}>
            {r.targetType.toLowerCase()}/{r.targetNum}
            {r.targetVersionNum ? ` · ${r.targetVersionNum}` : ''}
          </Text>
        ),
      },
      {
        title: 'CASE',
        dataIndex: 'caseCount',
        key: 'caseCount',
        width: 80,
        align: 'right',
        render: (n?: number) => (
          <Text style={{ color: COLOR.textSecondary }}>{n ?? '-'}</Text>
        ),
      },
      {
        title: '方式',
        dataIndex: 'mode',
        key: 'mode',
        width: 80,
        render: (m: string) => (
          <Text style={{ color: COLOR.textSecondary }}>
            {m === 'AUTO' ? '自动' : '人工'}
          </Text>
        ),
      },
      {
        title: '进度',
        key: 'progress',
        width: 120,
        render: (_, r) => {
          const cfg = STATUS_CHIP[r.status];
          return (
            <span
              style={{
                background: cfg.bg,
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
        title: '通过率',
        dataIndex: 'passRate',
        key: 'passRate',
        width: 100,
        align: 'right',
        render: (v?: number) => {
          if (typeof v !== 'number')
            return <Text style={{ color: COLOR.textMuted }}>-</Text>;
          const pct = (v * 100).toFixed(0);
          const color = v >= 0.9 ? COLOR.textSuccess : v >= 0.6 ? '#D97706' : COLOR.textError;
          return (
            <Text style={{ color, fontWeight: 500 }}>{pct}%</Text>
          );
        },
      },
      {
        title: '创建人',
        dataIndex: 'executorAgentNum',
        key: 'executorAgentNum',
        width: 120,
        render: (n?: string) => (
          <Text style={{ color: COLOR.textSecondary, fontSize: 12 }}>
            {n ?? '-'}
          </Text>
        ),
      },
      {
        title: '创建时间',
        dataIndex: 'gmtCreate',
        key: 'gmtCreate',
        width: 160,
        render: (t?: string) => (
          <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>
            {formatTime(t) ?? '-'}
          </Text>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 120,
        render: (_, r) => (
          <Space size={16}>
            <a onClick={() => navigate(`/skill/evaluation/detail/${r.num}`)}>
              详情
            </a>
            <a
              onClick={() =>
                Modal.confirm({
                  title: '删除评测？',
                  okType: 'danger',
                  onOk: async () => {
                    await EvalApi.delete(r.num);
                    message.success('已删除');
                    reload();
                  },
                })
              }
              style={{ color: COLOR.textError }}
            >
              删除
            </a>
          </Space>
        ),
      },
    ],
    [navigate],
  );

  const filterChips: { key: FilterKey; label: string }[] = [
    { key: 'ALL', label: `全部 ${counts.ALL}` },
    { key: 'RUNNING', label: `进行中 ${counts.RUNNING}` },
    { key: 'SUCCESS', label: `已完成 ${counts.SUCCESS}` },
    { key: 'FAILED', label: `失败 ${counts.FAILED}` },
  ];

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
              Skill 评测 · 任务列表
            </Title>
            <Text
              style={{
                color: COLOR.textSecondary,
                fontSize: 14,
                marginTop: 4,
                display: 'block',
              }}
            >
              基于 Case 集批量评测 Agent / Skill / Prompt，自动/人工双模式，结果可追溯与跨版本对比
            </Text>
          </div>
          <Space>
            <Button
              icon={<ExperimentOutlined />}
              onClick={() => navigate('/skill/evaluation/seeds')}
              style={{ borderColor: COLOR.border }}
            >
              种子库
            </Button>
            <Button
              icon={<CloudUploadOutlined />}
              onClick={() =>
                message.info('M2 上线 case 批量导入（CSV / JSONL）')
              }
              style={{ borderColor: COLOR.border }}
            >
              导入 case
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateOpen(true)}
              style={{ background: COLOR.primary }}
            >
              新建评测任务
            </Button>
          </Space>
        </div>
      </div>

      {/* 筛选胶囊 */}
      <div
        style={{
          display: 'flex',
          gap: 8,
          marginBottom: 16,
        }}
      >
        {filterChips.map((c) => {
          const active = c.key === filter;
          return (
            <button
              key={c.key}
              onClick={() => setFilter(c.key)}
              style={{
                padding: '6px 14px',
                background: active ? COLOR.bgInfo : '#fff',
                border: `1px solid ${active ? COLOR.primaryDeep : COLOR.borderHard}`,
                color: active ? COLOR.primaryDeep : COLOR.textSecondary,
                borderRadius: 999,
                fontSize: 13,
                fontWeight: active ? 500 : 400,
                cursor: 'pointer',
              }}
            >
              {c.label}
            </button>
          );
        })}
      </div>

      {/* 表格 */}
      <div
        style={{
          border: `1px solid ${COLOR.borderHard}`,
          borderRadius: 8,
          overflow: 'hidden',
          background: '#fff',
        }}
      >
        <Table<EvaluationVO>
          rowKey="num"
          columns={columns}
          dataSource={filtered}
          loading={loading}
          pagination={false}
          size="middle"
          rowClassName={() => 'eval-list-row'}
        />
      </div>

      <style>{`
        .eval-list-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.borderHard} !important;
        }
        .ant-table-thead > tr > th {
          background: ${COLOR.headerBg} !important;
          color: ${COLOR.textMuted} !important;
          font-size: 11px !important;
          font-weight: 700 !important;
          letter-spacing: 0.06em !important;
          text-transform: uppercase;
          padding: 10px 16px !important;
          border-bottom: 1px solid ${COLOR.borderHard} !important;
        }
        .ant-table-thead > tr > th::before {
          display: none !important;
        }
      `}</style>

      <CreateEvalDrawer
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={reload}
      />
    </div>
  );
}

/* =================== Create Drawer (Steps 向导) =================== */

function CreateEvalDrawer(props: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const { open, onClose, onCreated } = props;
  const [step, setStep] = useState(0);
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [skills, setSkills] = useState<SkillVO[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    if (!open) return;
    AgentApi.pageList({ pageNo: 1, pageSize: 100, status: 'PUBLISHED' }).then(
      (r) => setAgents(r.list),
    );
    SkillApi.pageList({ pageNo: 1, pageSize: 200, status: 'PUBLISHED' }).then(
      (r) => setSkills(r.list),
    );
    setStep(0);
    form.resetFields();
  }, [open, form]);

  const targetOptions = (type: 'SKILL' | 'AGENT') =>
    type === 'SKILL'
      ? skills.map((s) => ({ value: s.num, label: s.name }))
      : agents.map((a) => ({ value: a.num, label: a.name }));

  const submit = async () => {
    const v = await form.validateFields();
    if (v.mode === 'MANUAL') {
      const res = await EvalApi.createManual({
        targetType: v.targetType,
        targetNum: v.targetNum,
        executorAgentNum: v.executorAgentNum,
        input: v.inputText ? JSON.parse(v.inputText) : v.inputPlain,
        expectedOutput: v.expectedOutput,
        judgeMethod: v.judgeMethod,
      });
      message.success(`已完成评测 ${res.num}`);
    } else {
      const res = await EvalApi.createAuto({
        targetType: v.targetType,
        targetNum: v.targetNum,
        executorAgentNum: v.executorAgentNum,
        judgeAgentNum: v.judgeAgentNum,
        dimensions: v.dimensions,
        caseCount: v.caseCount,
        dataSource: v.dataSource,
        concurrency: v.concurrency,
      });
      message.success(`自动评测已提交 ${res.num}`);
    }
    onClose();
    onCreated();
  };

  const stepsItems = [
    { title: '对象' },
    { title: 'Case' },
    { title: '方式' },
    { title: '确认' },
  ];

  return (
    <Drawer
      title="新建评测任务"
      placement="right"
      width={720}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          {step < 3 ? (
            <Button type="primary" onClick={() => setStep(step + 1)}>
              下一步 →
            </Button>
          ) : (
            <Button type="primary" onClick={submit}>
              提交
            </Button>
          )}
        </Space>
      }
    >
      <Steps current={step} items={stepsItems} style={{ marginBottom: 24 }} />

      <Form
        form={form}
        layout="vertical"
        initialValues={{
          targetType: 'SKILL',
          mode: 'MANUAL',
          dataSource: 'SCHEMA_GEN',
          dimensions: ['ACCURACY', 'RELEVANCE', 'COMPLETENESS'],
          caseCount: 20,
          concurrency: 5,
          judgeMethod: 'LLM_JUDGE',
        }}
      >
        {/* Step 1: 对象 */}
        <div style={{ display: step === 0 ? 'block' : 'none' }}>
          <Form.Item label="评测对象类型" name="targetType">
            <Radio.Group>
              <Radio.Button value="SKILL">Skill</Radio.Button>
              <Radio.Button value="AGENT">Agent</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item shouldUpdate noStyle>
            {({ getFieldValue }) => (
              <Form.Item
                label="选择对象"
                name="targetNum"
                rules={[{ required: true }]}
              >
                <Select
                  options={targetOptions(getFieldValue('targetType'))}
                  showSearch
                  optionFilterProp="label"
                  placeholder="搜索 skill / agent..."
                />
              </Form.Item>
            )}
          </Form.Item>
        </div>

        {/* Step 2: Case */}
        <div style={{ display: step === 1 ? 'block' : 'none' }}>
          <Form.Item label="数据源" name="dataSource">
            <Radio.Group>
              <Radio.Button value="SCHEMA_GEN">Schema 生成</Radio.Button>
              <Radio.Button value="HISTORY">历史回放</Radio.Button>
              <Radio.Button value="SEED">种子库</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item label="用例数" name="caseCount">
            <InputNumber min={1} max={500} style={{ width: '100%' }} />
          </Form.Item>
        </div>

        {/* Step 3: 方式 */}
        <div style={{ display: step === 2 ? 'block' : 'none' }}>
          <Form.Item label="评测模式" name="mode">
            <Radio.Group>
              <Radio.Button value="MANUAL">人工调试（同步）</Radio.Button>
              <Radio.Button value="AUTO">自动评测（异步）</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item shouldUpdate noStyle>
            {({ getFieldValue }) => {
              const mode = getFieldValue('mode');
              return mode === 'MANUAL' ? (
                <>
                  <Form.Item label="执行 Agent" name="executorAgentNum" rules={[{ required: true }]}>
                    <Select options={agents.map((a) => ({ value: a.num, label: a.name }))} />
                  </Form.Item>
                  <Form.Item label="文本输入" name="inputPlain">
                    <Input.TextArea rows={3} placeholder="或在 inputText 填 JSON" />
                  </Form.Item>
                  <Form.Item label="JSON 输入" name="inputText">
                    <Input.TextArea rows={3} placeholder='{"key":"value"}' />
                  </Form.Item>
                  <Form.Item label="期望输出（可选）" name="expectedOutput">
                    <Input.TextArea rows={2} />
                  </Form.Item>
                  <Form.Item label="判分方式" name="judgeMethod">
                    <Radio.Group>
                      <Radio.Button value="LLM_JUDGE">LLM 判分</Radio.Button>
                      <Radio.Button value="KEYWORD">关键词</Radio.Button>
                      <Radio.Button value="RULE">规则</Radio.Button>
                      <Radio.Button value="NONE">不判分</Radio.Button>
                    </Radio.Group>
                  </Form.Item>
                </>
              ) : (
                <>
                  <Form.Item label="执行 Agent" name="executorAgentNum" rules={[{ required: true }]}>
                    <Select options={agents.map((a) => ({ value: a.num, label: a.name }))} />
                  </Form.Item>
                  <Form.Item label="判分 Agent" name="judgeAgentNum" rules={[{ required: true }]}>
                    <Select options={agents.map((a) => ({ value: a.num, label: a.name }))} />
                  </Form.Item>
                  <Form.Item label="评分维度" name="dimensions">
                    <Select
                      mode="multiple"
                      options={[
                        { value: 'ACCURACY', label: '准确性' },
                        { value: 'RELEVANCE', label: '相关性' },
                        { value: 'COMPLETENESS', label: '完整性' },
                        { value: 'STYLE', label: '风格' },
                        { value: 'COMPLIANCE', label: '合规' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item label="并发" name="concurrency">
                    <InputNumber min={1} max={20} style={{ width: '100%' }} />
                  </Form.Item>
                </>
              );
            }}
          </Form.Item>
        </div>

        {/* Step 4: 确认 */}
        <div style={{ display: step === 3 ? 'block' : 'none' }}>
          <Form.Item shouldUpdate>
            {({ getFieldsValue }) => {
              const v = getFieldsValue();
              return (
                <div
                  style={{
                    background: '#F8FAFC',
                    border: `1px solid ${COLOR.border}`,
                    borderRadius: 8,
                    padding: '14px 16px',
                    fontSize: 13,
                    lineHeight: 1.8,
                  }}
                >
                  <div>
                    <Text strong>对象：</Text>
                    {v.targetType} · {v.targetNum ?? '(未选)'}
                  </div>
                  <div>
                    <Text strong>Case：</Text>
                    {v.dataSource} · {v.caseCount} 条
                  </div>
                  <div>
                    <Text strong>方式：</Text>
                    {v.mode === 'MANUAL' ? '人工调试（同步）' : '自动评测（异步）'}
                  </div>
                  {v.mode === 'AUTO' ? (
                    <div>
                      <Text strong>维度：</Text>
                      {(v.dimensions ?? []).join(' / ')}
                    </div>
                  ) : null}
                </div>
              );
            }}
          </Form.Item>
        </div>
      </Form>
    </Drawer>
  );
}
