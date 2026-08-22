/**
 * 创建并启动评测任务 — `/agent/evaluation/tasks/new`
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Form,
  Input,
  Select,
  Space,
  Typography,
  message,
} from 'antd';
import { evalApi } from '@/services/evaluation';
import { agentApi } from '@/services/agent';
import type {
  AgentVO,
  AgentVersionVO,
  EvalDatasetVO,
  EvalGraderVO,
  TaskBindMode,
} from '@/types';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import { COLOR, EVAL_BASE } from '../constants';
import { LabelWithTip } from '../LabelWithTip';
import { GraderMappingEditor } from './GraderMappingEditor';
import {
  mappingRowsToRecord,
  syncMappingsByGraders,
  buildGraderBindingsPayload,
  type MappingRow,
} from './graderMapping';
import {
  buildUniqueEvalName,
  isAutoTaskName,
} from '../suggestEvalName';

const { Title, Text } = Typography;

const FIELD_TIPS = {
  name: '本任务的展示名称，便于在列表与对比中识别（如「客服 v3 回归」）。',
  description: '可选说明：评测目的、改动点、注意事项等，仅作文档用。',
  datasetNum:
    '只能选择已发布的评测集。任务创建后配置冻结，将按选定版本的行数据执行。',
  datasetVersion:
    '评测集的已发布版本号。不同版本行内容可能不同；对比回归时建议两边用同一版本。',
  bindMode:
    'AGENT：按行调用指定 Agent 版本，用实际输出打分。NONE：不调用 Agent，适合评测集里已有 output、只做离线评分或人工标注。',
  agentNum:
    '要评测的目标 Agent。若所选评测集已绑定 Agent，将自动带出；仍可手动改选。',
  agentVersionNum:
    '该 Agent 的已发布版本。任务会冻结此版本配置；换版本请另建任务，便于对比。自动带出 Agent 时会优先选在线版本。',
  graderNums: (
    <>
      <div style={{ marginBottom: 6 }}>
        本任务挂载的评估器（可多选）。全部 Pass 该行才算综合通过。
      </div>
      <div>
        选中后下方「变量映射」默认 response→$actual_output、reference→$row.reference；自定义变量名或评测集列名时，在映射表中改。
      </div>
    </>
  ),
} as const;

export default function TaskCreatePage() {
  const navigate = useNavigate();
  useBreadcrumbName('新建评测任务');
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [datasets, setDatasets] = useState<EvalDatasetVO[]>([]);
  const [graders, setGraders] = useState<EvalGraderVO[]>([]);
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [versions, setVersions] = useState<AgentVersionVO[]>([]);
  const [mappingsByGrader, setMappingsByGrader] = useState<
    Record<string, MappingRow[]>
  >({});
  const [existingTaskNames, setExistingTaskNames] = useState<string[]>([]);

  const datasetNum = Form.useWatch('datasetNum', form) as string | undefined;
  const bindMode = Form.useWatch('bindMode', form) as TaskBindMode | undefined;
  const agentNum = Form.useWatch('agentNum', form) as string | undefined;
  const graderNums =
    (Form.useWatch('graderNums', form) as string[] | undefined) ?? [];
  const graderNumsKey = graderNums.join(',');

  useEffect(() => {
    void Promise.all([
      evalApi.pageDatasets({ pageNo: 1, pageSize: 100, status: 'PUBLISHED' }),
      evalApi.pageGraders({ pageNo: 1, pageSize: 100 }),
      agentApi.pageList({ pageNo: 1, pageSize: 100 }),
      evalApi.pageTasks({ pageNo: 1, pageSize: 100 }),
    ]).then(([ds, gr, ag, tk]) => {
      setDatasets(ds?.list ?? []);
      setGraders(gr?.list ?? []);
      setAgents(ag?.list ?? []);
      const names = (tk?.list ?? []).map((t) => t.name).filter(Boolean);
      setExistingTaskNames(names);
      const current = form.getFieldValue('name') as string | undefined;
      if (!current?.trim() || isAutoTaskName(current)) {
        form.setFieldValue(
          'name',
          buildUniqueEvalName('评测任务', names),
        );
      }
    });
  }, [form]);

  const suggestNameForDataset = (num?: string) => {
    const current = form.getFieldValue('name') as string | undefined;
    if (current && !isAutoTaskName(current)) return;
    const dsName = num
      ? datasets.find((d) => d.num === num)?.name?.trim()
      : undefined;
    const prefix = dsName ? `${dsName} ·` : '评测任务';
    form.setFieldValue(
      'name',
      buildUniqueEvalName(prefix, existingTaskNames),
    );
  };
  useEffect(() => {
    const nums = graderNumsKey ? graderNumsKey.split(',') : [];
    setMappingsByGrader((prev) => syncMappingsByGraders(prev, nums));
  }, [graderNumsKey]);

  useEffect(() => {
    if (!agentNum) {
      setVersions([]);
      return;
    }
    void agentApi
      .versionList(agentNum)
      .then(setVersions)
      .catch(() => setVersions([]));
  }, [agentNum]);

  /** Agent 版本列表就绪且尚未选择时，优先带出在线已发布版本 */
  useEffect(() => {
    if (!agentNum || versions.length === 0) return;
    if (form.getFieldValue('agentVersionNum')) return;
    const published = versions.filter(
      (v) => String(v.status).toUpperCase() === 'PUBLISHED',
    );
    const preferred =
      published.find((v) => v.current) ?? published[0] ?? undefined;
    if (preferred?.num) {
      form.setFieldValue('agentVersionNum', preferred.num);
    }
  }, [agentNum, versions, form]);

  const versionOptions = useMemo(() => {
    const ds = datasets.find((d) => d.num === datasetNum);
    if (!ds?.latestVersion) return [];
    return Array.from({ length: ds.latestVersion }, (_, i) => {
      const v = i + 1;
      return { value: v, label: `v${v}` };
    }).reverse();
  }, [datasets, datasetNum]);

  const publishedVersions = useMemo(
    () => versions.filter((v) => String(v.status).toUpperCase() === 'PUBLISHED'),
    [versions],
  );

  const applyDatasetSelection = (num: string) => {
    suggestNameForDataset(num);
    const ds = datasets.find((d) => d.num === num);
    const patch: Record<string, unknown> = {
      datasetVersion: ds?.latestVersion ?? undefined,
    };
    if (ds?.agentNum) {
      patch.bindMode = 'AGENT';
      patch.agentNum = ds.agentNum;
      patch.agentVersionNum = undefined;
    } else {
      patch.agentNum = undefined;
      patch.agentVersionNum = undefined;
    }
    form.setFieldsValue(patch);
  };

  const onFinish = async (values: {
    name: string;
    description?: string;
    datasetNum: string;
    datasetVersion: number;
    bindMode: TaskBindMode;
    agentNum?: string;
    agentVersionNum?: string;
    graderNums: string[];
  }) => {
    if (!values.graderNums?.length) {
      message.warning('请至少选择一个评估器');
      return;
    }
    for (const num of values.graderNums) {
      const mapping = mappingRowsToRecord(mappingsByGrader[num]);
      if (!Object.keys(mapping).length) {
        message.warning(`评估器 ${num} 的变量映射不能为空`);
        return;
      }
    }
    setSubmitting(true);
    try {
      const res = await evalApi.createAndStartTask({
        name: values.name.trim(),
        description: values.description?.trim(),
        datasetNum: values.datasetNum,
        datasetVersion: values.datasetVersion,
        bindMode: values.bindMode,
        agentNum:
          values.bindMode === 'AGENT' ? values.agentNum : undefined,
        agentVersionNum:
          values.bindMode === 'AGENT' ? values.agentVersionNum : undefined,
        graders: buildGraderBindingsPayload(
          values.graderNums,
          mappingsByGrader,
        ),
      });
      message.success('任务已创建并启动');
      navigate(`${EVAL_BASE}/tasks/${res.num}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        padding: 32,
        background: '#fff',
        minHeight: '100%',
        maxWidth: 880,
      }}
    >
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/tasks`}
        moduleName="Agent 评测"
        current="新建评测任务"
      />
      <Title
        level={3}
        style={{ margin: '0 0 4px', color: COLOR.textPrimary, fontWeight: 700 }}
      >
        创建评测任务
      </Title>
      <Text
        style={{
          color: COLOR.textSecondary,
          display: 'block',
          marginBottom: 24,
        }}
      >
        选定已发布评测集版本、Agent 与评估器；变量默认 response /
        reference，可在下方映射表自定义
      </Text>

      <Form
        form={form}
        layout="vertical"
        initialValues={{
          bindMode: 'AGENT',
          name: buildUniqueEvalName('评测任务', []),
        }}
        onFinish={onFinish}
      >
        <Form.Item
          name="name"
          label={<LabelWithTip label="名称" tip={FIELD_TIPS.name} />}
          rules={[{ required: true, message: '请输入名称' }]}
          extra="已自动填充唯一名称；选定评测集后若仍为自动名会带上评测集名"
        >
          <Input maxLength={64} placeholder="如：客服 v3 回归" />
        </Form.Item>
        <Form.Item
          name="description"
          label={<LabelWithTip label="描述" tip={FIELD_TIPS.description} />}
        >
          <Input.TextArea rows={2} maxLength={256} />
        </Form.Item>
        <Form.Item
          name="datasetNum"
          label={<LabelWithTip label="评测集" tip={FIELD_TIPS.datasetNum} />}
          rules={[{ required: true, message: '请选择评测集' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="仅已发布"
            options={datasets.map((d) => ({
              value: d.num,
              label: `${d.name} (${d.num})${d.agentNum ? ' · 已绑 Agent' : ''}`,
            }))}
            onChange={(num) => applyDatasetSelection(num)}
          />
        </Form.Item>
        <Form.Item
          name="datasetVersion"
          label={
            <LabelWithTip label="评测集版本" tip={FIELD_TIPS.datasetVersion} />
          }
          rules={[{ required: true, message: '请选择版本' }]}
        >
          <Select
            placeholder="选择版本"
            options={versionOptions}
            disabled={!datasetNum}
          />
        </Form.Item>
        <Form.Item
          name="bindMode"
          label={<LabelWithTip label="绑定方式" tip={FIELD_TIPS.bindMode} />}
          rules={[{ required: true }]}
        >
          <Select
            options={[
              { value: 'AGENT', label: 'AGENT（调用 Agent 产出）' },
              { value: 'NONE', label: 'NONE（纯标注，不调用 Agent）' },
            ]}
          />
        </Form.Item>
        {bindMode === 'AGENT' && (
          <>
            <Form.Item
              name="agentNum"
              label={<LabelWithTip label="Agent" tip={FIELD_TIPS.agentNum} />}
              rules={[{ required: true, message: '请选择 Agent' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={agents.map((a) => ({
                  value: a.num,
                  label: `${a.name} (${a.num})`,
                }))}
                onChange={() =>
                  form.setFieldValue('agentVersionNum', undefined)
                }
              />
            </Form.Item>
            <Form.Item
              name="agentVersionNum"
              label={
                <LabelWithTip
                  label="Agent 版本"
                  tip={FIELD_TIPS.agentVersionNum}
                />
              }
              rules={[{ required: true, message: '请选择版本' }]}
            >
              <Select
                placeholder="选择已发布版本"
                disabled={!agentNum}
                options={(publishedVersions.length
                  ? publishedVersions
                  : versions
                ).map((v) => ({
                  value: v.num,
                  label: `${v.versionNum || v.version || v.num} (${v.status})${v.current ? ' · 在线' : ''}`,
                }))}
              />
            </Form.Item>
          </>
        )}
        <Form.Item
          name="graderNums"
          label={<LabelWithTip label="评估器" tip={FIELD_TIPS.graderNums} />}
          rules={[{ required: true, message: '请选择评估器' }]}
        >
          <Select
            mode="multiple"
            showSearch
            optionFilterProp="label"
            placeholder="可多选"
            options={graders.map((g) => ({
              value: g.num,
              label: `${g.name} (${g.builtinCode || g.kind})`,
            }))}
          />
        </Form.Item>
        <Form.Item style={{ marginBottom: 24 }}>
          <GraderMappingEditor
            graderNums={graderNums}
            graders={graders}
            value={mappingsByGrader}
            onChange={setMappingsByGrader}
          />
        </Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>
            创建并启动
          </Button>
          <Button onClick={() => navigate(`${EVAL_BASE}/tasks`)}>取消</Button>
        </Space>
      </Form>
    </div>
  );
}
