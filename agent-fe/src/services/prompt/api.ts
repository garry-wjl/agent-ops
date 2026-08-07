/**
 * Prompt 中心服务 — 与 BE《Prompt 中心 技术方案 v1.0》对齐。
 *
 * 路径约定（技术方案 §7.2.1，base /api/v1/prompt）：
 * - 命令（POST，/command）：create / update / delete
 * - 查询（GET，/query）：page / detail / checkKey
 *
 * 当前工作空间编号经 request 拦截器自动注入 X-Workspace-Num 头（/api/v1/prompt 不在 skip-list），
 * 故读写均无需手动传 workspaceNum。num 系统生成，前端不传。
 */
import { get, post } from "../request";
import type {
  PageVO,
  PromptCreateParam,
  PromptDetailVo,
  PromptKeyExists,
  PromptNumParam,
  PromptPageQueryParam,
  PromptUpdateParam,
  PromptVo,
} from "@/types";

export const promptApi = {
  // ============================================================
  // 命令：POST /api/v1/prompt/command/*
  // ============================================================

  /** 新建 Prompt（返回带系统生成 num 的 PromptVo）。 */
  create: (param: PromptCreateParam) =>
    post<PromptVo>("/api/v1/prompt/command/create", param),

  /** 编辑 Prompt（按 num 加载后覆盖可填字段；promptKey 变更触发后端唯一性预检）。 */
  update: (param: PromptUpdateParam) =>
    post<void>("/api/v1/prompt/command/update", param),

  /** 软删除 Prompt（无状态约束，直接软删）。 */
  delete: (param: PromptNumParam) =>
    post<void>("/api/v1/prompt/command/delete", param),

  // ============================================================
  // 查询：GET /api/v1/prompt/query/*
  // ============================================================

  /** 分页查询当前空间内的 Prompt 列表（keyword / tag 筛选，按 update_time DESC）。 */
  pageList: (query: PromptPageQueryParam) =>
    get<PageVO<PromptVo>>("/api/v1/prompt/query/page", query),

  /** Prompt 详情（全字段）。 */
  detail: (num: string) =>
    get<PromptDetailVo>("/api/v1/prompt/query/detail", { num }),

  /**
   * 工作空间内 promptKey 唯一性预检（失焦校验）。
   * 返回 true 表示已存在（冲突）；excludeNum 用于编辑态排除自身。
   */
  checkKey: (promptKey: string, excludeNum?: string) =>
    get<PromptKeyExists>("/api/v1/prompt/query/checkKey", {
      promptKey,
      excludeNum,
    }),
};
