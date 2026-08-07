/**
 * 工具管理服务 — 与 BE 工具管理技术方案 v1.0 对齐
 *
 * 路径约定（技术方案 §7 / §12，base /api/v1/tool）：
 * - 命令（POST，/command）：create / update / publish / unpublish / republish / deleteDraft
 * - 查询（GET，/query）：page / detail / mountable / reuseCount / mountedAgents / checkName
 *
 * 创建仅落草稿（§0 #11）；「直接发布」由调用方在 create 成功后再调 publish。
 * 当前工作空间编号经 request 拦截器自动注入 X-Workspace-Num 头（/api/v1/tool 不在 skip-list），
 * 故读写均无需手动传 workspaceNum。
 */
import { get, post } from "../request";
import type {
  AgentBriefVO,
  McpTestConnectionParam,
  McpTestConnectionResult,
  PageVO,
  ToolCreateParam,
  ToolDetailVO,
  ToolNameExists,
  ToolNumParam,
  ToolPageQueryParam,
  ToolUpdateParam,
  ToolVO,
} from "@/types";

export const toolApi = {
  // ============================================================
  // 命令：POST /api/v1/tool/command/*
  // ============================================================

  /** 新建工具（仅落草稿态；返回带 num 的 ToolVO） */
  create: (param: ToolCreateParam) =>
    post<ToolVO>("/api/v1/tool/command/create", param),

  /** 编辑工具（type/creationMode 不可改；任何编辑后 status 回 DRAFT） */
  update: (param: ToolUpdateParam) =>
    post<void>("/api/v1/tool/command/update", param),

  /** 发布（DRAFT → PUBLISHED；全字段校验 + OpenAPI 端点解析） */
  publish: (param: ToolNumParam) =>
    post<void>("/api/v1/tool/command/publish", param),

  /** 弃用（PUBLISHED → DEPRECATED；FC 工具被 MCP 打包引用时后端拒绝并 toast） */
  unpublish: (param: ToolNumParam) =>
    post<void>("/api/v1/tool/command/unpublish", param),

  /** 重新发布（DEPRECATED → PUBLISHED） */
  republish: (param: ToolNumParam) =>
    post<void>("/api/v1/tool/command/republish", param),

  /** 删除草稿（仅 DRAFT 可物理删；非草稿后端禁删并 toast） */
  deleteDraft: (param: ToolNumParam) =>
    post<void>("/api/v1/tool/command/deleteDraft", param),

  // ============================================================
  // 查询：GET /api/v1/tool/query/*
  // ============================================================

  /** 分页查询当前空间内的工具列表（每行附实时 reuseCount） */
  pageList: (query: ToolPageQueryParam) =>
    get<PageVO<ToolVO>>("/api/v1/tool/query/page", query),

  /** 工具详情（全字段 + endpointMeta + reuseCount） */
  detail: (num: string) =>
    get<ToolDetailVO>("/api/v1/tool/query/detail", { num }),

  /** Agent Step4 可挂载工具列表（仅 status=PUBLISHED） */
  mountable: () => get<ToolVO[]>("/api/v1/tool/query/mountable"),

  /** 复用数（实时统计挂载该工具的已发布 Agent 数） */
  reuseCount: (num: string) =>
    get<number>("/api/v1/tool/query/reuseCount", { num }),

  /** 复用数下钻：挂载该工具的已发布 Agent 简表 */
  mountedAgents: (num: string) =>
    get<AgentBriefVO[]>("/api/v1/tool/query/mountedAgents", { num }),

  /**
   * 工作空间内 name 唯一性预检（失焦校验）。
   * 返回 true 表示已存在（冲突）；excludeNum 用于编辑态排除自身。
   */
  checkName: (name: string, excludeNum?: string) =>
    get<ToolNameExists>("/api/v1/tool/query/checkName", { name, excludeNum }),

  // ============================================================
  // MCP 测试连接
  // ============================================================

  /** 测试 MCP 远程连接（不持久化数据）。 */
  testMcpConnection: (param: McpTestConnectionParam) =>
    post<McpTestConnectionResult>("/api/v1/tool/command/testMcpConnection", param),
};
