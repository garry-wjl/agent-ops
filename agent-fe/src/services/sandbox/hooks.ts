/**
 * 沙箱管理 react-query hooks。
 *
 * - 分页 queryKey 带 currentWorkspaceNum + query：切换空间或改筛选条件时自动失效，避免命中旧空间结果。
 * - 写 mutation onSuccess 统一 invalidate 分页列表（详情按 num 额外失效）。
 * - 业务错误已由 request 响应拦截器统一 message.error；调用方仅需在需要时 catch 保持弹窗打开。
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useWorkspaceStore } from "@/stores/workspace";
import { sandboxApi } from "./api";
import type {
  SandboxCreateParam,
  SandboxOperateParam,
  SandboxPageQueryParam,
  SandboxUpdateParam,
} from "@/types";

export const sandboxQueryKeys = {
  page: () => ["sandbox", "page"] as const,
  detail: (num: string) => ["sandbox", "detail", num] as const,
};

/** 分页查询当前空间沙箱列表 */
export function useSandboxPageQuery(query: SandboxPageQueryParam) {
  const ws = useWorkspaceStore((s) => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...sandboxQueryKeys.page(), ws, query],
    queryFn: () => sandboxApi.pageList(query),
    staleTime: 30_000,
  });
}

/** 沙箱详情（详情抽屉用） */
export function useSandboxDetailQuery(num: string | undefined) {
  return useQuery({
    queryKey: sandboxQueryKeys.detail(num ?? ""),
    queryFn: () => sandboxApi.detail(num as string),
    enabled: !!num,
  });
}

/** 失效分页列表缓存（写操作成功后统一调用） */
function useInvalidatePage() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: sandboxQueryKeys.page() });
}

/** 新建沙箱 */
export function useSandboxCreateMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: SandboxCreateParam) => sandboxApi.create(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 编辑沙箱 */
export function useSandboxUpdateMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: SandboxUpdateParam) => sandboxApi.update(param),
    onSuccess: (_, param) => {
      qc.invalidateQueries({ queryKey: sandboxQueryKeys.page() });
      qc.invalidateQueries({ queryKey: sandboxQueryKeys.detail(param.num) });
    },
  });
}

/** 删除沙箱 */
export function useSandboxDeleteMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: SandboxOperateParam) => sandboxApi.delete(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 提交沙箱（草稿 / 失败 → 初始化） */
export function useSandboxSubmitMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: SandboxOperateParam) => sandboxApi.submit(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 下线沙箱（在线 → 下线） */
export function useSandboxOfflineMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: SandboxOperateParam) => sandboxApi.offline(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 重新上线沙箱（下线 → 初始化） */
export function useSandboxReonlineMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: SandboxOperateParam) => sandboxApi.reonline(param),
    onSuccess: () => invalidatePage(),
  });
}
