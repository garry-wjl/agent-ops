/**
 * 知识库管理类型 — 对齐 rd-agent-be knowledgebase client VO/DTO
 */
import type { PageParam } from './common';

/** 知识库类型 */
export type KbType = 'SIMPLE' | 'RAG_FLOW';

/** 知识库聚合状态 */
export type KbStatus =
  | 'INDEXING'
  | 'READY'
  | 'PARTIAL_FAILED'
  | 'DISABLED';

/** 文件索引状态 */
export type KbIndexStatus =
  | 'PENDING'
  | 'PARSING'
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'READY'
  | 'FAILED';

/** SIMPLE 文本分块策略 */
export type SplitStrategy =
  | 'PARAGRAPH'
  | 'SENTENCE'
  | 'RECURSIVE'
  | 'FIXED';

/** Agent 知识库检索模式 */
export type RetrievalMode = 'AUTO' | 'ON_DEMAND' | 'HYBRID';

/** 索引配置 */
export interface KbIndexConfig {
  configVersion?: number;
  embeddingModelId?: string;
  splitStrategy?: SplitStrategy;
  chunkSize?: number;
  chunkOverlap?: number;
  wordSeparateTables?: boolean;
  wordTableFormat?: string;
  parserId?: string;
  chunkMethod?: string;
  layoutRecognize?: boolean;
}

/** 默认检索参数 */
export interface KbRetrievalDefaults {
  topK?: number;
  minScore?: number;
}

/** 列表 / 命令返回摘要 */
export interface KbVO {
  kbNum: string;
  workspaceNum?: string;
  name: string;
  description?: string;
  kbType: KbType;
  status: KbStatus;
  fileCount?: number;
  chunkCount?: number;
  configVersion?: number;
  agentsBoundCount?: number;
}

/** 详情 */
export interface KbDetailVO {
  kbNum: string;
  workspaceNum?: string;
  name: string;
  description?: string;
  kbType: KbType;
  status: KbStatus;
  indexConfig?: KbIndexConfig;
  sourceConfig?: Record<string, unknown>;
  retrievalDefaults?: KbRetrievalDefaults;
  fileCount?: number;
  chunkCount?: number;
  agentsBoundCount?: number;
  configAlignment?: KbConfigAlignment[];
}

/** 配置版本对齐统计 */
export interface KbConfigAlignment {
  configVersion: number;
  fileCount: number;
  current?: boolean;
}

/** 知识库文件 */
export interface KbFileVO {
  fileNum: string;
  kbNum: string;
  ossFileId?: string;
  fileName: string;
  mimeType?: string;
  fileSize?: number;
  indexStatus: KbIndexStatus;
  indexConfigVersion?: number;
  chunkCount?: number;
  errorMessage?: string;
  indexedAt?: string;
}

/** 检索测试命中块 */
export interface KbChunkVO {
  kbNum: string;
  fileNum?: string;
  fileName?: string;
  configVersion?: number;
  content?: string;
  score?: number;
}

/** 可挂载知识库项（Agent 绑定） */
export interface MountableKbItem {
  kbNum: string;
  name: string;
  description?: string;
  kbType: KbType;
  status: KbStatus;
}

/** 类型表单 schema 字段 */
export interface KbTypeSchemaField {
  name: string;
  required?: boolean;
  label?: string;
  type?: string;
}

/** 类型表单 schema */
export interface KbTypeSchemaVO {
  kbType: KbType;
  fields: KbTypeSchemaField[];
}

/** 列表查询 */
export interface KbListQueryParam extends PageParam {
  keyword?: string;
  kbType?: KbType;
  status?: KbStatus;
}

/** 创建入参 */
export interface KbCreateParam {
  name: string;
  description?: string;
  kbType: KbType;
  indexConfig?: KbIndexConfig;
  retrievalDefaults?: KbRetrievalDefaults;
}

/** 更新基本信息 */
export interface KbUpdateBasicParam {
  kbNum: string;
  name: string;
  description?: string;
}

/** 更新索引配置 */
export interface KbUpdateIndexConfigParam {
  kbNum: string;
  indexConfig?: KbIndexConfig;
}

/** 单编号操作 */
export interface KbNumParam {
  kbNum: string;
}

/** 文件编号操作 */
export interface KbFileNumParam {
  kbNum: string;
  fileNum: string;
}

/** 登记已上传文件 */
export interface KbUploadFileParam {
  kbNum: string;
  ossFileId: string;
  fileName: string;
  mimeType?: string;
  fileSize?: number;
}

/** 检索测试 */
export interface KbTestRetrieveParam {
  kbNum: string;
  question: string;
  topK?: number;
  minScore?: number;
}

/** Agent 知识库绑定 */
export interface KnowledgeBaseBindingParam {
  kbNum: string;
  retrievalMode?: RetrievalMode;
  topK?: number;
  minScore?: number;
}
