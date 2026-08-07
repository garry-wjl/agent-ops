/**
 * Prompt 中心 react-query hooks。
 *
 * - 分页 queryKey 带 currentWorkspaceNum + query：切换空间或改筛选条件时自动失效。
 * - 写 mutation onSuccess 统一 invalidate 分页列表（详情按 num 额外失效）。
 * - 业务错误已由 request 响应拦截器统一 message.error；调用方仅在需要时 catch 保持弹窗打开。
 * - checkKey（失焦唯一性校验）为一次性查询，不进 react-query，直接调 promptApi.checkKey。
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useWorkspaceStore } from "@/stores/workspace";
import { promptApi } from "./api";
import type {
  PromptCreateParam,
  PromptNumParam,
  PromptPageQueryParam,
  PromptUpdateParam,
} from "@/types";

export const promptQueryKeys = {
  page: () => ["prompt", "page"] as const,
  detail: (num: string) => ["prompt", "detail", num] as const,
};

/** 分页查询当前空间 Prompt 列表 */
export function usePromptPageQuery(query: PromptPageQueryParam) {
  const ws = useWorkspaceStore((s) => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...promptQueryKeys.page(), ws, query],
    queryFn: () => promptApi.pageList(query),
    staleTime: 30_000,
  });
}

/** Prompt 详情（详情抽屉用） */
export function usePromptDetailQuery(num: string | undefined) {
  return useQuery({
    queryKey: promptQueryKeys.detail(num ?? ""),
    queryFn: () => promptApi.detail(num as string),
    enabled: !!num,
  });
}

/** 失效分页列表缓存（写操作成功后统一调用） */
function useInvalidatePage() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: promptQueryKeys.page() });
}

/** 失效与某 num 关联的详情缓存 */
function useInvalidateByNum() {
  const qc = useQueryClient();
  return (num: string) =>
    qc.invalidateQueries({ queryKey: promptQueryKeys.detail(num) });
}

/** 新建 Prompt */
export function usePromptCreateMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: PromptCreateParam) => promptApi.create(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 编辑 Prompt */
export function usePromptUpdateMutation() {
  const invalidatePage = useInvalidatePage();
  const invalidateByNum = useInvalidateByNum();
  return useMutation({
    mutationFn: (param: PromptUpdateParam) => promptApi.update(param),
    onSuccess: (_, param) => {
      invalidatePage();
      invalidateByNum(param.num);
    },
  });
}

/** 软删除 Prompt */
export function usePromptDeleteMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: PromptNumParam) => promptApi.delete(param),
    onSuccess: () => invalidatePage(),
  });
}
