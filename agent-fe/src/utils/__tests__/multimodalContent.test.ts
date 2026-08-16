import { describe, expect, it } from 'vitest';
import {
  formatFileSize,
  inferAttachmentKind,
  parseMultimodalContent,
} from '@/utils/multimodalContent';

describe('parseMultimodalContent', () => {
  it('parses JSON string content', () => {
    const raw = JSON.stringify({
      text: '看图',
      attachments: [
        {
          fileId: 'f1',
          name: 'a.png',
          mimeType: 'image/png',
          size: 10,
          kind: 'image',
        },
      ],
    });
    expect(parseMultimodalContent(raw)).toEqual({
      text: '看图',
      attachments: [
        {
          fileId: 'f1',
          name: 'a.png',
          mimeType: 'image/png',
          size: 10,
          kind: 'image',
        },
      ],
    });
  });

  it('returns null for plain text', () => {
    expect(parseMultimodalContent('hello')).toBeNull();
  });

  it('accepts object content', () => {
    const obj = {
      text: '',
      attachments: [{ fileId: 'f2', kind: 'file' as const }],
    };
    expect(parseMultimodalContent(obj)?.attachments[0].fileId).toBe('f2');
  });
});

describe('inferAttachmentKind / formatFileSize', () => {
  it('infers image from mime', () => {
    expect(inferAttachmentKind('image/png')).toBe('image');
    expect(inferAttachmentKind('application/pdf', 'a.pdf')).toBe('file');
  });

  it('formats sizes', () => {
    expect(formatFileSize(500)).toBe('500 B');
    expect(formatFileSize(2048)).toBe('2.0 KB');
  });
});
