import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchCurrentUser } from './api';

export const authQueryKeys = {
  current: () => ['auth', 'current'] as const,
};

/** 获取当前登录用户信息 */
export function useCurrentUserQuery() {
  return useQuery({
    queryKey: authQueryKeys.current(),
    queryFn: () => fetchCurrentUser(),
    retry: false,
    staleTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
  });
}

/** 登出 */
export function useLogoutMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {},
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: authQueryKeys.current() });
    },
  });
}
