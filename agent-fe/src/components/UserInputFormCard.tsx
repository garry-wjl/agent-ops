/**
 * 调试台 HITL 用户表单 — SSE user_input.required 触发展示；提交后可只读回放。
 */
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import type { PendingUserInput, UserInputFieldDef } from '@/types';

export type UserFormCardStatus = 'pending' | 'submitted' | 'cancelled';

export interface UserInputFormCardProps {
  pending: PendingUserInput;
  /** pending：可编辑；submitted/cancelled：只读回放 */
  status?: UserFormCardStatus;
  /** 已提交的值（只读态展示） */
  values?: Record<string, unknown>;
  submitting?: boolean;
  onSubmit?: (values: Record<string, unknown>) => void | Promise<void>;
  onCancel?: () => void | Promise<void>;
}

function buildInitialValues(
  fields: UserInputFieldDef[],
  values?: Record<string, unknown>,
): Record<string, unknown> {
  const init: Record<string, unknown> = {};
  for (const f of fields) {
    const raw = values && values[f.name] !== undefined ? values[f.name] : f.default;
    if (raw === undefined) continue;
    if (f.type === 'date' && typeof raw === 'string') {
      const d = dayjs(raw);
      if (d.isValid()) init[f.name] = d;
      continue;
    }
    init[f.name] = raw;
  }
  return init;
}

/** 只读态把值格式化成可读字符串，避免 Ant Form initialValues 在 disabled 控件上偶发空白 */
function formatReadOnlyValue(f: UserInputFieldDef, raw: unknown): string {
  if (raw === undefined || raw === null || raw === '') return '—';
  if (f.type === 'boolean') return raw === true || raw === 'true' ? '是' : '否';
  if (f.type === 'multi_enum' && Array.isArray(raw)) {
    return raw.length ? raw.map(String).join('、') : '—';
  }
  if (f.type === 'enum' && Array.isArray(f.options)) {
    const hit = f.options.find((o) =>
      typeof o === 'string' ? o === String(raw) : o.value === String(raw),
    );
    if (typeof hit === 'string') return hit;
    if (hit && typeof hit === 'object') return hit.label ?? hit.value;
  }
  return String(raw);
}

function fieldRules(f: UserInputFieldDef, readOnly: boolean) {
  if (readOnly || !f.required) return [];
  return [{ required: true, message: `请填写${f.label ?? f.name}` }];
}

function renderEditableField(f: UserInputFieldDef) {
  const label = f.label ?? f.name;
  const rules = fieldRules(f, false);
  switch (f.type) {
    case 'textarea':
      return (
        <Form.Item key={f.name} name={f.name} label={label} rules={rules} extra={f.description}>
          <Input.TextArea rows={4} placeholder={f.placeholder} />
        </Form.Item>
      );
    case 'number':
      return (
        <Form.Item key={f.name} name={f.name} label={label} rules={rules} extra={f.description}>
          <InputNumber style={{ width: '100%' }} placeholder={f.placeholder} />
        </Form.Item>
      );
    case 'boolean':
      return (
        <Form.Item
          key={f.name}
          name={f.name}
          label={label}
          valuePropName="checked"
          rules={rules}
          extra={f.description}
        >
          <Switch />
        </Form.Item>
      );
    case 'date':
      return (
        <Form.Item key={f.name} name={f.name} label={label} rules={rules} extra={f.description}>
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
      );
    case 'enum': {
      const options = (f.options ?? []).map((o) =>
        typeof o === 'string' ? { value: o, label: o } : { value: o.value, label: o.label ?? o.value },
      );
      return (
        <Form.Item key={f.name} name={f.name} label={label} rules={rules} extra={f.description}>
          <Select allowClear placeholder={f.placeholder ?? '请选择'} options={options} />
        </Form.Item>
      );
    }
    case 'multi_enum': {
      const options = (f.options ?? []).map((o) =>
        typeof o === 'string' ? { value: o, label: o } : { value: o.value, label: o.label ?? o.value },
      );
      return (
        <Form.Item key={f.name} name={f.name} label={label} rules={rules} extra={f.description}>
          <Select
            mode="multiple"
            allowClear
            placeholder={f.placeholder ?? '请选择'}
            options={options}
          />
        </Form.Item>
      );
    }
    case 'string':
    default:
      return (
        <Form.Item key={f.name} name={f.name} label={label} rules={rules} extra={f.description}>
          <Input placeholder={f.placeholder} />
        </Form.Item>
      );
  }
}

function renderReadOnlyField(
  f: UserInputFieldDef,
  values?: Record<string, unknown>,
) {
  const label = f.label ?? f.name;
  const raw = values && values[f.name] !== undefined ? values[f.name] : f.default;
  return (
    <Form.Item key={f.name} label={label} extra={f.description}>
      <Input disabled value={formatReadOnlyValue(f, raw)} />
    </Form.Item>
  );
}

function normalizeSubmitValues(
  fields: UserInputFieldDef[],
  raw: Record<string, unknown>,
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...raw };
  for (const f of fields) {
    const v = out[f.name];
    if (f.type === 'date' && v != null && dayjs.isDayjs(v)) {
      out[f.name] = (v as dayjs.Dayjs).format('YYYY-MM-DD');
    }
  }
  return out;
}

function statusTag(status: UserFormCardStatus) {
  if (status === 'submitted') return <Tag color="success">已提交</Tag>;
  if (status === 'cancelled') return <Tag>已取消</Tag>;
  return <Tag color="warning">待填写</Tag>;
}

export default function UserInputFormCard({
  pending,
  status = 'pending',
  values,
  submitting,
  onSubmit,
  onCancel,
}: UserInputFormCardProps) {
  const [form] = Form.useForm();
  const readOnly = status !== 'pending';
  const fields = pending.fields ?? [];

  return (
    <Card
      size="small"
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          {pending.title ?? '请填写表单'}
          {statusTag(status)}
        </span>
      }
      extra={
        !readOnly && onCancel ? (
          <Button type="text" size="small" disabled={submitting} onClick={() => void onCancel()}>
            关闭
          </Button>
        ) : null
      }
      styles={{ body: { paddingTop: 12 } }}
      style={{
        borderRadius: 12,
        border: '1px solid #E5E7EB',
        background: readOnly ? '#F8FAFC' : '#FFFBEB',
        marginTop: 12,
      }}
    >
      {pending.description ? (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 13 }}>
          {pending.description}
        </Typography.Paragraph>
      ) : null}
      {readOnly ? (
        <Form layout="vertical">
          {fields.map((f) => renderReadOnlyField(f, values))}
        </Form>
      ) : (
        <Form
          key={`${pending.requestId}-${status}`}
          form={form}
          layout="vertical"
          initialValues={buildInitialValues(fields, values)}
          onFinish={async (vals) => {
            if (!onSubmit) return;
            await onSubmit(normalizeSubmitValues(fields, vals));
          }}
        >
          {fields.map((f) => renderEditableField(f))}
          <Form.Item style={{ marginBottom: 0 }}>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button disabled={submitting} onClick={() => void onCancel?.()}>
                取消
              </Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                提交
              </Button>
            </div>
          </Form.Item>
        </Form>
      )}
    </Card>
  );
}
