/**
 * 鉴权 / 权限类型 — 与 BE 权限管理 v1.0 对齐
 * 详见 doc：2026-06-15_权限管理-技术方案.md（§3 facade / §7.2 接口 JSON）
 */

/** 角色作用域 */
export type RoleScope = 'PLATFORM' | 'SPACE';

/** 角色列表卡片 VO */
export interface RoleVO {
  roleNum: string;
  name: string;
  description?: string;
  scope: RoleScope;
  builtin: boolean;
  /** 当前角色被绑定的用户数（删除按钮禁用判定 + 列表展示） */
  assignedUserCount: number;
}

/** 单个权限项（在勾选面板里的最小单元） */
export interface PermissionVO {
  code: string;
  name: string;
  description?: string;
  /** RoleDetail 上下文里指示已勾选；listPermissions 全集查询时为 undefined */
  selected?: boolean;
}

/** 按资源域分组的权限项列表 */
export interface PermissionGroupVO {
  resourceDomain: string;
  resourceDomainName: string;
  permissions: PermissionVO[];
}

/** 角色权限明细 VO */
export interface RoleDetailVO {
  roleNum: string;
  name: string;
  description?: string;
  scope: RoleScope;
  workspaceNum?: string;
  builtin: boolean;
  permissionGroups: PermissionGroupVO[];
  assignedUserCount: number;
}

/** 全平台角色总览 VO */
export interface RoleSummaryVO {
  roleNum: string;
  name: string;
  description?: string;
  scope: RoleScope;
  workspaceNum?: string;
  workspaceName?: string;
  builtin: boolean;
  assignedUserCount: number;
  permissionCount: number;
}

/** "我的权限" 抽屉 VO */
export interface MyPermissionVO {
  userId: string;
  workspaceNum: string;
  isPlatformAdmin: boolean;
  roles: RoleVO[];
  permissionsByDomain: PermissionGroupVO[];
}

/** 创建角色入参 */
export interface RoleCreateParam {
  name: string;
  description?: string;
  permissionCodes: string[];
}

/** 编辑角色入参（整体覆盖） */
export interface RoleUpdateParam {
  roleNum: string;
  name: string;
  description?: string;
  permissionCodes: string[];
}

/** 平台角色赋人入参 */
export interface PlatformRoleAssignParam {
  empNo: string;
  platformRoleNum: string;
}

/** 内置角色业务编号常量（与 client.AuthzConstants 一致） */
export const ROLE_PLATFORM_ADMIN = 'RL-PLATFORM-ADMIN';
export const ROLE_SPACE_ADMIN = 'RL-SPACE-ADMIN';
export const ROLE_SPACE_MEMBER = 'RL-SPACE-MEMBER';
