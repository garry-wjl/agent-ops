/**
 * 知识库管理服务 — POST /api/v1/knowledge-base/command/* + GET/POST query
 */
import { get, post } from '../request';
import type {
  KbChunkVO,
  KbConfigAlignment,
  KbCreateParam,
  KbDetailVO,
  KbFileNumParam,
  KbFileVO,
  KbListQueryParam,
  KbNumParam,
  KbTestRetrieveParam,
  KbTypeSchemaVO,
  KbUpdateBasicParam,
  KbUpdateIndexConfigParam,
  KbUploadFileParam,
  KbVO,
  MountableKbItem,
  PageVO,
} from '@/types';

export const knowledgeBaseApi = {
  create: (param: KbCreateParam) =>
    post<KbVO>('/api/v1/knowledge-base/command/create', param),

  updateBasic: (param: KbUpdateBasicParam) =>
    post<KbVO>('/api/v1/knowledge-base/command/updateBasic', param),

  updateIndexConfig: (param: KbUpdateIndexConfigParam) =>
    post<KbVO>('/api/v1/knowledge-base/command/updateIndexConfig', param),

  delete: (param: KbNumParam) =>
    post<void>('/api/v1/knowledge-base/command/delete', param),

  registerFile: (param: KbUploadFileParam) =>
    post<KbVO>('/api/v1/knowledge-base/command/registerFile', param),

  deleteFile: (param: KbFileNumParam) =>
    post<void>('/api/v1/knowledge-base/command/deleteFile', param),

  reindexFile: (param: KbFileNumParam) =>
    post<void>('/api/v1/knowledge-base/command/reindexFile', param),

  reindexStaleFiles: (param: KbNumParam) =>
    post<void>('/api/v1/knowledge-base/command/reindexStaleFiles', param),

  reindexAll: (param: KbNumParam) =>
    post<void>('/api/v1/knowledge-base/command/reindexAll', param),

  list: (query: KbListQueryParam) =>
    get<PageVO<KbVO>>('/api/v1/knowledge-base/query/list', query),

  detail: (kbNum: string) =>
    get<KbDetailVO>('/api/v1/knowledge-base/query/detail', { kbNum }),

  files: (kbNum: string) =>
    get<KbFileVO[]>('/api/v1/knowledge-base/query/files', { kbNum }),

  mountable: () =>
    get<MountableKbItem[]>('/api/v1/knowledge-base/query/mountable'),

  typeSchemas: () =>
    get<KbTypeSchemaVO[]>('/api/v1/knowledge-base/query/typeSchemas'),

  configAlignment: (kbNum: string) =>
    get<KbConfigAlignment[]>(
      '/api/v1/knowledge-base/query/configAlignment',
      { kbNum },
    ),

  testRetrieve: (param: KbTestRetrieveParam) =>
    post<KbChunkVO[]>('/api/v1/knowledge-base/query/testRetrieve', param),
};
