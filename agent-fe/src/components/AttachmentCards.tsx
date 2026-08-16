/**
 * 用户消息内的附件卡片：图片缩略图 / 文档图标+名+大小。
 */
import { useEffect, useState } from 'react';
import { FileTextOutlined, PaperClipOutlined } from '@ant-design/icons';
import { commonApi } from '@/services/common/api';
import type { AttachmentRef } from '@/types';
import { formatFileSize, inferAttachmentKind } from '@/utils/multimodalContent';

const COLOR = {
  border: '#E5E7EB',
  textPrimary: '#0F172B',
  textMuted: '#94A3B8',
  bg: '#fff',
} as const;

export interface AttachmentCardsProps {
  attachments: AttachmentRef[];
  /** 本地预览 URL（发送前），key=fileId */
  previewUrls?: Record<string, string>;
}

export default function AttachmentCards({
  attachments,
  previewUrls,
}: AttachmentCardsProps) {
  if (!attachments.length) return null;
  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 8,
        marginTop: 8,
      }}
    >
      {attachments.map((a) => (
        <AttachmentCard
          key={a.fileId}
          attachment={a}
          previewUrl={previewUrls?.[a.fileId]}
        />
      ))}
    </div>
  );
}

function AttachmentCard({
  attachment,
  previewUrl,
}: {
  attachment: AttachmentRef;
  previewUrl?: string;
}) {
  const kind =
    attachment.kind ??
    inferAttachmentKind(attachment.mimeType, attachment.name);
  const [url, setUrl] = useState<string | undefined>(previewUrl);

  useEffect(() => {
    if (previewUrl) {
      setUrl(previewUrl);
      return;
    }
    if (kind !== 'image') return;
    let cancelled = false;
    commonApi
      .fileUrl({ fileId: attachment.fileId })
      .then((vo) => {
        if (!cancelled && vo?.url) setUrl(vo.url);
      })
      .catch(() => {
        /* 预览失败静默降级为文件卡片 */
      });
    return () => {
      cancelled = true;
    };
  }, [attachment.fileId, kind, previewUrl]);

  if (kind === 'image' && url) {
    return (
      <a
        href={url}
        target="_blank"
        rel="noreferrer"
        style={{
          display: 'block',
          width: 96,
          height: 96,
          borderRadius: 8,
          overflow: 'hidden',
          border: `1px solid ${COLOR.border}`,
          background: COLOR.bg,
        }}
      >
        <img
          src={url}
          alt={attachment.name ?? 'image'}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </a>
    );
  }

  const sizeLabel = formatFileSize(attachment.size);
  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        maxWidth: 280,
        padding: '8px 12px',
        borderRadius: 8,
        border: `1px solid ${COLOR.border}`,
        background: COLOR.bg,
        fontSize: 12,
        color: COLOR.textPrimary,
      }}
      title={attachment.fileId}
    >
      {kind === 'image' ? (
        <PaperClipOutlined style={{ color: COLOR.textMuted }} />
      ) : (
        <FileTextOutlined style={{ color: COLOR.textMuted }} />
      )}
      <div style={{ minWidth: 0, flex: 1 }}>
        <div
          style={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            fontWeight: 500,
          }}
        >
          {attachment.name || attachment.fileId}
        </div>
        {sizeLabel ? (
          <div style={{ color: COLOR.textMuted, fontSize: 11 }}>{sizeLabel}</div>
        ) : null}
      </div>
    </div>
  );
}
