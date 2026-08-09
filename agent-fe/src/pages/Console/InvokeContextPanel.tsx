/**
 * 调试台调用上下文键值表：本地 localStorage 保存，发送时组装为 context object。
 */
import { useEffect, useState } from 'react';
import { Button, Input, Space, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';

const { Text } = Typography;

const KEY_RE = /^[A-Za-z_][A-Za-z0-9_]*$/;

export interface ContextRow {
  key: string;
  value: string;
}

function storageKey(agentNum: string): string {
  return `agentops.debug.invokeContext.${agentNum}`;
}

export function loadContextRows(agentNum?: string): ContextRow[] {
  if (!agentNum) return [{ key: '', value: '' }];
  try {
    const raw = localStorage.getItem(storageKey(agentNum));
    if (!raw) return [{ key: '', value: '' }];
    const parsed = JSON.parse(raw) as ContextRow[] | Record<string, string>;
    if (Array.isArray(parsed)) {
      return parsed.length > 0 ? parsed : [{ key: '', value: '' }];
    }
    const rows = Object.entries(parsed).map(([key, value]) => ({
      key,
      value: String(value ?? ''),
    }));
    return rows.length > 0 ? rows : [{ key: '', value: '' }];
  } catch {
    return [{ key: '', value: '' }];
  }
}

export function saveContextRows(agentNum: string, rows: ContextRow[]): void {
  localStorage.setItem(storageKey(agentNum), JSON.stringify(rows));
}

/** 组装合法 context；非法 key 抛错文案供 toast */
export function rowsToContext(
  rows: ContextRow[],
): Record<string, string> | undefined {
  const out: Record<string, string> = {};
  for (const row of rows) {
    const k = row.key.trim();
    if (!k && !row.value.trim()) continue;
    if (!KEY_RE.test(k)) {
      throw new Error(`上下文键名非法: ${k || '(空)'}，须匹配 [A-Za-z_][A-Za-z0-9_]*`);
    }
    out[k] = row.value;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

interface Props {
  agentNum?: string;
  rows: ContextRow[];
  onChange: (rows: ContextRow[]) => void;
}

export default function InvokeContextPanel({ agentNum, rows, onChange }: Props) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!agentNum) return;
    saveContextRows(agentNum, rows);
  }, [agentNum, rows]);

  const filled = rows.filter((r) => r.key.trim()).length;

  return (
    <div
      style={{
        marginBottom: 12,
        border: '1px solid #E5E7EB',
        borderRadius: 8,
        background: '#FAFBFC',
      }}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={{
          width: '100%',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '8px 12px',
          border: 'none',
          background: 'transparent',
          cursor: 'pointer',
          textAlign: 'left',
        }}
      >
        <Text style={{ fontSize: 13, color: '#0F172B', fontWeight: 500 }}>
          调用上下文 context
          {filled > 0 ? (
            <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
              {filled} 项
            </Text>
          ) : null}
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {open ? '收起' : '展开'} · 本地保存
        </Text>
      </button>
      {open ? (
        <div style={{ padding: '0 12px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            键名匹配 [A-Za-z_][A-Za-z0-9_]*，发送时注入系统提示词 {'{{key}}'}，并合并进会话默认上下文。
          </Text>
          {rows.map((row, idx) => (
            <Space key={idx} style={{ width: '100%' }} align="start">
              <Input
                placeholder="key"
                value={row.key}
                onChange={(e) => {
                  const next = [...rows];
                  next[idx] = { ...row, key: e.target.value };
                  onChange(next);
                }}
                style={{ width: 160 }}
                status={
                  row.key.trim() && !KEY_RE.test(row.key.trim()) ? 'error' : undefined
                }
              />
              <Input
                placeholder="value"
                value={row.value}
                onChange={(e) => {
                  const next = [...rows];
                  next[idx] = { ...row, value: e.target.value };
                  onChange(next);
                }}
                style={{ flex: 1, minWidth: 200 }}
              />
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => {
                  const next = rows.filter((_, i) => i !== idx);
                  onChange(next.length > 0 ? next : [{ key: '', value: '' }]);
                }}
              />
            </Space>
          ))}
          <Button
            type="dashed"
            size="small"
            icon={<PlusOutlined />}
            onClick={() => onChange([...rows, { key: '', value: '' }])}
            style={{ alignSelf: 'flex-start' }}
          >
            添加键值
          </Button>
          {!agentNum ? (
            <Text type="warning" style={{ fontSize: 12 }}>
              请先选择 Agent，上下文才会按 Agent 本地保存。
            </Text>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

/** 供外部发送前校验；失败 toast 并返回 undefined */
export function tryBuildContext(rows: ContextRow[]): Record<string, string> | undefined {
  try {
    return rowsToContext(rows);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上下文非法');
    return undefined;
  }
}
