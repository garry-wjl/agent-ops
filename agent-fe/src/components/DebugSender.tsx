/**
 * DebugSender — Figma 节点 132:94 还原
 * 单一输入框 + 附件（选文件 / 粘贴 / 拖拽）+ 底部提示 + 右下双按钮
 * JSON 模式由外部 prop 控制；JSON 模式下暂不挂附件（与 BE multimodal 文本路径对齐）
 */
import { Button, Input, Tooltip, message as antdMessage } from 'antd';
import {
  CaretRightFilled,
  CloseOutlined,
  PaperClipOutlined,
  PauseOutlined,
} from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { uploadFile } from '@/services';
import type { AttachmentRef, PendingAttachment } from '@/types';
import { safeJsonParse } from '@/utils/format';
import {
  formatFileSize,
  inferAttachmentKind,
} from '@/utils/multimodalContent';

const MAX_ATTACHMENTS = 6;
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

const ALLOWED_MIME = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
  'image/gif',
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'text/plain',
  'text/markdown',
]);

const ACCEPT =
  '.png,.jpg,.jpeg,.webp,.gif,.pdf,.docx,.xlsx,.txt,.md,image/png,image/jpeg,image/webp,image/gif,application/pdf,text/plain,text/markdown';

export interface DebugSenderProps {
  loading: boolean;
  disabled?: boolean;
  /** 当前调试 Agent，上传登记用 */
  agentNum?: string;
  /** 输入模式 — 由 SubToolbar 顶部 toggle 控制 */
  mode: 'text' | 'json';
  /** 选中 Skill 时（M2+），用于自动加载 input schema 作为校验/模板 */
  skillHint?: string;
  onSubmit: (
    input: string | Record<string, any>,
    inputType: 'text' | 'json' | 'multimodal',
    attachments?: AttachmentRef[],
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
  danger: '#DC2626',
} as const;

function resolveMime(file: File): string {
  if (file.type) return file.type;
  const n = file.name.toLowerCase();
  if (n.endsWith('.md')) return 'text/markdown';
  if (n.endsWith('.txt')) return 'text/plain';
  if (n.endsWith('.pdf')) return 'application/pdf';
  if (n.endsWith('.docx')) {
    return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  }
  if (n.endsWith('.xlsx')) {
    return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  }
  if (n.endsWith('.png')) return 'image/png';
  if (n.endsWith('.jpg') || n.endsWith('.jpeg')) return 'image/jpeg';
  if (n.endsWith('.webp')) return 'image/webp';
  if (n.endsWith('.gif')) return 'image/gif';
  return 'application/octet-stream';
}

export default function DebugSender(props: DebugSenderProps) {
  const { loading, disabled, agentNum, mode, skillHint, onSubmit, onCancel } =
    props;
  const [text, setText] = useState('');
  const [json, setJson] = useState('{\n  \n}');
  const [attachments, setAttachments] = useState<PendingAttachment[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!skillHint) return;
  }, [skillHint, mode]);

  useEffect(() => {
    return () => {
      attachments.forEach((a) => {
        if (a.previewUrl) URL.revokeObjectURL(a.previewUrl);
      });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const jsonInvalid = useMemo(() => {
    if (mode !== 'json') return false;
    return !safeJsonParse(json);
  }, [mode, json]);

  const uploading = attachments.some((a) => a.uploading);
  const hasReadyAttachments = attachments.some(
    (a) => a.fileId && !a.uploading && !a.error,
  );

  const addFiles = useCallback(
    async (files: FileList | File[]) => {
      if (disabled || loading || mode === 'json') return;
      const list = Array.from(files);
      if (!list.length) return;

      const room = MAX_ATTACHMENTS - attachments.length;
      if (room <= 0) {
        antdMessage.warning(`最多 ${MAX_ATTACHMENTS} 个附件`);
        return;
      }
      const slice = list.slice(0, room);
      if (list.length > room) {
        antdMessage.warning(`最多 ${MAX_ATTACHMENTS} 个附件，已截取前 ${room} 个`);
      }

      for (const file of slice) {
        const mime = resolveMime(file);
        if (!ALLOWED_MIME.has(mime)) {
          antdMessage.error(`不支持的类型: ${file.name}`);
          continue;
        }
        if (file.size > MAX_SIZE_BYTES) {
          antdMessage.error(`文件过大（上限 10MB）: ${file.name}`);
          continue;
        }

        const localId = `pending-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
        const kind = inferAttachmentKind(mime, file.name);
        const previewUrl =
          kind === 'image' ? URL.createObjectURL(file) : undefined;
        const pending: PendingAttachment = {
          fileId: localId,
          name: file.name,
          mimeType: mime,
          size: file.size,
          kind,
          previewUrl,
          uploading: true,
        };
        setAttachments((prev) => [...prev, pending]);

        try {
          const chatName = `chat/${Date.now()}_${file.name.replace(/[^\w.\u4e00-\u9fa5-]/g, '_')}`;
          const result = await uploadFile({
            file,
            fileName: chatName,
            mimeType: mime,
            sizeBytes: file.size,
            agentNum,
          });
          setAttachments((prev) =>
            prev.map((a) =>
              a.fileId === localId
                ? {
                    ...a,
                    fileId: result.fileId,
                    uploading: false,
                    error: undefined,
                  }
                : a,
            ),
          );
        } catch (e) {
          const msg = e instanceof Error ? e.message : '上传失败';
          setAttachments((prev) =>
            prev.map((a) =>
              a.fileId === localId
                ? { ...a, uploading: false, error: msg }
                : a,
            ),
          );
          antdMessage.error(`${file.name}: ${msg}`);
        }
      }
    },
    [agentNum, attachments.length, disabled, loading, mode],
  );

  const removeAttachment = (fileId: string) => {
    setAttachments((prev) => {
      const target = prev.find((a) => a.fileId === fileId);
      if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((a) => a.fileId !== fileId);
    });
  };

  const handleSubmit = () => {
    if (disabled || loading || uploading) return;
    if (mode === 'json') {
      const parsed = safeJsonParse<Record<string, any>>(json);
      if (!parsed) return;
      onSubmit(parsed, 'json');
      return;
    }

    const ready = attachments.filter(
      (a) => a.fileId && !a.uploading && !a.error && !a.fileId.startsWith('pending-'),
    );
    const textTrim = text.trim();
    if (!textTrim && ready.length === 0) return;

    const refs: AttachmentRef[] = ready.map((a) => ({
      fileId: a.fileId,
      name: a.name,
      mimeType: a.mimeType,
      size: a.size,
      kind: a.kind,
    }));
    const inputType = refs.length > 0 ? 'multimodal' : 'text';
    onSubmit(textTrim, inputType, refs.length ? refs : undefined);
    setText('');
    attachments.forEach((a) => {
      if (a.previewUrl) URL.revokeObjectURL(a.previewUrl);
    });
    setAttachments([]);
  };

  const onPaste = (e: React.ClipboardEvent) => {
    if (mode !== 'text') return;
    const items = e.clipboardData?.files;
    if (items && items.length > 0) {
      e.preventDefault();
      void addFiles(items);
    }
  };

  return (
    <div
      onDragEnter={(e) => {
        e.preventDefault();
        if (mode === 'text') setDragOver(true);
      }}
      onDragOver={(e) => {
        e.preventDefault();
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragOver(false);
        if (e.dataTransfer.files?.length) void addFiles(e.dataTransfer.files);
      }}
      style={{
        border: `1px solid ${dragOver ? COLOR.primary : COLOR.border}`,
        borderRadius: 12,
        background: '#fff',
        padding: '12px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        boxShadow: dragOver ? `0 0 0 2px ${COLOR.primary}22` : undefined,
      }}
    >
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept={ACCEPT}
        style={{ display: 'none' }}
        onChange={(e) => {
          if (e.target.files) void addFiles(e.target.files);
          e.target.value = '';
        }}
      />

      {mode === 'text' && attachments.length > 0 ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {attachments.map((a) => (
            <div
              key={a.fileId}
              style={{
                position: 'relative',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: a.kind === 'image' && a.previewUrl ? 0 : '6px 10px',
                borderRadius: 8,
                border: `1px solid ${a.error ? COLOR.danger : COLOR.border}`,
                fontSize: 12,
                opacity: a.uploading ? 0.7 : 1,
                overflow: 'hidden',
              }}
            >
              {a.kind === 'image' && a.previewUrl ? (
                <img
                  src={a.previewUrl}
                  alt={a.name}
                  style={{ width: 56, height: 56, objectFit: 'cover', display: 'block' }}
                />
              ) : (
                <span style={{ color: COLOR.textPrimary }}>
                  {a.name}
                  {a.size != null ? ` · ${formatFileSize(a.size)}` : ''}
                  {a.uploading ? ' · 上传中…' : ''}
                  {a.error ? ` · ${a.error}` : ''}
                </span>
              )}
              <button
                type="button"
                onClick={() => removeAttachment(a.fileId)}
                style={{
                  position: a.kind === 'image' && a.previewUrl ? 'absolute' : 'static',
                  top: 2,
                  right: 2,
                  border: 'none',
                  background: 'rgba(15,23,43,0.55)',
                  color: '#fff',
                  borderRadius: 999,
                  width: 18,
                  height: 18,
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: 0,
                  fontSize: 10,
                }}
              >
                <CloseOutlined />
              </button>
            </div>
          ))}
        </div>
      ) : null}

      {mode === 'text' ? (
        <Input.TextArea
          variant="borderless"
          autoSize={{ minRows: 1, maxRows: 6 }}
          value={text}
          disabled={disabled}
          onChange={(e) => setText(e.target.value)}
          onPaste={onPaste}
          onPressEnter={(e) => {
            if (e.shiftKey) return;
            e.preventDefault();
            handleSubmit();
          }}
          placeholder="继续追问，或粘贴图片 / 拖入文件…"
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

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          {mode === 'text' ? (
            <Tooltip title="上传图片或文档（png/jpg/webp/gif/pdf/docx/xlsx/txt/md）">
              <Button
                type="text"
                size="small"
                icon={<PaperClipOutlined />}
                disabled={disabled || loading || attachments.length >= MAX_ATTACHMENTS}
                onClick={() => fileInputRef.current?.click()}
              />
            </Tooltip>
          ) : null}
          <span
            style={{
              fontSize: 11,
              color: COLOR.textMuted,
              fontWeight: 400,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {mode === 'json' && jsonInvalid
              ? 'JSON 格式不合法'
              : mode === 'json'
                ? 'Shift+Enter 换行 · ⌘J 切文本'
                : hasReadyAttachments
                  ? '可纯附件发送 · Shift+Enter 换行'
                  : 'Shift+Enter 换行 · ⌘J 切 JSON · 可粘贴/拖入附件'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          {loading ? (
            <Button icon={<PauseOutlined />} onClick={onCancel}>
              停止
            </Button>
          ) : null}
          <Button
            type="primary"
            icon={<CaretRightFilled />}
            disabled={uploading}
            onClick={handleSubmit}
          >
            发送
          </Button>
        </div>
      </div>
    </div>
  );
}
