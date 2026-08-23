/**
 * 知识库常量 — 对齐 PRD 字段约束与状态枚举
 */
import { describe, expect, it } from 'vitest';
import {
  DEFAULT_INDEX_CONFIG,
  DEFAULT_RETRIEVAL,
  KB_INDEX_STATUS_META,
  KB_LIMITS,
  KB_STATUS_META,
  KB_TYPE_META,
  RETRIEVAL_MODE_LABEL,
  SPLIT_STRATEGY_LABEL,
} from './constants';

describe('KB_LIMITS', () => {
  it('name max 64 per PRD', () => {
    expect(KB_LIMITS.NAME_MAX).toBe(64);
    expect(KB_LIMITS.DESC_MAX).toBe(500);
  });
});

describe('KB status/type enums', () => {
  it('covers PRD lifecycle statuses', () => {
    expect(Object.keys(KB_STATUS_META).sort()).toEqual(
      ['DISABLED', 'INDEXING', 'PARTIAL_FAILED', 'READY'].sort(),
    );
    expect(KB_STATUS_META.READY.label).toBe('就绪');
  });

  it('covers kb types SIMPLE and RAG_FLOW', () => {
    expect(Object.keys(KB_TYPE_META).sort()).toEqual(['RAG_FLOW', 'SIMPLE'].sort());
  });

  it('covers file index statuses', () => {
    expect(Object.keys(KB_INDEX_STATUS_META)).toContain('PENDING');
    expect(Object.keys(KB_INDEX_STATUS_META)).toContain('READY');
    expect(Object.keys(KB_INDEX_STATUS_META)).toContain('FAILED');
  });
});

describe('defaults', () => {
  it('retrieval defaults match PRD', () => {
    expect(DEFAULT_RETRIEVAL.topK).toBe(5);
    expect(DEFAULT_RETRIEVAL.minScore).toBe(0.5);
    expect(DEFAULT_RETRIEVAL.retrievalMode).toBe('AUTO');
  });

  it('index config defaults', () => {
    expect(DEFAULT_INDEX_CONFIG.chunkSize).toBe(512);
    expect(DEFAULT_INDEX_CONFIG.chunkOverlap).toBe(64);
    expect(DEFAULT_INDEX_CONFIG.splitStrategy).toBe('PARAGRAPH');
  });
});

describe('labels', () => {
  it('retrieval modes', () => {
    expect(Object.keys(RETRIEVAL_MODE_LABEL).sort()).toEqual(
      ['AUTO', 'HYBRID', 'ON_DEMAND'].sort(),
    );
  });

  it('split strategies', () => {
    expect(Object.keys(SPLIT_STRATEGY_LABEL)).toContain('PARAGRAPH');
    expect(Object.keys(SPLIT_STRATEGY_LABEL)).toContain('FIXED');
  });
});
