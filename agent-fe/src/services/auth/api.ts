/**
 * 当前用户 — 部门统一登录中间件已注入 Cookie；前端仅从 /api/v1/auth/me 拿身份回显 + 权限
 */
import { get } from '../request';

export interface CurrentUser {
  userId: string;
  userName: string;
  /** 兼容旧字段：viewer / editor / admin */
  role: 'viewer' | 'editor' | 'admin';
  avatar?: string;
  /** 是否平台管理员（权限管理 v1.0 起后端返回） */
  isPlatformAdmin?: boolean;
  /** 当前工作空间业务编号（X-Workspace-Num 解析得到） */
  currentWorkspaceNum?: string;
  /** 当前空间持有的角色 num 列表 */
  currentWorkspaceRoles?: string[];
  /** 权限并集（含 platform_admin 全集 / 当前空间角色并集） */
  permissions?: string[];
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return get<CurrentUser>('/api/v1/auth/me');
}
