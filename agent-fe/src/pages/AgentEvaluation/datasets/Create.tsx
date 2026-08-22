/**
 * 创建 / 编辑评测集 — `/agent/evaluation/datasets/new` | `.../:num/edit`
 * 编辑仅支持改名称、描述、用例字段定义（类型与 Agent 只读）；走 updateDraft。
 */
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Form,
  Input,
  Radio,
  Select,
  Space,
  Typography,
  message,
} from 'antd';
import { evalApi } from '@/services/evaluation';
import { agentApi } from '@/services/agent';
import type { AgentVO, DatasetType } from '@/types';
import {
  DEFAULT_DATASET_SCHEMA_JSON,
  buildAgentDatasetSchema,
  prettyJson,
} from '@/types';
import JsonEditor from '@/components/JsonEditor';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import { COLOR, EVAL_BASE } from '../constants';
import {
  buildUniqueEvalName,
  isAutoDatasetName,
} from '../suggestEvalName';

const { Title, Text } = Typography;

export default function DatasetCreatePage() {
  const navigate = useNavigate();
  const { num: editNum } = useParams<{ num?: string }>();
  const isEdit = Boolean(editNum);

  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(isEdit);
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [existingNames, setExistingNames] = useState<string[]>([]);
  const [schemaEditorKey, setSchemaEditorKey] = useState(0);
  const type = Form.useWatch('type', form) as DatasetType | undefined;
  const agentNum = Form.useWatch('agentNum', form) as string | undefined;

  useBreadcrumbName(isEdit ? '编辑评测集' : '新建评测集');

  useEffect(() => {
    void agentApi
      .pageList({ pageNo: 1, pageSize: 100 })
      .then((ag) => setAgents(ag?.list ?? []))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (isEdit) return;
    void evalApi
      .pageDatasets({ pageNo: 1, pageSize: 100 })
      .then((ds) => {
        const names = (ds?.list ?? []).map((d) => d.name).filter(Boolean);
        setExistingNames(names);
        const current = form.getFieldValue('name') as string | undefined;
        if (!current?.trim() || isAutoDatasetName(current)) {
          form.setFieldValue('name', buildUniqueEvalName('评测集', names));
        }
      })
      .catch(() => {
        if (!form.getFieldValue('name')) {
          form.setFieldValue('name', buildUniqueEvalName('评测集', []));
        }
      });
  }, [form, isEdit]);

  useEffect(() => {
    if (!isEdit || !editNum) return;
    setLoadingDetail(true);
    void evalApi
      .datasetDetail(editNum)
      .then((d) => {
        form.setFieldsValue({
          name: d.name,
          description: d.description,
          type: d.type,
          agentNum: d.agentNum,
          schemaJson: prettyJson(d.schemaJson || DEFAULT_DATASET_SCHEMA_JSON),
        });
        setSchemaEditorKey((k) => k + 1);
      })
      .catch(() => message.error('加载评测集失败'))
      .finally(() => setLoadingDetail(false));
  }, [isEdit, editNum, form]);

  const suggestNameForAgent = (num?: string, agentDisplayName?: string) => {
    if (isEdit) return;
    const current = form.getFieldValue('name') as string | undefined;
    if (current && !isAutoDatasetName(current)) return;
    const agentName =
      agentDisplayName?.trim() ||
      (num ? agents.find((a) => a.num === num)?.name?.trim() : undefined);
    const prefix = agentName ? `${agentName} 评测集` : '评测集';
    form.setFieldValue(
      'name',
      buildUniqueEvalName(prefix, existingNames),
    );
  };

  const applyAgentSchema = async (num?: string) => {
    if (isEdit) return;
    if (!num) {
      suggestNameForAgent(undefined);
      form.setFieldValue('schemaJson', '');
      setSchemaEditorKey((k) => k + 1);
      return;
    }
    setSchemaLoading(true);
    try {
      const detail = await agentApi.detail(num);
      suggestNameForAgent(
        num,
        detail.name || agents.find((a) => a.num === num)?.name,
      );
      let systemPrompt = detail.currentVersion?.configSnapshot?.systemPrompt;
      if (!systemPrompt && detail.currentVersionNum) {
        try {
          const vd = await agentApi.versionDetail(num, detail.currentVersionNum);
          systemPrompt = vd.configSnapshot?.systemPrompt;
        } catch {
          /* ignore */
        }
      }
      if (!systemPrompt) {
        try {
          const list = await agentApi.versionList(num);
          const online =
            list.find((v) => v.current) ||
            list.find((v) => String(v.status).toUpperCase() === 'PUBLISHED');
          if (online?.num) {
            const vd = await agentApi.versionDetail(num, online.num);
            systemPrompt = vd.configSnapshot?.systemPrompt;
          }
        } catch {
          /* ignore */
        }
      }
      const schema = buildAgentDatasetSchema({
        agentNum: num,
        agentName: detail.name || agents.find((a) => a.num === num)?.name,
        systemPrompt,
      });
      form.setFieldValue('schemaJson', schema);
      setSchemaEditorKey((k) => k + 1);
      message.success(
        systemPrompt && /\{\{[A-Za-z_]/.test(systemPrompt)
          ? `已带出 Agent「${detail.name || num}」用例字段定义（含提示词变量）`
          : `已带出 Agent「${detail.name || num}」用例字段定义`,
      );
    } catch {
      suggestNameForAgent(
        num,
        agents.find((a) => a.num === num)?.name,
      );
      const fallback = buildAgentDatasetSchema({
        agentNum: num,
        agentName: agents.find((a) => a.num === num)?.name,
      });
      form.setFieldValue('schemaJson', fallback);
      setSchemaEditorKey((k) => k + 1);
      message.warning('拉取 Agent 详情失败，已填入基础调用参数用例字段定义');
    } finally {
      setSchemaLoading(false);
    }
  };

  const onFinish = async (values: {
    name: string;
    description?: string;
    type: DatasetType;
    agentNum?: string;
    schemaJson?: string;
  }) => {
    setSubmitting(true);
    try {
      const schemaJson = (values.schemaJson || DEFAULT_DATASET_SCHEMA_JSON).trim();
      if (isEdit && editNum) {
        await evalApi.updateDatasetDraft({
          num: editNum,
          name: values.name.trim(),
          description: values.description?.trim(),
          schemaJson,
        });
        message.success('评测集已保存');
        navigate(`${EVAL_BASE}/datasets/${editNum}`);
        return;
      }
      const res = await evalApi.createDataset({
        name: values.name.trim(),
        description: values.description?.trim(),
        type: values.type,
        agentNum: values.type === 'AGENT' ? values.agentNum : undefined,
        schemaJson,
      });
      message.success('评测集已创建');
      navigate(`${EVAL_BASE}/datasets/${res.num}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%', maxWidth: 800 }}>
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/datasets`}
        moduleName="Agent 评测"
        current={isEdit ? '编辑评测集' : '新建评测集'}
      />
      <Title
        level={3}
        style={{ margin: '0 0 4px', color: COLOR.textPrimary, fontWeight: 700 }}
      >
        {isEdit ? '编辑评测集' : '创建评测集'}
      </Title>
      <Text style={{ color: COLOR.textSecondary, display: 'block', marginBottom: 24 }}>
        {isEdit
          ? '可修改名称、描述与用例字段定义；类型与关联 Agent 创建后不可改。改用例字段定义主要影响后续草稿行录入。'
          : '定义表结构并创建草稿；随后可导入 xlsx 或手动增删行并发布版本'}
      </Text>

      <Form
        form={form}
        layout="vertical"
        initialValues={
          isEdit
            ? undefined
            : {
                type: 'AGENT',
                schemaJson: '',
                name: buildUniqueEvalName('评测集', []),
              }
        }
        onFinish={onFinish}
        disabled={loadingDetail}
      >
        <Form.Item
          name="name"
          label="名称"
          rules={[{ required: true, message: '请输入名称' }]}
          extra={isEdit ? undefined : '已自动填充唯一名称，可直接改成业务名'}
        >
          <Input placeholder="如：客服回归集" maxLength={64} />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} maxLength={256} placeholder="可选" />
        </Form.Item>
        <Form.Item name="type" label="类型" rules={[{ required: true }]}>
          <Radio.Group
            disabled={isEdit}
            onChange={(e) => {
              if (isEdit) return;
              const next = e.target.value as DatasetType;
              if (next === 'CUSTOM') {
                form.setFieldValue(
                  'schemaJson',
                  prettyJson(DEFAULT_DATASET_SCHEMA_JSON),
                );
                form.setFieldValue('agentNum', undefined);
                setSchemaEditorKey((k) => k + 1);
              } else {
                form.setFieldValue('schemaJson', '');
                form.setFieldValue('agentNum', undefined);
                setSchemaEditorKey((k) => k + 1);
              }
            }}
          >
            <Radio value="AGENT">AGENT（关联智能体）</Radio>
            <Radio value="CUSTOM">CUSTOM（自定义）</Radio>
          </Radio.Group>
        </Form.Item>
        {type === 'AGENT' && (
          <Form.Item
            name="agentNum"
            label="关联 Agent"
            rules={
              isEdit
                ? undefined
                : [{ required: true, message: '请选择 Agent' }]
            }
            extra={
              isEdit
                ? '创建后不可修改关联 Agent'
                : '选择后自动拉取该 Agent 调用参数作为用例字段定义（input / reference / context；若有 {{变量}} 会写入 context.properties）'
            }
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择 Agent"
              loading={schemaLoading}
              disabled={isEdit}
              options={agents.map((a) => ({
                value: a.num,
                label: `${a.name || a.num} (${a.num})`,
              }))}
              onChange={(v) => void applyAgentSchema(v)}
            />
          </Form.Item>
        )}
        <Form.Item
          name="schemaJson"
          label="用例字段定义"
          rules={[
            {
              required: true,
              message:
                !isEdit && type === 'AGENT' && !agentNum
                  ? '请先选择关联 Agent，以自动带出用例字段定义'
                  : '请填写用例字段定义',
            },
            {
              validator: async (_, v) => {
                const raw = String(v || '').trim();
                if (!raw) {
                  throw new Error(
                    !isEdit && type === 'AGENT'
                      ? '请先选择关联 Agent'
                      : '请填写用例字段定义',
                  );
                }
                try {
                  const parsed = JSON.parse(raw);
                  if (!Array.isArray(parsed)) {
                    throw new Error('须为数组');
                  }
                } catch (e) {
                  if (e instanceof Error && e.message.includes('请先')) {
                    throw e;
                  }
                  throw new Error('用例字段定义须为合法 JSON 数组');
                }
              },
            },
          ]}
          extra={
            !isEdit && type === 'AGENT' && !agentNum
              ? '请先选择上方关联 Agent，用例字段定义将自动填入'
              : '字段定义 JSON 数组，可继续手工调整'
          }
        >
          <JsonEditor key={schemaEditorKey} height={280} />
        </Form.Item>
        <Space>
          <Button
            type="primary"
            htmlType="submit"
            loading={submitting || schemaLoading || loadingDetail}
          >
            {isEdit ? '保存' : '创建'}
          </Button>
          <Button
            onClick={() =>
              navigate(
                isEdit && editNum
                  ? `${EVAL_BASE}/datasets/${editNum}`
                  : `${EVAL_BASE}/datasets`,
              )
            }
          >
            取消
          </Button>
        </Space>
      </Form>
    </div>
  );
}
