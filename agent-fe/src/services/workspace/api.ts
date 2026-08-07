/**
 * 工作空间服务 — 与 BE 工作空间管理技术方案 v1.0 对齐
 *
 * 路径约定（技术方案 §5）：
 * - 读：GET /api/v1/workspace/list        我可见的全部空间（不分页）
 *       GET /api/v1/workspace/detail?num  空间详情（编辑抽屉用）
 * - 写：POST /api/v1/workspace/create     创建
 *       POST /api/v1/workspace/update     编辑（整体覆盖名称/描述/两栏成员）
 *       POST /api/v1/workspace/delete     逻辑删除（资产非空会被 422 拦截）
 *
 * 仅 创建 / 编辑 / 删除 三个写操作；成员增删与角色调整都在 update 里整体提交。
 * 员工搜索是通用接口，走 commonApi.searchEmployees（/api/v1/common/employee/search）。
 */
import { get, post } from '../request';
import type {
  WorkspaceCreateParam,
  WorkspaceDeleteParam,
  WorkspaceDetailVO,
  WorkspaceUpdateParam,
  WorkspaceVO,
} from '@/types';

export const workspaceApi = {
  /** 我创建 + 我加入的全部空间（不分页，前端再做搜索过滤） */
  list: () => get<WorkspaceVO[]>('/api/v1/workspace/list'),

  /** 空间详情（含成员列表，用于编辑抽屉） */
  detail: (num: string) =>
    get<WorkspaceDetailVO>('/api/v1/workspace/detail', { num }),

  /** 创建空间（创建人自动入管理员） */
  create: (param: WorkspaceCreateParam) =>
    post<WorkspaceVO>('/api/v1/workspace/create', param),

  /** 编辑空间（整体覆盖：名称 + 描述 + 完整 adminEmpNos / memberEmpNos + 整空间用户-角色映射） */
  update: (param: WorkspaceUpdateParam) =>
    post<void>('/api/v1/workspace/update', param),

  /**
   * 逻辑删除空间。资产非空时后端抛业务异常（message 含各类资产数量），
   * 由全局响应拦截器统一 toast；调用方 catch 后保持弹窗打开即可。
   */
  delete: (param: WorkspaceDeleteParam) =>
    post<void>('/api/v1/workspace/delete', param),
};
