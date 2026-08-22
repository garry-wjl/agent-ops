/**
 * 内置评估器稳定配置：表格展示字段用途 / 取值含义 / 编辑控件。
 * 无参预置展示说明；未知预置回退 JSON 编辑。
 */
import { Form, Input, Select, Switch, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import JsonEditor from '@/components/JsonEditor';
import { COLOR } from '../constants';

const { Text } = Typography;

export type BuiltinConfigFieldType = 'boolean' | 'string' | 'string[]';

export interface BuiltinConfigFieldDef {
  key: string;
  label: string;
  /** 字段用途 */
  purpose: string;
  /** 取值含义 */
  valueMeaning: string;
  type: BuiltinConfigFieldType;
  required?: boolean;
  placeholder?: string;
}

/** 各预置稳定字段定义（与后端 BuiltinGraderEngine 对齐） */
export const PRESET_CONFIG_FIELDS: Record<string, BuiltinConfigFieldDef[]> = {
  NON_EMPTY: [],
  JSON_VALID: [],
  TOOL_CALLED: [],
  EXACT_MATCH: [
    {
      key: 'trim',
      label: 'trim',
      purpose: '对比前是否去掉 response / reference 的首尾空白。',
      valueMeaning: '开启：先 trim 再比；关闭：保留空白参与比较。默认开启。',
      type: 'boolean',
    },
    {
      key: 'ignoreCase',
      label: 'ignoreCase',
      purpose: '精确匹配时是否忽略英文大小写。',
      valueMeaning: '开启：Hello 与 hello 视为相同；关闭：区分大小写。默认关闭。',
      type: 'boolean',
    },
  ],
  CONTAINS: [
    {
      key: 'keywords',
      label: 'keywords',
      purpose: '要求 Agent 输出中必须全部出现的关键词列表。',
      valueMeaning:
        '字符串数组；每一个词都须在 response 中出现才 Pass。留空则无法通过。',
      type: 'string[]',
      required: true,
      placeholder: '输入后回车添加，如：工单号',
    },
  ],
  TOOL_NAME_CONTAINS: [
    {
      key: 'keyword',
      label: 'keyword',
      purpose: '校验轨迹里是否调用了名称包含该片段的工具。',
      valueMeaning:
        '非空字符串；任一工具名包含该片段即 Pass（如 refund 可匹配 refund_order）。',
      type: 'string',
      required: true,
      placeholder: '如：search / refund',
    },
  ],
};

const EMPTY_PRESET_HINT: Record<string, string> = {
  NON_EMPTY: '无需额外配置。运行时检查 response 是否非空即可。',
  JSON_VALID: '无需额外配置。运行时检查 response 是否为合法 JSON 对象或数组。',
  TOOL_CALLED: '无需额外配置。运行时检查轨迹中是否存在任意工具调用。',
};

export function isKnownBuiltinPreset(presetCode?: string): boolean {
  return !!presetCode && Object.prototype.hasOwnProperty.call(PRESET_CONFIG_FIELDS, presetCode);
}

/** 从默认 / 已存 JSON 解析出表单 config 对象 */
export function parseBuiltinConfig(
  presetCode: string | undefined,
  configJson?: string,
): Record<string, unknown> {
  const fields = presetCode ? PRESET_CONFIG_FIELDS[presetCode] : undefined;
  let raw: Record<string, unknown> = {};
  try {
    const parsed = JSON.parse(configJson || '{}') as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      raw = parsed as Record<string, unknown>;
    }
  } catch {
    raw = {};
  }
  if (!fields) return raw;

  const next: Record<string, unknown> = {};
  for (const f of fields) {
    const v = raw[f.key];
    if (f.type === 'boolean') {
      next[f.key] = typeof v === 'boolean' ? v : f.key === 'trim';
    } else if (f.type === 'string[]') {
      if (Array.isArray(v)) {
        next[f.key] = v.map(String).filter(Boolean);
      } else if (typeof v === 'string' && v.trim()) {
        next[f.key] = v
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean);
      } else {
        next[f.key] = [];
      }
    } else {
      next[f.key] = v == null ? '' : String(v);
    }
  }
  return next;
}

