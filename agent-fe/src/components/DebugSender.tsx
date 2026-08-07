/**
 * DebugSender — Figma 节点 132:94 还原
 * 单一输入框 + 底部提示行 + 右下双按钮（停止 / 发送）
 * JSON 模式由外部 prop 控制（SubToolbar 顶部 ⌘J toggle）；JSON 模式下用 Monaco 替换 textarea
 */
import { Button, Input } from 'antd';
import { CaretRightFilled, PauseOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { useEffect, useMemo, useState } from 'react';
import { safeJsonParse } from '@/utils/format';

export interface DebugSenderProps {
  loading: boolean;
  disabled?: boolean;
  /** 输入模式 — 由 SubToolbar 顶部 toggle 控制 */
  mode: 'text' | 'json';
  /** 选中 Skill 时（M2+），用于自动加载 input schema 作为校验/模板 */
  skillHint?: string;
  onSubmit: (
    input: string | Record<string, any>,
    inputType: 'text' | 'json',
  ) => void;
  onCancel?: () => void;
}

const COLOR = {
  border: '#E5E7EB',
  textPrimary: '#0F172B',
  textMuted: '#94A3B8',
  textSecondary: '#64748B',
  primary: '#3B82F6',
  buttonBg: '#F8FAFC',
} as const;

export default function DebugSender(props: DebugSenderProps) {
  const { loading, disabled, mode, skillHint, onSubmit, onCancel } = props;
  const [text, setText] = useState('');
  const [json, setJson] = useState('{\n  \n}');

  useEffect(() => {
    if (!skillHint) return;
    // v2.0：Skill 已无结构化 schema，JSON 模板自动生成功能下线；保留入参输入由用户自行填写
  }, [skillHint, mode]);

  const jsonInvalid = useMemo(() => {
    if (mode !== 'json') return false;
    return !safeJsonParse(json);
  }, [mode, json]);

  const handleSubmit = () => {
    if (disabled || loading) return;
    if (mode === 'text') {
      if (!text.trim()) {
        return;
      }
      onSubmit(text, 'text');
      setText('');
    } else {
      const parsed = safeJsonParse<Record<string, any>>(json);
      if (!parsed) return;
      onSubmit(parsed, 'json');
    }
  };

  return (
    <div
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 12,
        background: '#fff',
        padding: '12px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      {/* 输入区 */}
      {mode === 'text' ? (
        <Input.TextArea
          variant="borderless"
          autoSize={{ minRows: 1, maxRows: 6 }}
          value={text}
          disabled={disabled}
          onChange={(e) => setText(e.target.value)}
          onPressEnter={(e) => {
            if (e.shiftKey) return;
            e.preventDefault();
            handleSubmit();
          }}
          placeholder="继续追问，或粘贴正文..."
          style={{
            padding: 0,
            fontSize: 13,
            color: COLOR.textPrimary,
            resize: 'none',
            boxShadow: 'none',
          }}
        />
      ) : (
        <div
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 6,
            overflow: 'hidden',
          }}
        >
          <Editor
            height="180px"
            defaultLanguage="json"
            value={json}
            onChange={(v) => setJson(v ?? '')}
            options={{
              minimap: { enabled: false },
              scrollBeyondLastLine: false,
              fontSize: 12,
              tabSize: 2,
              wordWrap: 'on',
              lineNumbers: 'off',
              folding: false,
            }}
          />
        </div>
      )}

      {/* 底部提示 + 操作按钮 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <span
          style={{
            fontSize: 11,
            color: COLOR.textMuted,
            fontWeight: 400,
          }}
        >
          {mode === 'json' && jsonInvalid
            ? 'JSON 格式不合法'
            : 'Shift+Enter 换行 · ⌘J 切 JSON · 当前输入将作为 input.prompt 字段'}
        </span>
        <div style={{ display: 'flex', gap: 8 }}>
          {loading ? (
            <Button
              icon={<PauseOutlined />}
              onClick={onCancel}
            >
              停止
            </Button>
          ) : null}
          <Button
            type="primary"
            icon={<CaretRightFilled />}
            onClick={handleSubmit}
          >
            发送
          </Button>
        </div>
      </div>
    </div>
  );
}
