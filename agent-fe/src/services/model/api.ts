/**
 * 模型管理服务 — 与 BE 模型管理技术方案 v1.0 §7.2.1 + 2026-06-17 scope 优化 §7.2 对齐
 *
 * 两套入口（scope 由入口决定，不可跨入口）：
 * - 空间模型（base /api/v1/model）：工作空间级，X-Workspace-Num 头由 request 拦截器自动注入
 *     读：GET  /api/v1/model/page    按当前空间 + name/modelId/status/keyword 分页
 *         GET  /api/v1/model/detail  详情（全字段，apiKey 脱敏）
 *         GET  /api/v1/model/selectable  Agent 可选模型（系统启用 + 当前空间启用，无 Key）
 *     写：POST /api/v1/model/create   新建草稿
 *         POST /api/v1/model/update   编辑（状态不变；apiKey 留空保留原值）
 *         POST /api/v1/model/delete   软删（仅草稿态）
 *         POST /api/v1/model/enable   启用（DRAFT/DISABLED → ENABLED）
 *         POST /api/v1/model/disable  禁用（ENABLED → DISABLED）
 * - 系统模型（base /api/v1/system/model）：平台级，需 `system:manage_settings` 权限，
 *     后端按 PLATFORM 固定 scope，不注入工作空间头（已在 request 拦截器 skip-list 中）
 *     读：GET  /api/v1/system/model/page    分页
 *         GET  /api/v1/system/model/detail  详情（系统模型无任何 Key 字段）
 *     写：POST /api/v1/system/model/{create|update|delete|enable|disable}
 *
 * 兼容别名：GET /api/v1/models/selectable（复数路径，仅在前端已按 PRD 复数路径实现时使用）。
 * API Key 全程脱敏；系统模型连脱敏串都不返回。
 */
import { get, post } from '../request';
import type {
  ModelCreateParam,
  ModelDetailVO,
  ModelOperateParam,
  ModelPageQueryParam,
  ModelSelectableVO,
  ModelUpdateParam,
  ModelVO,
  PageVO,
} from '@/types';

export const modelApi = {
  // ===== 空间模型（/api/v1/model）=====

  /** 分页查询当前空间内的模型列表 */
  pageList: (query: ModelPageQueryParam) => get<PageVO<ModelVO>>('/api/v1/model/page', query),

  /** 模型详情（全字段，apiKey 脱敏；返回体含嵌套 model 字段） */
  detail: (num: string) => get<ModelDetailVO>('/api/v1/model/detail', { num }),

  /** Agent 可选模型：系统启用模型 + 当前空间启用模型；不含任何 Key 字段 */
  selectable: () => get<ModelSelectableVO[]>('/api/v1/model/selectable'),

  /** 兼容别名（复数路径） */
  selectableAlias: () => get<ModelSelectableVO[]>('/api/v1/models/selectable'),

  /** 新建模型（草稿态落库，返回含 num 的 ModelVO） */
  create: (param: ModelCreateParam) => post<ModelVO>('/api/v1/model/create', param),

  /** 编辑模型（状态不变，apiKey 留空保留原值） */
  update: (param: ModelUpdateParam) => post<void>('/api/v1/model/update', param),

  /** 软删模型（仅草稿态，后端 8003 拦截非法态由拦截器 toast） */
  delete: (param: ModelOperateParam) => post<void>('/api/v1/model/delete', param),

  /** 启用模型（DRAFT / DISABLED → ENABLED） */
  enable: (param: ModelOperateParam) => post<void>('/api/v1/model/enable', param),

  /** 禁用模型（ENABLED → DISABLED） */
  disable: (param: ModelOperateParam) => post<void>('/api/v1/model/disable', param),

  // ===== 系统模型（/api/v1/system/model，需 system:manage_settings）=====

  /** 分页查询系统模型（PLATFORM scope） */
  systemPageList: (query: ModelPageQueryParam) =>
    get<PageVO<ModelVO>>('/api/v1/system/model/page', query),

  /** 系统模型详情（无任何 Key 字段） */
  systemDetail: (num: string) => get<ModelDetailVO>('/api/v1/system/model/detail', { num }),

  /** 新建系统模型（PLATFORM scope，workspaceNum 后端置 null） */
  systemCreate: (param: ModelCreateParam) => post<ModelVO>('/api/v1/system/model/create', param),

  /** 编辑系统模型（状态不变，apiKey 留空保留原值） */
  systemUpdate: (param: ModelUpdateParam) => post<void>('/api/v1/system/model/update', param),

  /** 软删系统模型（仅草稿态） */
  systemDelete: (param: ModelOperateParam) => post<void>('/api/v1/system/model/delete', param),

  /** 启用系统模型（DRAFT / DISABLED → ENABLED） */
  systemEnable: (param: ModelOperateParam) => post<void>('/api/v1/system/model/enable', param),

  /** 禁用系统模型（ENABLED → DISABLED） */
  systemDisable: (param: ModelOperateParam) => post<void>('/api/v1/system/model/disable', param),
};
