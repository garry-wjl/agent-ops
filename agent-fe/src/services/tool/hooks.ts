/**
 * 工具管理 react-query hooks。
 *
 * - 分页 queryKey 带 currentWorkspaceNum + query：切换空间或改筛选条件时自动失效。
 * - 写 mutation onSuccess 统一 invalidate 分页列表（详情/复用数按 num 额外失效）。
 * - 业务错误已由 request 响应拦截器统一 message.error；调用方仅在需要时 catch 保持弹窗打开。
 * - checkName（失焦唯一性校验）为一次性查询，不进 react-query，直接调 toolApi.checkName。
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useWorkspaceStore } from "@/stores/workspace";
import { toolApi } from "./api";
import type {
  McpTestConnectionParam,
  ToolCreateParam,
  ToolNumParam,
  ToolPageQueryParam,
  ToolUpdateParam,
} from "@/types";

export const toolQueryKeys = {
  page: () => ["tool", "page"] as const,
  detail: (num: string) => ["tool", "detail", num] as const,
  mountable: () => ["tool", "mountable"] as const,
  reuseCount: (num: string) => ["tool", "reuseCount", num] as const,
  mountedAgents: (num: string) => ["tool", "mountedAgents", num] as const,
};

/** 分页查询当前空间工具列表 */
export function useToolPageQuery(query: ToolPageQueryParam) {
  const ws = useWorkspaceStore((s) => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...toolQueryKeys.page(), ws, query],
    queryFn: () => toolApi.pageList(query),
    staleTime: 30_000,
  });
}

/** 工具详情（详情抽屉用） */
export function useToolDetailQuery(num: string | undefined) {
  return useQuery({
    queryKey: toolQueryKeys.detail(num ?? ""),
    queryFn: () => toolApi.detail(num as string),
    enabled: !!num,
  });
}

/** 可挂载工具列表（MCP API 打包「已有 API」下拉用：过滤 type=FC、status=PUBLISHED） */
export function useToolMountableQuery(enabled = true) {
  const ws = useWorkspaceStore((s) => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...toolQueryKeys.mountable(), ws],
    queryFn: () => toolApi.mountable(),
    enabled,
    staleTime: 30_000,
  });
}

/** 复用数下钻：挂载该工具的 Agent 简表 */
export function useToolMountedAgentsQuery(num: string | undefined) {
  return useQuery({
    queryKey: toolQueryKeys.mountedAgents(num ?? ""),
    queryFn: () => toolApi.mountedAgents(num as string),
    enabled: !!num,
  });
}

/** 失效分页列表缓存（写操作成功后统一调用） */
function useInvalidatePage() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: toolQueryKeys.page() });
}

/** 失效与某 num 关联的详情 / 复用数缓存 */
function useInvalidateByNum() {
  const qc = useQueryClient();
  return (num: string) => {
    qc.invalidateQueries({ queryKey: toolQueryKeys.detail(num) });
    qc.invalidateQueries({ queryKey: toolQueryKeys.reuseCount(num) });
  };
}

/** 新建工具（草稿） */
export function useToolCreateMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: ToolCreateParam) => toolApi.create(param),
    onSuccess: () => invalidatePage(),
  });
}

/** 编辑工具 */
export function useToolUpdateMutation() {
  const invalidatePage = useInvalidatePage();
  const invalidateByNum = useInvalidateByNum();
  return useMutation({
    mutationFn: (param: ToolUpdateParam) => toolApi.update(param),
    onSuccess: (_, param) => {
      invalidatePage();
      invalidateByNum(param.num);
    },
  });
}

/** 发布工具 */
export function useToolPublishMutation() {
  const invalidatePage = useInvalidatePage();
  const invalidateByNum = useInvalidateByNum();
  return useMutation({
    mutationFn: (param: ToolNumParam) => toolApi.publish(param),
    onSuccess: (_, param) => {
      invalidatePage();
      invalidateByNum(param.num);
    },
  });
}

/** 弃用工具 */
export function useToolUnpublishMutation() {
  const invalidatePage = useInvalidatePage();
  const invalidateByNum = useInvalidateByNum();
  return useMutation({
    mutationFn: (param: ToolNumParam) => toolApi.unpublish(param),
    onSuccess: (_, param) => {
      invalidatePage();
      invalidateByNum(param.num);
    },
  });
}

/** 重新发布工具 */
export function useToolRepublishMutation() {
  const invalidatePage = useInvalidatePage();
  const invalidateByNum = useInvalidateByNum();
  return useMutation({
    mutationFn: (param: ToolNumParam) => toolApi.republish(param),
    onSuccess: (_, param) => {
      invalidatePage();
      invalidateByNum(param.num);
    },
  });
}

/** 删除草稿 */
export function useToolDeleteDraftMutation() {
  const invalidatePage = useInvalidatePage();
  return useMutation({
    mutationFn: (param: ToolNumParam) => toolApi.deleteDraft(param),
    onSuccess: () => invalidatePage(),
  });
}

// ============================================================
// MCP 测试连接
// ============================================================

/** MCP 测试连接（不 invalidate 任何列表缓存，测试结果只展示在前端弹窗/行内）。 */
export function useMcpTestConnectionMutation() {
  return useMutation({
    mutationFn: (param: McpTestConnectionParam) =>
      toolApi.testMcpConnection(param),
  });
}
