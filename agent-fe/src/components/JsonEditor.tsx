/**
 * JSON Monaco 编辑器：支持格式化，可接入 Form.Item（value / onChange）。
 * readOnly 时仍可点「格式化」本地美化展示（不回写父组件）。
 */
import { useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Button, Space, message } from 'antd';
import { FormatPainterOutlined } from '@ant-design/icons';

const MONACO_OPTIONS = {
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 13,
  tabSize: 2,
  wordWrap: 'on' as const,
  lineNumbers: 'on' as const,
  folding: true,
  automaticLayout: true,
};

export interface JsonEditorProps {
  value?: string;
  onChange?: (value: string) => void;
  height?: number | string;
  readOnly?: boolean;
  /** 格式化失败时是否 toast，默认 true */
  toastOnError?: boolean;
}

export default function JsonEditor({
  value = '',
  onChange,
  height = 200,
  readOnly = false,
  toastOnError = true,
}: JsonEditorProps) {
  const [localOverride, setLocalOverride] = useState<string | null>(null);
  const displayValue = localOverride ?? value ?? '';

  useEffect(() => {
    setLocalOverride(null);
  }, [value]);

  const handleFormat = () => {
    const raw = displayValue.trim();
    if (!raw) {
      const empty = '{}';
      if (readOnly && !onChange) {
        setLocalOverride(empty);
      } else {
        onChange?.(empty);
      }
      return;
    }
    try {
      const formatted = JSON.stringify(JSON.parse(raw), null, 2);
      if (readOnly && !onChange) {
        setLocalOverride(formatted);
      } else {
        onChange?.(formatted);
      }
      message.success('已格式化');
    } catch {
      if (toastOnError) {
        message.error('JSON 无法解析，请检查格式');
      }
    }
  };

  return (
    <div
      style={{
        border: '1px solid #E2E8F0',
        borderRadius: 8,
        overflow: 'hidden',
        background: '#fff',
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          padding: '4px 8px',
          borderBottom: '1px solid #E2E8F0',
          background: '#fff',
        }}
      >
        <Space size={4}>
          <Button
            type="text"
            size="small"
            icon={<FormatPainterOutlined />}
            onClick={handleFormat}
          >
            格式化
          </Button>
        </Space>
      </div>
      <Editor
        height={height}
        defaultLanguage="json"
        language="json"
        value={displayValue}
        onChange={(v) => {
          if (readOnly) return;
          setLocalOverride(null);
          onChange?.(v ?? '');
        }}
        options={{
          ...MONACO_OPTIONS,
          readOnly,
        }}
      />
    </div>
  );
}
