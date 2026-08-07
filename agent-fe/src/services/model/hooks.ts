/**
 * 模型管理 react-query hooks。
 *
 * - 分页 queryKey 带 currentWorkspaceNum + query：切换空间或改筛选条件时自动失效，避免命中旧空间结果。
 * - 写 mutation onSuccess 统一 invalidate 分页列表（详情按 num 额外失效）。
 * - 业务错误已由 request 响应拦截器统一 message.error；调用方仅需在需要时 catch 保持弹窗打开。
 * - 2026-06-17 scope 优化：新增 selectable query、系统模型 CRUD hooks；
 *   selectable 一次拉系统+当前空间合集，queryKey 带工作空间以在切换时刷新。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useWorkspaceStore } from '@/stores/workspace';
import { modelApi } from './api';
import type {
  ModelCreateParam,
  ModelOperateParam,
  ModelPageQueryParam,
  ModelUpdateParam,
} from '@/types';

export const modelQueryKeys = {
  page: () => ['model', 'page'] as const,
  /** 系统模型分页（独立缓存，不与空间模型分页混用） */
  systemPage: () => ['model', 'system-page'] as const,
  detail: (num: string) => ['model', 'detail', num] as const,
  /** Agent 可选模型合集（系统启用 + 当前空间启用） */
  selectable: (ws: string | undefined) => ['model', 'selectable', ws ?? ''] as const,
};

/** 分页查询当前空间模型列表 */
export function useModelPageQuery(query: ModelPageQueryParam, enabled = true) {
  const ws = useWorkspaceStore(s => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...modelQueryKeys.page(), ws, query],
    queryFn: () => modelApi.pageList(query),
    staleTime: 30_000,
    enabled,
  });
}

/** 分页查询系统模型列表（PLATFORM scope） */
export function useSystemModelPageQuery(query: ModelPageQueryParam, enabled = true) {
  return useQuery({
    queryKey: [...modelQueryKeys.systemPage(), query],
    queryFn: () => modelApi.systemPageList(query),
    staleTime: 30_000,
    enabled,
  });
}

/** 模型详情（详情抽屉用） */
export function useModelDetailQuery(num: string | undefined) {
  return useQuery({
    queryKey: modelQueryKeys.detail(num ?? ''),
    queryFn: () => modelApi.detail(num as string),
    enabled: !!num,
  });
}

/**
 * Agent 可选模型：系统启用模型 + 当前空间启用模型，不含 Key。
 * 切换工作空间时自动刷新（queryKey 带 ws）。
 */
export function useModelSelectableQuery() {
  const ws = useWorkspaceStore(s => s.currentWorkspaceNum);
  return useQuery({
    queryKey: modelQueryKeys.selectable(ws),
    queryFn: () => modelApi.selectable(),
    staleTime: 60_000,
  });
}

/** 失效分页列表缓存（写操作成功后统一调用） */
function useInvalidatePage() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: modelQueryKeys.page() });
}

/** 失效系统模型分页缓存 */
function useInvalidateSystemPage() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: modelQueryKeys.systemPage() });
}

/** 新建模型 */
export function useModelCreateMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: ModelCreateParam) => modelApi.create(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 编辑模型 */
export function useModelUpdateMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: ModelUpdateParam) => modelApi.update(param),
    onSuccess: (_, param) => {
      qc.invalidateQueries({ queryKey: modelQueryKeys.page() });
      qc.invalidateQueries({ queryKey: modelQueryKeys.detail(param.num) });
    },
  });
}

/** 删除模型（仅草稿态） */
export function useModelDeleteMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: ModelOperateParam) => modelApi.delete(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 启用模型（DRAFT / DISABLED → ENABLED） */
export function useModelEnableMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: ModelOperateParam) => modelApi.enable(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 禁用模型（ENABLED → DISABLED） */
export function useModelDisableMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: ModelOperateParam) => modelApi.disable(param),
    onSuccess: () => invalidatePage(),
  });
}

// ===== 系统模型写 hooks（/api/v1/system/model，需 system:manage_settings）=====

/** 新建系统模型 */
export function useSystemModelCreateMutation() {
  const invalidateSystemPage = useInvalidateSystemPage();
  return useMutation({
    mutationFn: (param: ModelCreateParam) => modelApi.systemCreate(param),
    onSuccess: () => invalidateSystemPage(),
  });
}

/** 编辑系统模型 */
export function useSystemModelUpdateMutation() {
  const qc = useQueryClient();
  const invalidateSystemPage = useInvalidateSystemPage();
  return useMutation({
    mutationFn: (param: ModelUpdateParam) => modelApi.systemUpdate(param),
    onSuccess: (_, param) => {
      invalidateSystemPage();
      qc.invalidateQueries({ queryKey: modelQueryKeys.detail(param.num) });
    },
  });
}

/** 软删系统模型（仅草稿态） */
export function useSystemModelDeleteMutation() {
  const invalidateSystemPage = useInvalidateSystemPage();
  return useMutation({
    mutationFn: (param: ModelOperateParam) => modelApi.systemDelete(param),
    onSuccess: () => invalidateSystemPage(),
  });
}

/** 启用系统模型（DRAFT / DISABLED → ENABLED） */
export function useSystemModelEnableMutation() {
  const invalidateSystemPage = useInvalidateSystemPage();
  return useMutation({
    mutationFn: (param: ModelOperateParam) => modelApi.systemEnable(param),
    onSuccess: () => invalidateSystemPage(),
  });
}

/** 禁用系统模型（ENABLED → DISABLED） */
export function useSystemModelDisableMutation() {
  const invalidateSystemPage = useInvalidateSystemPage();
  return useMutation({
    mutationFn: (param: ModelOperateParam) => modelApi.systemDisable(param),
    onSuccess: () => invalidateSystemPage(),
  });
}
