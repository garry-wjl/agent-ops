/**
 * 沙箱管理服务 — 与 BE 沙箱管理技术方案 v1.0 对齐
 *
 * 路径约定（技术方案 §7.2.1，base /api/v1/sandbox）：
 * - 读：GET  /api/v1/sandbox/page    按当前空间 + type/status/keyword 分页
 *       GET  /api/v1/sandbox/detail  详情（全字段快照）
 * - 写：POST /api/v1/sandbox/create   新建草稿
 *       POST /api/v1/sandbox/update   编辑（按状态约束可改字段，后端裁定）
 *       POST /api/v1/sandbox/delete   软删（在线态禁删，后端拦截）
 *       POST /api/v1/sandbox/submit   提交（草稿/失败 → 初始化，异步建容器）
 *       POST /api/v1/sandbox/offline  下线（在线 → 下线）
 *       POST /api/v1/sandbox/reonline 重新上线（下线 → 初始化，重走供给）
 *
 * 当前工作空间编号经 request 拦截器自动注入 X-Workspace-Num 头（/api/v1/sandbox 不在 skip-list），
 * 故读写均无需手动传 workspaceNum。上线无独立接口：submit 后由后端 SandboxRunner 异步建容器并转 ONLINE。
 */
import { get, post } from "../request";
import type {
  PageVO,
  SandboxCreateParam,
  SandboxDetailVO,
  SandboxOperateParam,
  SandboxPageQueryParam,
  SandboxUpdateParam,
  SandboxVO,
} from "@/types";

export const sandboxApi = {
  /** 分页查询当前空间内的沙箱列表 */
  pageList: (query: SandboxPageQueryParam) =>
    get<PageVO<SandboxVO>>("/api/v1/sandbox/page", query),

  /** 沙箱详情（全字段 + 当前状态） */
  detail: (num: string) =>
    get<SandboxDetailVO>("/api/v1/sandbox/detail", { num }),

  /** 新建沙箱（草稿态落库） */
  create: (param: SandboxCreateParam) =>
    post<SandboxVO>("/api/v1/sandbox/create", param),

  /** 编辑沙箱（按当前状态约束可改字段，由后端裁定） */
  update: (param: SandboxUpdateParam) =>
    post<void>("/api/v1/sandbox/update", param),

  /** 软删沙箱（在线态后端禁删，会抛业务异常由拦截器 toast） */
  delete: (param: SandboxOperateParam) =>
    post<void>("/api/v1/sandbox/delete", param),

  /** 提交沙箱（草稿 / 失败 → 初始化 + 异步建容器） */
  submit: (param: SandboxOperateParam) =>
    post<void>("/api/v1/sandbox/submit", param),

  /** 下线沙箱（在线 → 下线 + 异步 kill 容器） */
  offline: (param: SandboxOperateParam) =>
    post<void>("/api/v1/sandbox/offline", param),

  /** 重新上线沙箱（下线 → 初始化，重走供给流程） */
  reonline: (param: SandboxOperateParam) =>
    post<void>("/api/v1/sandbox/reonline", param),
};
