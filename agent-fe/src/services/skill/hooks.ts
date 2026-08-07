import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { skillApi } from './api';
import type {
  SkillPageQuery,
  SkillCreateParam,
  SkillUpdateParam,
  SkillPublishParam,
  SkillRollbackParam,
  SkillCheckRecordPageQuery,
} from '@/types';

export const skillQueryKeys = {
  page: () => ['skill', 'page'] as const,
  detail: (num: string) => ['skill', 'detail', num] as const,
  versions: (skillNum: string) => ['skill', 'versions', skillNum] as const,
  bindableVersions: (skillNum: string) =>
    ['skill', 'bindableVersions', skillNum] as const,
  resourceTree: (num: string, version?: string) =>
    ['skill', 'resourceTree', num, version ?? '__draft__'] as const,
  checkRecords: (skillNum: string) =>
    ['skill', 'checkRecords', skillNum] as const,
  checkRecordDetail: (recordNum: string) =>
    ['skill', 'checkRecord', recordNum] as const,
};

/** 分页列表 */
export function useSkillPageQuery(query: SkillPageQuery) {
  return useQuery({
    queryKey: skillQueryKeys.page(),
    queryFn: () => skillApi.pageList(query),
    staleTime: 30_000,
  });
}

/** 详情 */
export function useSkillDetailQuery(num: string) {
  return useQuery({
    queryKey: skillQueryKeys.detail(num),
    queryFn: () => skillApi.detail(num),
    enabled: !!num,
  });
}

/** 版本列表 */
export function useSkillVersionListQuery(skillNum: string) {
  return useQuery({
    queryKey: skillQueryKeys.versions(skillNum),
    queryFn: () => skillApi.versionList(skillNum),
    enabled: !!skillNum,
  });
}

/** 2026-07-28：可绑定版本列表（Agent 绑定 Skill 时的版本下拉数据源，仅已发布版本）。 */
export function useSkillBindableVersionsQuery(skillNum: string) {
  return useQuery({
    queryKey: skillQueryKeys.bindableVersions(skillNum),
    queryFn: () => skillApi.bindableVersions(skillNum),
    enabled: !!skillNum,
  });
}

/** 2026-06-10：双模式创建 Skill（仅落 DRAFT 草稿，不再首发）。 */
export function useSkillCreateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: SkillCreateParam) => skillApi.create(param),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.page() });
    },
  });
}

/** v2.11：更新 Skill 字段；BE 自动置 status=DRAFT。 */
export function useSkillUpdateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: SkillUpdateParam) => skillApi.update(param),
    onSuccess: (_, param) => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.detail(param.num) });
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.page() });
    },
  });
}

/** v2.11 新增：放弃草稿态修改。 */
export function useSkillDiscardDraftMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => skillApi.discardDraft(num),
    onSuccess: (_, num) => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.detail(num) });
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.page() });
    },
  });
}

/** 2026-06-10：发布（触发三检）；PASS 返回结果，FAIL 抛 BizError(3006)，data 含检测明细。 */
export function useSkillPublishMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: SkillPublishParam) => skillApi.publish(param),
    // 无论成功失败都刷新检测记录 + 详情 + 版本（FAIL 也落了一条检测记录）
    onSettled: (_d, _e, param) => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.detail(param.skillNum) });
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.versions(param.skillNum) });
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.checkRecords(param.skillNum) });
    },
  });
}

/** v2.11：回滚到指定历史版本。 */
export function useSkillRollbackMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (param: SkillRollbackParam) =>
      skillApi.rollback(param.skillNum, param.targetVersion),
    onSuccess: (_, param) => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.detail(param.skillNum) });
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.versions(param.skillNum) });
    },
  });
}

/** v2.11：下架（接替原 deprecate）。 */
export function useSkillUnpublishMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => skillApi.unpublish(num),
    onSuccess: (_, num) => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.detail(num) });
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.page() });
    },
  });
}

/** 删除 */
export function useSkillDeleteMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (skillNum: string) => skillApi.delete(skillNum),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: skillQueryKeys.page() });
    },
  });
}

/** 2026-06-10：资源文件树（version 空取草稿树，非空取版本快照树）。 */
export function useSkillResourceTreeQuery(num: string, version?: string) {
  return useQuery({
    queryKey: skillQueryKeys.resourceTree(num, version),
    queryFn: () => skillApi.resourceTree(num, version),
    enabled: !!num,
  });
}

/** 2026-06-10：检测记录分页列表。 */
export function useSkillCheckRecordPageQuery(query: SkillCheckRecordPageQuery) {
  return useQuery({
    queryKey: skillQueryKeys.checkRecords(query.skillNum),
    queryFn: () => skillApi.checkRecordPage(query),
    enabled: !!query.skillNum,
  });
}

/** 2026-06-10：检测记录详情。 */
export function useSkillCheckRecordDetailQuery(recordNum: string) {
  return useQuery({
    queryKey: skillQueryKeys.checkRecordDetail(recordNum),
    queryFn: () => skillApi.checkRecordDetail(recordNum),
    enabled: !!recordNum,
  });
}
