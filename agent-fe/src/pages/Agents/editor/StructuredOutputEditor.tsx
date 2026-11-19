import { useMemo, useState } from 'react';
import { Button, Input, Radio, Select, message } from 'antd';
import {
  fieldsToSchema,
  schemaToFields,
  type SchemaField,
  type SchemaFieldType,
  type SchemaItemType,
} from './schemaConvert';

const TYPE_OPTIONS: { value: SchemaFieldType; label: string }[] = [
  { value: 'string', label: 'string' },
  { value: 'number', label: 'number' },
  { value: 'integer', label: 'integer' },
  { value: 'boolean', label: 'boolean' },
  { value: 'object', label: 'object' },
  { value: 'array', label: 'array' },
];

const ITEM_OPTIONS: { value: SchemaItemType; label: string }[] = [
  { value: 'string', label: 'string' },
  { value: 'number', label: 'number' },
  { value: 'integer', label: 'integer' },
  { value: 'boolean', label: 'boolean' },
  { value: 'object', label: 'object' },
];

interface Props {
  value?: Record<string, unknown>;
  onChange: (schema: Record<string, unknown>) => void;
}

/** 结构化输出 Schema：字段表和 JSON 文本共用同一份对象。 */
export default function StructuredOutputEditor({ value, onChange }: Props) {
  const initial = useMemo(() => schemaToFields(value ?? { type: 'object', properties: {} }), [value]);
  const [mode, setMode] = useState<'form' | 'json'>(initial.ok ? 'form' : 'json');
  const [fields, setFields] = useState<SchemaField[]>(initial.ok ? initial.fields : []);
  const [text, setText] = useState(() => JSON.stringify(value ?? { type: 'object', properties: {} }, null, 2));
  const [textError, setTextError] = useState<string>();

  const commitFields = (next: SchemaField[]) => {
    setFields(next);
    const schema = fieldsToSchema(next);
    setText(JSON.stringify(schema, null, 2));
    setTextError(undefined);
    onChange(schema);
  };

  const switchMode = (next: 'form' | 'json') => {
    if (next === mode) return;
    if (next === 'json') {
      setText(JSON.stringify(fieldsToSchema(fields), null, 2));
      setTextError(undefined);
      onChange(fieldsToSchema(fields));
      setMode('json');
      return;
    }
    let parsed: unknown = value;
    try {
      parsed = JSON.parse(text);
    } catch {
      message.warning('JSON 还不是合法对象，不能切到字段表');
      return;
    }
    const converted = schemaToFields(parsed);
    if (!converted.ok) {
      message.warning(converted.reason);
      return;
    }
    setFields(converted.fields);
    setMode('form');
  };

  const commitText = (raw: string) => {
    setText(raw);
    try {
      const parsed = JSON.parse(raw) as unknown;
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        setTextError('JSON Schema 必须是对象');
        return;
      }
      setTextError(undefined);
      onChange(parsed as Record<string, unknown>);
      const converted = schemaToFields(parsed);
      if (converted.ok) setFields(converted.fields);
    } catch {
      setTextError('JSON 格式不正确');
    }
  };

  return (
    <div>
      <Radio.Group
        value={mode}
        onChange={(event) => switchMode(event.target.value)}
        style={{ marginBottom: 8 }}
      >
        <Radio.Button value="form">字段表</Radio.Button>
        <Radio.Button value="json">JSON</Radio.Button>
      </Radio.Group>
      {mode === 'form' ? (
        <FieldList fields={fields} onChange={commitFields} />
      ) : (
        <>
          <Input.TextArea
            value={text}
            autoSize={{ minRows: 8, maxRows: 18 }}
            onChange={(event) => commitText(event.target.value)}
          />
          {textError ? (
            <div style={{ marginTop: 6, color: '#cf1322', fontSize: 12 }}>{textError}</div>
          ) : null}
        </>
      )}
    </div>
  );
}

function FieldList({
  fields,
  onChange,
}: {
  fields: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
}) {
  const patch = (index: number, partial: Partial<SchemaField>) => {
    onChange(fields.map((field, i) => (i === index ? { ...field, ...partial } : field)));
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {fields.map((field, index) => (
        <div key={`${field.name}-${index}`} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
          <Input
            placeholder="字段名"
            value={field.name}
            style={{ width: 140 }}
            onChange={(event) => patch(index, { name: event.target.value })}
          />
          <Select
            value={field.type}
            style={{ width: 110 }}
            options={TYPE_OPTIONS}
            onChange={(type) => patch(index, { type })}
          />
          {field.type === 'array' ? (
            <Select
              value={field.itemType ?? 'string'}
              style={{ width: 110 }}
              options={ITEM_OPTIONS}
              onChange={(itemType) => patch(index, { itemType })}
            />
          ) : null}
          <Select
            value={field.required ? 'yes' : 'no'}
            style={{ width: 88 }}
            options={[
              { value: 'yes', label: '必填' },
              { value: 'no', label: '可选' },
            ]}
            onChange={(flag) => patch(index, { required: flag === 'yes' })}
          />
          <Input
            placeholder="说明"
            value={field.description}
            style={{ width: 160 }}
            onChange={(event) => patch(index, { description: event.target.value })}
          />
          <Button
            type="link"
            onClick={() => onChange(fields.filter((_, i) => i !== index))}
          >
            删除
          </Button>
        </div>
      ))}
      {fields.map((field, index) =>
        field.type === 'object' || (field.type === 'array' && (field.itemType ?? 'string') === 'object') ? (
          <div key={`children-${field.name}-${index}`} style={{ marginLeft: 24 }}>
            <div style={{ fontSize: 12, color: '#90A1B9', marginBottom: 4 }}>
              {field.name || '未命名'} 的子字段
            </div>
            <FieldList
              fields={field.children ?? []}
              onChange={(children) => patch(index, { children })}
            />
          </div>
        ) : null,
      )}
      <Button
        style={{ width: 120 }}
        onClick={() =>
          onChange([...fields, { name: '', type: 'string', required: false }])
        }
      >
        添加字段
      </Button>
    </div>
  );
}
