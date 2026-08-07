/**
 * 用户管理 API — 与 BE /api/v1/users、/api/v1/auth/login 对齐
 */
import { get, post } from '../request';

export interface UserVO {
  num: string;
  username: string;
  email: string;
  remark?: string;
  status: 'ENABLED' | 'DISABLED' | string;
}

export interface UserDetailVO extends UserVO {
  platformRoleNums?: string[];
}

export interface UserBriefVO {
  num: string;
  username: string;
  email?: string;
}

export interface UserPageResult {
  list: UserVO[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface UserPageQuery {
  keyword?: string;
  status?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface LoginResultVO {
  userNum: string;
  username: string;
}

export const userApi = {
  page: (query: UserPageQuery) =>
    get<UserPageResult>('/api/v1/users/page', query as Record<string, unknown>),

  detail: (num: string) =>
    get<UserDetailVO>('/api/v1/users/detail', { num }),

  searchEnabled: (keyword: string, limit = 20) =>
    get<UserBriefVO[]>('/api/v1/users/search-enabled', { keyword, limit }),

  create: (body: {
    username: string;
    email: string;
    remark?: string;
    password: string;
  }) => post<UserVO>('/api/v1/users/create', body),

  update: (body: {
    num: string;
    username: string;
    email: string;
    remark?: string;
  }) => post<UserVO>('/api/v1/users/update', body),

  enable: (num: string) => post<void>('/api/v1/users/enable', { num }),

  disable: (num: string) => post<void>('/api/v1/users/disable', { num }),

  resetPassword: (num: string, password: string) =>
    post<void>('/api/v1/users/reset-password', { num, password }),

  savePlatformRoles: (num: string, roleNums: string[]) =>
    post<void>('/api/v1/users/save-platform-roles', { num, roleNums }),

  login: (username: string, password: string) =>
    post<LoginResultVO>('/api/v1/auth/login', { username, password }),

  logout: () => post<void>('/api/v1/auth/logout'),
};

export const userQueryKeys = {
  all: ['users'] as const,
  page: (q: UserPageQuery) => [...userQueryKeys.all, 'page', q] as const,
  detail: (num: string) => [...userQueryKeys.all, 'detail', num] as const,
};
