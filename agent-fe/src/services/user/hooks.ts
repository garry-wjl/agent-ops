import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userApi, userQueryKeys, type UserPageQuery } from './api';

export function useUsersPageQuery(query: UserPageQuery) {
  return useQuery({
    queryKey: userQueryKeys.page(query),
    queryFn: () => userApi.page(query),
  });
}

export function useUserDetailQuery(num?: string) {
  return useQuery({
    queryKey: userQueryKeys.detail(num ?? ''),
    queryFn: () => userApi.detail(num!),
    enabled: Boolean(num),
  });
}

export function useCreateUserMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: userApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: userQueryKeys.all }),
  });
}

export function useUpdateUserMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: userApi.update,
    onSuccess: () => qc.invalidateQueries({ queryKey: userQueryKeys.all }),
  });
}

export function useEnableUserMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => userApi.enable(num),
    onSuccess: () => qc.invalidateQueries({ queryKey: userQueryKeys.all }),
  });
}

export function useDisableUserMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (num: string) => userApi.disable(num),
    onSuccess: () => qc.invalidateQueries({ queryKey: userQueryKeys.all }),
  });
}

export function useResetPasswordMutation() {
  return useMutation({
    mutationFn: ({ num, password }: { num: string; password: string }) =>
      userApi.resetPassword(num, password),
  });
}

export function useSaveUserPlatformRolesMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ num, roleNums }: { num: string; roleNums: string[] }) =>
      userApi.savePlatformRoles(num, roleNums),
    onSuccess: () => qc.invalidateQueries({ queryKey: userQueryKeys.all }),
  });
}
