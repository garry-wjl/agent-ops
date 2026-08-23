/**
 * 知识库页面路由 — 对齐 router/index.tsx 与 PRD 导航
 */
import { describe, expect, it } from 'vitest';

export const KB_ROUTES = {
  root: '/kb',
  list: '/kb/manage',
  create: '/kb/manage/editor/new',
  detail: (kbNum: string) => `/kb/manage/detail/${kbNum}`,
} as const;

describe('KB routes', () => {
  it('list path', () => {
    expect(KB_ROUTES.list).toBe('/kb/manage');
  });

  it('create wizard path', () => {
    expect(KB_ROUTES.create).toBe('/kb/manage/editor/new');
  });

  it('detail path with kbNum', () => {
    expect(KB_ROUTES.detail('KB-001')).toBe('/kb/manage/detail/KB-001');
  });

  it('root redirects to manage', () => {
    expect(KB_ROUTES.root).toBe('/kb');
  });
});
