import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authzApi } from './api';
import type {
  PlatformRoleAssignParam,
  RoleCreateParam,
  RoleUpdateParam,
} from '@/types';

export const authzQueryKeys = {
  workspaceRoles: (workspaceNum?: string) =>
    ['authz', 'roles', 'workspace', workspaceNum ?? ''] as const,
  allRoles: () => ['authz', 'roles', 'all'] as const,
  roleDetail: (roleNum: string) => ['authz', 'role', roleNum] as const,
  permissions: (scope?: 'PLATFORM' | 'SPACE') =>
    ['authz', 'permissions', scope ?? 'ALL'] as const,
  myPermissions: (workspaceNum?: string) =>
    ['authz', 'me', workspaceNum ?? ''] as const,
  memberRoles: (workspaceNum?: string) =>
    ['authz', 'member-roles', workspaceNum ?? ''] as const,
  platformAdmins: () => ['authz', 'platform-admins'] as const,
  platformRoles: () => ['authz', 'platform-roles'] as const,
};

export function useWorkspaceRolesQuery(workspaceNum?: string) {
  return useQuery({
    queryKey: authzQueryKeys.workspaceRoles(workspaceNum),
    queryFn: () => authzApi.listInWorkspace(workspaceNum),
    enabled: Boolean(workspaceNum),
    staleTime: 60 * 1000,
  });
}

export function useAllRolesQuery(enabled = true) {
  return useQuery({
    queryKey: authzQueryKeys.allRoles(),
    queryFn: () => authzApi.listAll(),
    enabled,
    staleTime: 60 * 1000,
  });
}

export function useRoleDetailQuery(roleNum: string | undefined) {
  return useQuery({
    queryKey: authzQueryKeys.roleDetail(roleNum ?? ''),
    queryFn: () => authzApi.detail(roleNum as string),
    enabled: Boolean(roleNum),
  });
}

export function usePermissionsQuery(scope?: 'PLATFORM' | 'SPACE', enabled = true) {
  return useQuery({
    queryKey: authzQueryKeys.permissions(scope),
    queryFn: () => authzApi.listPermissions(scope),
    enabled,
    staleTime: 30 * 60 * 1000,
  });
}

export function useMyPermissionsQuery(workspaceNum?: string) {
  return useQuery({
    queryKey: authzQueryKeys.myPermissions(workspaceNum),
    queryFn: () => authzApi.myPermissions(workspaceNum),
    enabled: Boolean(workspaceNum),
    staleTime: 5 * 60 * 1000,
  });
}

export function useMemberRolesQuery(workspaceNum?: string) {
  return useQuery({
    queryKey: authzQueryKeys.memberRoles(workspaceNum),
    queryFn: () => authzApi.listMemberRoles(workspaceNum),
    enabled: Boolean(workspaceNum),
  });
}

export function useCreateRoleMutation(workspaceNum?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: RoleCreateParam) => authzApi.create(param),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: authzQueryKeys.workspaceRoles(workspaceNum),
      });
    },
  });
}

export function useUpdateRoleMutation(workspaceNum?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: RoleUpdateParam) => authzApi.update(param),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({
        queryKey: authzQueryKeys.workspaceRoles(workspaceNum),
      });
      qc.invalidateQueries({
        queryKey: authzQueryKeys.roleDetail(vars.roleNum),
      });
    },
  });
}

export function useDeleteRoleMutation(workspaceNum?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (roleNum: string) => authzApi.delete(roleNum),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: authzQueryKeys.workspaceRoles(workspaceNum),
      });
    },
  });
}

export function useAssignPlatformRoleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: PlatformRoleAssignParam) =>
      authzApi.assignPlatformRole(param),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: authzQueryKeys.allRoles() });
      qc.invalidateQueries({ queryKey: authzQueryKeys.platformAdmins() });
    },
  });
}

export function useUnassignPlatformRoleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: PlatformRoleAssignParam) =>
      authzApi.unassignPlatformRole(param),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: authzQueryKeys.allRoles() });
      qc.invalidateQueries({ queryKey: authzQueryKeys.platformAdmins() });
    },
  });
}

export function useSaveUserPlatformRolesMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: { empNo: string; roleNums: string[] }) =>
      authzApi.saveUserPlatformRoles(param),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: authzQueryKeys.platformAdmins() });
    },
  });
}

export function usePlatformAdminsQuery(enabled = true) {
  return useQuery({
    queryKey: authzQueryKeys.platformAdmins(),
    queryFn: () => authzApi.listPlatformAdmins(),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function usePlatformRolesQuery(enabled = true) {
  return useQuery({
    queryKey: authzQueryKeys.platformRoles(),
    queryFn: () => authzApi.listPlatformRoles(),
    enabled,
    staleTime: 60 * 1000,
  });
}

export function useCreatePlatformRoleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: RoleCreateParam) => authzApi.createPlatformRole(param),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: authzQueryKeys.platformRoles() });
      qc.invalidateQueries({ queryKey: authzQueryKeys.allRoles() });
    },
  });
}

export function useUpdatePlatformRoleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (param: RoleUpdateParam) => authzApi.updatePlatformRole(param),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: authzQueryKeys.platformRoles() });
      qc.invalidateQueries({ queryKey: authzQueryKeys.allRoles() });
      qc.invalidateQueries({ queryKey: authzQueryKeys.roleDetail(vars.roleNum) });
    },
  });
}

export function useDeletePlatformRoleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (roleNum: string) => authzApi.deletePlatformRole(roleNum),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: authzQueryKeys.platformRoles() });
      qc.invalidateQueries({ queryKey: authzQueryKeys.allRoles() });
    },
  });
}
