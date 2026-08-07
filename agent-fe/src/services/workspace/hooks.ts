import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { workspaceApi } from './api';
import { authzQueryKeys } from '@/services/authz';
import type {
  WorkspaceCreateParam,
  WorkspaceDeleteParam,
  WorkspaceUpdateParam,
} from '@/types';

export const workspaceQueryKeys = {
  list: () => ['workspace', 'list'] as const,
  detail: (num: string) => ['workspace', 'detail', num] as const,
};

/** 我可见的全部空间（不分页） */
export function useWorkspaceListQuery() {
  return useQuery({
    queryKey: workspaceQueryKeys.list(),
    queryFn: () => workspaceApi.list(),
    staleTime: 30_000,
  });
}

/** 空间详情（编辑抽屉用） */
export function useWorkspaceDetailQuery(num: string | undefined) {
  return useQuery({
    queryKey: workspaceQueryKeys.detail(num ?? ''),
    queryFn: () => workspaceApi.detail(num as string),
    enabled: !!num,
  });
}

/** 创建空间 */
export function useWorkspaceCreateMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: WorkspaceCreateParam) => workspaceApi.create(param),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: workspaceQueryKeys.list() });
    },
  });
}

/** 编辑空间（整体覆盖） */
export function useWorkspaceUpdateMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: WorkspaceUpdateParam) => workspaceApi.update(param),
    onSuccess: (_, param) => {
      qc.invalidateQueries({ queryKey: workspaceQueryKeys.list() });
      qc.invalidateQueries({ queryKey: workspaceQueryKeys.detail(param.num) });
      qc.invalidateQueries({ queryKey: authzQueryKeys.memberRoles(param.num) });
    },
  });
}

/** 删除空间（逻辑删除） */
export function useWorkspaceDeleteMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: WorkspaceDeleteParam) => workspaceApi.delete(param),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: workspaceQueryKeys.list() });
    },
  });
}
