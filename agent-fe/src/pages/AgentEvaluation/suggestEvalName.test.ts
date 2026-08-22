import { describe, expect, it } from 'vitest';
import {
  buildUniqueEvalName,
  formatEvalNameStamp,
  isAutoDatasetName,
  isAutoTaskName,
} from './suggestEvalName';

describe('suggestEvalName', () => {
  const fixed = new Date(2026, 7, 22, 18, 16, 5); // month 0-based → Aug 22

  it('formatEvalNameStamp 格式为 yyyyMMdd-HHmmss', () => {
    expect(formatEvalNameStamp(fixed)).toBe('20260822-181605');
  });

  it('无冲突时返回 prefix + stamp', () => {
    expect(buildUniqueEvalName('评测集', [], { now: fixed })).toBe(
      '评测集 20260822-181605',
    );
    expect(buildUniqueEvalName('评测任务', [], { now: fixed })).toBe(
      '评测任务 20260822-181605',
    );
  });

  it('与已有名称冲突时追加 -2、-3', () => {
    const existing = [
      '评测集 20260822-181605',
      '评测集 20260822-181605-2',
    ];
    expect(buildUniqueEvalName('评测集', existing, { now: fixed })).toBe(
      '评测集 20260822-181605-3',
    );
  });

  it('超长名称截断到 maxLen 且仍尽量唯一', () => {
    const longPrefix = '甲'.repeat(60);
    const name = buildUniqueEvalName(longPrefix, [], {
      now: fixed,
      maxLen: 64,
    });
    expect(name.length).toBeLessThanOrEqual(64);
    expect(name.startsWith('甲')).toBe(true);
  });

  it('isAutoDatasetName / isAutoTaskName 识别自动名', () => {
    expect(isAutoDatasetName('评测集 20260822-181605')).toBe(true);
    expect(isAutoDatasetName('客服助手 评测集 20260822-181605')).toBe(true);
    expect(isAutoDatasetName('我的回归集')).toBe(false);
    expect(isAutoTaskName('评测任务 20260822-181605')).toBe(true);
    expect(isAutoTaskName('客服集 · 20260822-181605')).toBe(true);
    expect(isAutoTaskName('手写任务名')).toBe(false);
  });
});
