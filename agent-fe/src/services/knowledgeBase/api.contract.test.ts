/**
 * 知识库 API 路径契约 — 对齐技术方案 §7.2（静态校验，避免拉起 axios/workspace store）
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const EXPECTED_PATHS = {
  create: '/api/v1/knowledge-base/command/create',
  updateBasic: '/api/v1/knowledge-base/command/updateBasic',
  updateIndexConfig: '/api/v1/knowledge-base/command/updateIndexConfig',
  delete: '/api/v1/knowledge-base/command/delete',
  registerFile: '/api/v1/knowledge-base/command/registerFile',
  deleteFile: '/api/v1/knowledge-base/command/deleteFile',
  reindexFile: '/api/v1/knowledge-base/command/reindexFile',
  reindexStaleFiles: '/api/v1/knowledge-base/command/reindexStaleFiles',
  reindexAll: '/api/v1/knowledge-base/command/reindexAll',
  list: '/api/v1/knowledge-base/query/list',
  detail: '/api/v1/knowledge-base/query/detail',
  files: '/api/v1/knowledge-base/query/files',
  mountable: '/api/v1/knowledge-base/query/mountable',
  typeSchemas: '/api/v1/knowledge-base/query/typeSchemas',
  configAlignment: '/api/v1/knowledge-base/query/configAlignment',
  testRetrieve: '/api/v1/knowledge-base/query/testRetrieve',
} as const;

const WORKSPACE_KB_PATHS = {
  kbConfigGet: '/api/v1/workspace/query/kbConfig',
  kbConfigSave: '/api/v1/workspace/command/saveKbConfig',
} as const;

const apiSource = readFileSync(
  resolve(__dirname, './api.ts'),
  'utf8',
);

describe('knowledgeBaseApi contract', () => {
  it('api.ts contains all command/query paths', () => {
    for (const path of Object.values(EXPECTED_PATHS)) {
      expect(apiSource).toContain(path);
    }
  });

  it('paths match technical spec §7.2', () => {
    expect(EXPECTED_PATHS.create).toBe('/api/v1/knowledge-base/command/create');
    expect(EXPECTED_PATHS.testRetrieve).toBe('/api/v1/knowledge-base/query/testRetrieve');
    expect(WORKSPACE_KB_PATHS.kbConfigSave).toBe('/api/v1/workspace/command/saveKbConfig');
  });
});
