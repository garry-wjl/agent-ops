/**
 * MULTIMODAL 用户消息 content 解析 / 格式化。
 */
import type { AttachmentRef, MultimodalContent } from '@/types';

export function isMultimodalContent(value: unknown): value is MultimodalContent {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const atts = (value as MultimodalContent).attachments;
  return Array.isArray(atts);
}

/**
 * 从 MessageVO.content（string JSON 或已解析对象）解析多模态载荷。
 */
export function parseMultimodalContent(
  content: string | Record<string, unknown> | null | undefined,
): MultimodalContent | null {
  if (content == null) return null;
  let obj: unknown = content;
  if (typeof content === 'string') {
    const trimmed = content.trim();
    if (!trimmed.startsWith('{')) return null;
    try {
      obj = JSON.parse(trimmed);
    } catch {
      return null;
    }
  }
  if (!isMultimodalContent(obj)) return null;
  return {
    text: obj.text ?? '',
    attachments: (obj.attachments ?? []).filter(
      (a): a is AttachmentRef => !!a && typeof a.fileId === 'string' && !!a.fileId,
    ),
  };
}

export function formatFileSize(bytes?: number): string {
  if (bytes == null || Number.isNaN(bytes) || bytes < 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function inferAttachmentKind(mimeType?: string, name?: string): 'image' | 'file' {
  if (mimeType?.startsWith('image/')) return 'image';
  const lower = (name ?? '').toLowerCase();
  if (/\.(png|jpe?g|gif|webp)$/.test(lower)) return 'image';
  return 'file';
}
