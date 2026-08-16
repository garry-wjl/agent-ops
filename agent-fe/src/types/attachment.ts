/**
 * 聊天附件引用 — 对齐 BE AttachmentRefParam / MULTIMODAL content schema。
 */

export type AttachmentKind = 'image' | 'file';

/** Invoke 请求中的附件引用（已上传并登记） */
export interface AttachmentRef {
  fileId: string;
  name?: string;
  mimeType?: string;
  size?: number;
  kind?: AttachmentKind;
}

/** 用户消息 MULTIMODAL content JSON */
export interface MultimodalContent {
  text?: string | null;
  attachments: AttachmentRef[];
}

/** 调试台本地待发送附件（含预览） */
export interface PendingAttachment extends AttachmentRef {
  /** 本地 object URL，仅图片预览用；发送后 revoke */
  previewUrl?: string;
  /** 上传中 */
  uploading?: boolean;
  /** 上传失败文案 */
  error?: string;
}
