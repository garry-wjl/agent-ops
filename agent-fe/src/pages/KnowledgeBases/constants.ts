/**
 * 知识库展示常量
 */
import type {
  KbIndexStatus,
  KbStatus,
  KbType,
  RetrievalMode,
  SplitStrategy,
} from '@/types';

export const KB_STATUS_META: Record<
  KbStatus,
  { label: string; color: string }
> = {
  INDEXING: { label: '索引中', color: '#D97706' },
  READY: { label: '就绪', color: '#16A34A' },
  PARTIAL_FAILED: { label: '部分失败', color: '#DC2626' },
  DISABLED: { label: '已停用', color: '#90A1B9' },
};

export const KB_TYPE_META: Record<
  KbType,
  { label: string; color: string; bg: string }
> = {
  SIMPLE: { label: '简易知识库', color: '#2563EB', bg: '#EFF6FF' },
  RAG_FLOW: { label: 'RAG Flow', color: '#7C3AED', bg: '#F5F3FF' },
};

export const KB_INDEX_STATUS_META: Record<
  KbIndexStatus,
  { label: string; color: string }
> = {
  PENDING: { label: '待处理', color: '#90A1B9' },
  PARSING: { label: '解析中', color: '#D97706' },
  CHUNKING: { label: '分块中', color: '#D97706' },
  EMBEDDING: { label: '向量化', color: '#D97706' },
  READY: { label: '已就绪', color: '#16A34A' },
  FAILED: { label: '失败', color: '#DC2626' },
};

export const RETRIEVAL_MODE_LABEL: Record<RetrievalMode, string> = {
  AUTO: '自动注入',
  ON_DEMAND: '按需检索',
  HYBRID: '混合模式',
};

export const SPLIT_STRATEGY_LABEL: Record<SplitStrategy, string> = {
  PARAGRAPH: '按段落',
  SENTENCE: '按句子',
  RECURSIVE: '递归分块',
  FIXED: '固定长度',
};

export const KB_LIMITS = {
  NAME_MAX: 64,
  DESC_MAX: 500,
} as const;

export const DEFAULT_RETRIEVAL = {
  topK: 5,
  minScore: 0.5,
  retrievalMode: 'AUTO' as RetrievalMode,
};

export const DEFAULT_INDEX_CONFIG = {
  splitStrategy: 'PARAGRAPH' as SplitStrategy,
  chunkSize: 512,
  chunkOverlap: 64,
};
