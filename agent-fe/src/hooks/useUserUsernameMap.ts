/**
 * 全局用户编号 → 用户名映射（react-query 缓存）。
 * 列表 / 详情「创建人」「更新人」等审计字段统一用此解析，避免直接展示 USR-xxx。
 * <p>数据源：GET /api/v1/common/users/display-map（已登录即可，不要求用户管理权限）。
 */
import { useQuery } from '@tanstack/react-query';
import { commonApi } from '@/services/common/api';

export const userUsernameMapQueryKey = ['users', 'username-map'] as const;

/** 拉取并缓存全站用户名映射（5 分钟内复用）。 */
export function useUserUsernameMap() {
  return useQuery({
    queryKey: userUsernameMapQueryKey,
    queryFn: () => commonApi.userDisplayMap(),
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
  });
}

/** 将用户编号（或历史 username）解析为展示名；未知时回退原值。 */
export function resolveUsername(
  map: Record<string, string> | undefined,
  userNum?: string | null,
): string {
  if (!userNum) return '—';
  return map?.[userNum] ?? userNum;
}