/** 将表单 config 序列化为提交用 JSON 字符串 */
export function serializeBuiltinConfig(
  presetCode: string | undefined,
  config: Record<string, unknown> | undefined,
  fallbackJson?: string,
): string {
  if (!presetCode || !isKnownBuiltinPreset(presetCode)) {
    return (fallbackJson || '{}').trim() || '{}';
  }
  const fields = PRESET_CONFIG_FIELDS[presetCode] ?? [];
  if (fields.length === 0) return '{}';

  const out: Record<string, unknown> = {};
  const src = config ?? {};
  for (const f of fields) {
    const v = src[f.key];
    if (f.type === 'boolean') {
      out[f.key] = Boolean(v);
    } else if (f.type === 'string[]') {
      out[f.key] = Array.isArray(v)
        ? v.map(String).map((s) => s.trim()).filter(Boolean)
        : [];
    } else {
      out[f.key] = v == null ? '' : String(v).trim();
    }
  }
  return JSON.stringify(out);
}

function FieldValueControl({ field }: { field: BuiltinConfigFieldDef }) {
  if (field.type === 'boolean') {
    return (
      <Form.Item
        name={['config', field.key]}
        valuePropName="checked"
        style={{ margin: 0 }}
      >
        <Switch checkedChildren="是" unCheckedChildren="否" />
      </Form.Item>
    );
  }
  if (field.type === 'string[]') {
    return (
      <Form.Item
        name={['config', field.key]}
        rules={
          field.required
            ? [
                {
                  validator: async (_, value: string[] | undefined) => {
                    if (!value || value.length === 0) {
                      throw new Error('请至少添加一个关键词');
                    }
                  },
                },
              ]
            : undefined
        }
        style={{ margin: 0 }}
      >
        <Select
          mode="tags"
          tokenSeparators={[',']}
          placeholder={field.placeholder}
          style={{ minWidth: 220 }}
          open={false}
        />
      </Form.Item>
    );
  }
  return (
    <Form.Item
      name={['config', field.key]}
      rules={
        field.required
          ? [{ required: true, message: `请填写 ${field.label}` }]
          : undefined
      }
      style={{ margin: 0 }}
    >
      <Input placeholder={field.placeholder} allowClear />
    </Form.Item>
  );
}

export function BuiltinConfigTable({
  presetCode,
}: {
  presetCode?: string;
}) {
  if (!presetCode) {
    return (
      <Text type="secondary" style={{ fontSize: 13 }}>
        请先选择预置模板
      </Text>
    );
  }

  if (!isKnownBuiltinPreset(presetCode)) {
    return (
      <Form.Item name="configJson" style={{ marginBottom: 0 }}>
        <JsonEditor height={200} />
      </Form.Item>
    );
  }

  const fields = PRESET_CONFIG_FIELDS[presetCode] ?? [];
  const emptyHint = EMPTY_PRESET_HINT[presetCode];

  if (fields.length === 0) {
    return (
      <div
        style={{
          padding: '12px 16px',
          background: COLOR.headerBg,
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          fontSize: 13,
          color: COLOR.textSecondary,
          lineHeight: 1.6,
        }}
      >
        {emptyHint ?? '该预置无需额外配置参数。'}
      </div>
    );
  }

  const columns: ColumnsType<BuiltinConfigFieldDef> = [
    {
      title: '字段',
      dataIndex: 'label',
      width: 120,
      render: (label: string, row) => (
        <Text code style={{ fontSize: 12 }}>
          {label}
          {row.required ? (
            <Text type="danger" style={{ marginLeft: 2 }}>
              *
            </Text>
          ) : null}
        </Text>
      ),
    },
    {
      title: '用途',
      dataIndex: 'purpose',
      render: (t: string) => (
        <span style={{ color: COLOR.textSecondary, fontSize: 13 }}>{t}</span>
      ),
    },
    {
      title: '取值含义',
      dataIndex: 'valueMeaning',
      render: (t: string) => (
        <span style={{ color: COLOR.textMuted, fontSize: 12 }}>{t}</span>
      ),
    },
    {
      title: '值',
      key: 'value',
      width: 260,
      render: (_, row) => <FieldValueControl field={row} />,
    },
  ];

  return (
    <Table
      size="small"
      rowKey="key"
      pagination={false}
      columns={columns}
      dataSource={fields}
      style={{ border: `1px solid ${COLOR.border}`, borderRadius: 8 }}
    />
  );
}
