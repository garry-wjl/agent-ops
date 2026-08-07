import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { agentApi, agentQueryKeys } from './api';
import { useWorkspaceStore } from '@/stores/workspace';
import type {
  AgentPageQuery,
  AgentCreateParam,
  AgentDraftParam,
  ConfigSnapshot,
  PublishParam,
  A2aCreateParam,
  A2aDraftParam,
} from '@/types';

/** 分页列表（v2.4 起 queryKey 含 query 参数；并按当前工作空间分桶，避免切换空间命中旧缓存） */
export function useAgentPageQuery(query: AgentPageQuery) {
  const ws = useWorkspaceStore(s => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...agentQueryKeys.page(), ws, query],
    queryFn: () => agentApi.pageList(query),
    staleTime: 30_000,
  });
}

/** 详情 */
export function useAgentDetailQuery(num: string) {
  return useQuery({
    queryKey: agentQueryKeys.detail(num),
    queryFn: () => agentApi.detail(num),
    enabled: !!num,
  });
}

/** 版本列表 */
export function useAgentVersionListQuery(agentNum: string) {
  return useQuery({
    queryKey: agentQueryKeys.versions(agentNum),
    queryFn: () => agentApi.versionList(agentNum),
    enabled: !!agentNum,
  });
}

/** 2026-07-28：调试可选版本列表（调试台版本选择器数据源）。 */
export function useAgentDebugVersionsQuery(agentNum: string) {
  return useQuery({
    queryKey: agentQueryKeys.debugVersions(agentNum),
    queryFn: () => agentApi.debugVersions(agentNum),
    enabled: !!agentNum,
  });
}

/** 创建 */
export function useAgentCreateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: AgentCreateParam) => agentApi.create(param),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.page() });
    },
  });
}

/** 保存草稿 */
export function useAgentSaveDraftMutation() {
  return useMutation({
    mutationFn: (param: AgentDraftParam) => agentApi.saveDraft(param),
  });
}

/** 发布 */
export function useAgentPublishMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: PublishParam) => agentApi.publish(param),
    onSuccess: (_, param) => {
      if (param.agentNum) {
        queryClient.invalidateQueries({ queryKey: agentQueryKeys.detail(param.agentNum) });
        queryClient.invalidateQueries({ queryKey: agentQueryKeys.versions(param.agentNum) });
      } else {
        // 仅传 versionId 时无法精准 invalidate，宽进失效全部 detail / versions
        queryClient.invalidateQueries({ queryKey: ['agent'] });
      }
    },
  });
}

/**
 * v3.0：创建草稿版本
 * - 入参：agentNum
 * - 出参：versionId（前端后续编辑/删除/发布均用此 id）
 */
export function useCreateVersionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (agentNum: string) => agentApi.createVersion(agentNum),
    onSuccess: (_, agentNum) => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.versions(agentNum) });
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.detail(agentNum) });
    },
  });
}

/**
 * v3.0：编辑草稿版本（覆盖 configDraft）
 * - 入参：{ versionId, configDraft }
 */
export function useEditDraftVersionMutation() {
  return useMutation({
    mutationFn: (param: { versionId: string; configDraft: ConfigSnapshot }) =>
      agentApi.editDraftVersion(param.versionId, param.configDraft),
  });
}

/**
 * v3.0：删除草稿版本
 * - 入参：versionId
 */
export function useDeleteDraftVersionMutation() {
  return useMutation({
    mutationFn: (versionId: string) => agentApi.deleteDraftVersion(versionId),
  });
}

/** 下线 */
export function useAgentOfflineMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => agentApi.offline(num),
    onSuccess: (_, num) => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.detail(num) });
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.page() });
    },
  });
}

/** A2A 手动重新同步（v2.4 起合并入 AgentCommandController.a2aResync） */
export function useA2aManualResyncMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => agentApi.a2aManualResync(num),
    onSuccess: (_, num) => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.detail(num) });
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.page() });
    },
  });
}

/** v2.6：A2A 校验并接入（成功后会让列表与详情失效） */
export function useA2aCreateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: A2aCreateParam) => agentApi.createA2a(param),
    onSuccess: (detail) => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.page() });
      if (detail?.num) {
        queryClient.invalidateQueries({
          queryKey: agentQueryKeys.detail(detail.num),
        });
      }
    },
  });
}

/** v2.6：A2A 保存草稿（仅落库，不触发远端校验） */
export function useA2aSaveDraftMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: A2aDraftParam) => agentApi.saveA2aDraft(param),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.page() });
    },
  });
}

/** v2.6：A2A 取消订阅 */
export function useA2aUnsubscribeMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => agentApi.unsubscribeA2a(num),
    onSuccess: (_, num) => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.detail(num) });
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.page() });
    },
  });
}

/** v2.6：A2A 同步历史 */
export function useA2aSyncHistoryQuery(agentNum: string, limit = 100) {
  return useQuery({
    queryKey: [...agentQueryKeys.a2aHistory(agentNum), limit],
    queryFn: () => agentApi.a2aSyncHistory(agentNum, limit),
    enabled: !!agentNum,
  });
}

/* ============ Agent 对外调用秘钥（apiKey）—— Agent 优化 ============ */

/** 秘钥列表（仅掩码） */
export function useAgentApiKeyListQuery(agentNum: string) {
  return useQuery({
    queryKey: agentQueryKeys.apiKeys(agentNum),
    queryFn: () => agentApi.apiKeyList(agentNum),
    enabled: !!agentNum,
  });
}

/** 创建秘钥（成功后失效列表，返回本次明文由调用方回显） */
export function useAgentApiKeyCreateMutation(agentNum: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (remark: string) => agentApi.apiKeyCreate(agentNum, remark),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.apiKeys(agentNum) });
    },
  });
}

/** 删除秘钥（成功后失效列表） */
export function useAgentApiKeyDeleteMutation(agentNum: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => agentApi.apiKeyDelete(agentNum, num),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: agentQueryKeys.apiKeys(agentNum) });
    },
  });
}
