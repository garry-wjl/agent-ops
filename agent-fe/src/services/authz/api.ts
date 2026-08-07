/**
 * 鉴权服务 — 与 BE 权限管理技术方案 v1.0 对齐
 *
 * 接口（技术方案 §7.2）：
 * - 命令：POST /api/v1/roles/create | update | delete；POST /api/v1/platform-roles/assign
 * - 查询：GET  /api/v1/roles/list-in-workspace | list-all | detail | list-member-roles
 *         GET  /api/v1/permissions/list
 *         GET  /api/v1/roles/my-permissions
 */
import { get, post } from '../request';
import type {
  MyPermissionVO,
  PermissionGroupVO,
  PlatformRoleAssignParam,
  RoleCreateParam,
  RoleDetailVO,
  RoleSummaryVO,
  RoleUpdateParam,
  RoleVO,
} from '@/types';

export const authzApi = {
  /** 空间内全部角色（内置 + 自定义） */
  listInWorkspace: (workspaceNum?: string) =>
    get<RoleVO[]>('/api/v1/roles/list-in-workspace', { workspaceNum }),

  /** 全平台角色总览（platform_admin） */
  listAll: () => get<RoleSummaryVO[]>('/api/v1/roles/list-all'),

  /** 角色权限明细（编辑/查看抽屉用） */
  detail: (roleNum: string) =>
    get<RoleDetailVO>('/api/v1/roles/detail', { roleNum }),

  /** 全部权限元数据（按资源域分组；scope=PLATFORM 仅平台域，SPACE 仅空间域，缺省返回全集） */
  listPermissions: (scope?: 'PLATFORM' | 'SPACE') =>
    get<PermissionGroupVO[]>('/api/v1/permissions/list', { scope }),

  /** 「我的权限」抽屉 */
  myPermissions: (workspaceNum?: string) =>
    get<MyPermissionVO>('/api/v1/roles/my-permissions', { workspaceNum }),

  /** 编辑空间抽屉：成员 → 角色映射（empNo → RoleVO[]） */
  listMemberRoles: (workspaceNum?: string) =>
    get<Record<string, RoleVO[]>>('/api/v1/roles/list-member-roles', {
      workspaceNum,
    }),

  /** 创建自定义空间角色 */
  create: (param: RoleCreateParam) =>
    post<string>('/api/v1/roles/create', param),

  /** 编辑自定义空间角色（整体覆盖） */
  update: (param: RoleUpdateParam) => post<void>('/api/v1/roles/update', param),

  /** 删除自定义空间角色（被用户绑定时拒绝） */
  delete: (roleNum: string) =>
    post<void>('/api/v1/roles/delete', { roleNum }),

  /** platform_admin 分配平台角色 */
  assignPlatformRole: (param: PlatformRoleAssignParam) =>
    post<void>('/api/v1/platform-roles/assign', param),

  /** platform_admin 解除某工号的平台角色 */
  unassignPlatformRole: (param: PlatformRoleAssignParam) =>
    post<void>('/api/v1/platform-roles/unassign', param),

  /** 覆盖式保存某工号的全部平台角色（roleNums 空 → 解除所有） */
  saveUserPlatformRoles: (param: { empNo: string; roleNums: string[] }) =>
    post<void>('/api/v1/platform-roles/save-user-roles', param),

  /** 列出全部平台管理员（empNo → 平台角色 VO 列表） */
  listPlatformAdmins: () =>
    get<Record<string, RoleVO[]>>('/api/v1/platform-roles/list-admins'),

  /** 列出全部平台级角色（scope=PLATFORM，内置 + 自定义） */
  listPlatformRoles: () =>
    get<RoleSummaryVO[]>('/api/v1/platform-roles/role/list'),

  /** 创建平台自定义角色 */
  createPlatformRole: (param: RoleCreateParam) =>
    post<string>('/api/v1/platform-roles/role/create', param),

  /** 编辑平台自定义角色（整体覆盖；内置角色禁止编辑） */
  updatePlatformRole: (param: RoleUpdateParam) =>
    post<void>('/api/v1/platform-roles/role/update', param),

  /** 删除平台自定义角色（被绑定时拒绝；内置角色禁止删除） */
  deletePlatformRole: (roleNum: string) =>
    post<void>('/api/v1/platform-roles/role/delete', { roleNum }),
};
