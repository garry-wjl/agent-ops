/**
 * 工具管理类型 — 与后端 rd-agent-be 工具管理技术方案 v1.0 对齐
 *
 * 字段以技术方案 §4.2.1 领域模型 / §7 接口契约 / §8.1 表结构为准：
 * - 审计字段为 `createNo / updateNo / createTime / updateTime`（非 createdBy/createdAt）
 * - `reuseCount` 系统派生（查询时实时扫 agent.config_snapshot.mcpNums 统计），前端只读
 * - 单一 Tool 聚合，4 种形态专有字段共用一个 VO；按 type+creationMode 条件渲染
 *
 * 后端命令接口仅 create / update / publish / unpublish / republish / deleteDraft，
 * 创建仅落草稿；「直接发布」= create 后再 publish（两接口分离，见技术方案 §0 #11）。
 */
import type { PageParam } from "./common";

/** 工具类型：MCP server / FunctionCall HTTP API（建好不可改）。 */
export type ToolType = "MCP" | "FUNCTION_CALL";

/**
 * 创建方式（建好不可改）。
 * - REMOTE       MCP 远程连接（自备 endpoint）
 * - API_PACKAGE  MCP API 打包（已有 API / 粘贴 OpenAPI 包成 MCP）
 * - OPENAPI_SPEC FunctionCall OpenAPI Spec 导入
 * - MANUAL       FunctionCall 手动录入（Base URL + 多端点）
 */
export type ToolCreationMode =
  | "REMOTE"
  | "API_PACKAGE"
  | "OPENAPI_SPEC"
  | "MANUAL";

/** 工具生命周期状态（三态机，见技术方案 §4.2）。 */
export type ToolStatus = "DRAFT" | "PUBLISHED" | "DEPRECATED";

/** MCP 配置子类型：本地 stdio / 远程 sse|streamable-http。 */
export type McpConfigType = "LOCAL" | "REMOTE";

/** MCP API 打包方式：引用已发布 FC 工具 / 粘贴 OpenAPI 文档。 */
export type PackageMode = "EXISTING_API" | "OPENAPI_PASTE";

/** HTTP 请求方式（FC 手动录入端点用）。 */
export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH";

/** FC 手动录入参数类型。 */
export type ApiParamType = "string" | "number" | "boolean" | "integer";

/** MCP 代理透传请求头（value 可含 `{变量名}` 占位符，运行时由平台注入）。 */
export interface ProxyHeader {
  /** Header 名（工具内不重复，大小写不敏感） */
  name: string;
  /** 值：字面值或 `{变量名}` 占位符 */
  value: string;
  /** 描述（可选） */
  description?: string;
}

/** FC 手动录入 — Query / Path 参数。 */
export interface ApiParam {
  /** 参数名（合法变量名：字母 + 数字 + 下划线） */
  name: string;
  /** 参数类型 */
  type: ApiParamType;
  /** 默认值（字符串存储，运行时按 type 反序列化） */
  defaultValue?: string;
  /** 描述（≤200 字） */
  description?: string;
}

/** FC 手动录入 — 请求头（不支持变量占位）。 */
export interface ApiHeader {
  /** Header 名 */
  name: string;
  /** 默认值（字面值） */
  defaultValue?: string;
  /** 描述（≤200 字） */
  description?: string;
}

/** FC 手动录入 — 单个 API 端点。 */
export interface ApiEndpoint {
  /** 请求方式 */
  method: HttpMethod;
  /** 路径（以 / 开头，可含 {paramName} 占位，与 pathParams 一一对应） */
  path: string;
  /** 端点用途描述（≤200 字，给 LLM 看） */
  description: string;
  /** Query 参数 */
  queryParams?: ApiParam[];
  /** Path 参数（与 path 占位符一一对应） */
  pathParams?: ApiParam[];
  /** 请求头 */
  headers?: ApiHeader[];
}

/** 发布时由后端解析 OpenAPI 得到的单端点摘要。 */
export interface EndpointSummary {
  /** 请求方式 */
  method: string;
  /** 路径 */
  path: string;
  /** 摘要（summary / description） */
  summary?: string;
}

/** 发布时解析的端点元数据（OPENAPI_SPEC / API_PACKAGE-OPENAPI_PASTE）。 */
export interface EndpointMeta {
  /** 端点数 */
  endpointCount: number;
  /** 每端点摘要 */
  summaries: EndpointSummary[];
}

/**
 * 工具视图对象（列表项 / 命令返回 / 详情，与后端 ToolVO 一一对应）。
 * 4 种形态专有字段共用此 VO，按 type + creationMode 条件取用。
 */
export interface ToolVO {
  /** 业务编号 MCP+/FC+ 前缀 + yyyyMMddHHmm + 序号 */
  num: string;
  /** 归属工作空间业务编号 */
  workspaceNum: string;
  /** 工具名称（工作空间内唯一，≤128） */
  name: string;
  /** 工具描述（≤500） */
  description: string;
  /** 工具类型（建好不可改） */
  type: ToolType;
  /** 创建方式（建好不可改） */
  creationMode: ToolCreationMode;
  /** 标签（≤20 个，单 tag ≤32） */
  tags?: string[];
  /** 状态 */
  status: ToolStatus;
  /** 复用数：被多少已发布 Agent 挂载（系统派生，草稿态 Agent 不计） */
  reuseCount: number;

  // —— MCP 远程连接专有（type=MCP, creationMode=REMOTE）——
  /** MCP 配置子类型 */
  mcpConfigType?: McpConfigType;
  /** MCP 配置 JSON 原文（≤64KB） */
  mcpConfig?: string;
  /** 是否启用平台 MCP 代理 */
  proxyEnabled?: boolean;
  /** 透传请求头数组 */
  proxyHeaders?: ProxyHeader[];

  // —— MCP API 打包专有（type=MCP, creationMode=API_PACKAGE）——
  /** 打包方式 */
  packageMode?: PackageMode;
  /** 来源 FC 工具 num（EXISTING_API，动态跟随其最新已发布配置） */
  sourceFcToolNum?: string;

  // —— OpenAPI 原文（OPENAPI_SPEC / API_PACKAGE-OPENAPI_PASTE）——
  /** OpenAPI/Swagger 原文（≤1MB） */
  openApiSpec?: string;

  // —— FC 手动录入专有（type=FUNCTION_CALL, creationMode=MANUAL）——
  /** Base URL（所有端点共用） */
  baseUrl?: string;
  /** 端点数组 */
  endpoints?: ApiEndpoint[];

  /** 发布时解析的端点元数据（OpenAPI 形态） */
  endpointMeta?: EndpointMeta;

  /** 负责人 / 创建人用户 ID */
  ownerUserId?: string;
  /** 创建人工号 */
  createNo: string;
  /** 更新人工号 */
  updateNo: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/** 工具详情（后端 ToolDetailVO 为嵌套结构，预留扩展）。 */
export interface ToolDetailVO {
  /** 工具全字段快照 */
  tool: ToolVO;
}

/** 挂载该工具的 Agent 简表项（复用数下钻，与后端 AgentBriefVO 对齐）。 */
export interface AgentBriefVO {
  /** Agent 业务编号 */
  num: string;
  /** Agent 名称 */
  name: string;
  /** Agent 状态 */
  status?: string;
}

/**
 * 新建工具入参（与后端 ToolCreateParam 对齐）。
 * 仅落草稿；workspaceNum 由后端经 X-Workspace-Num 头取，前端不传。
 * 各形态专有字段按 type + creationMode 条件填充。
 */
export interface ToolCreateParam {
  /** 工具名称（必填，≤128） */
  name: string;
  /** 工具描述（必填，≤500） */
  description: string;
  /** 工具类型（必填） */
  type: ToolType;
  /** 创建方式（必填） */
  creationMode: ToolCreationMode;
  /** 标签（可选） */
  tags?: string[];

  /** MCP 配置子类型（MCP-REMOTE） */
  mcpConfigType?: McpConfigType;
  /** MCP 配置 JSON 原文（MCP-REMOTE） */
  mcpConfig?: string;
  /** 是否启用代理（MCP 两形态） */
  proxyEnabled?: boolean;
  /** 透传请求头（MCP 两形态） */
  proxyHeaders?: ProxyHeader[];

  /** 打包方式（MCP-API_PACKAGE） */
  packageMode?: PackageMode;
  /** 来源 FC 工具 num（EXISTING_API） */
  sourceFcToolNum?: string;
  /** OpenAPI 原文（OPENAPI_PASTE / OPENAPI_SPEC） */
  openApiSpec?: string;

  /** Base URL（FC-MANUAL） */
  baseUrl?: string;
  /** 端点数组（FC-MANUAL） */
  endpoints?: ApiEndpoint[];
}

/**
 * 编辑工具入参（与后端 ToolUpdateParam 对齐）。
 * type / creationMode 不可改（后端忽略入参）；任何编辑后 status 回 DRAFT。
 */
export interface ToolUpdateParam {
  /** 工具业务编号（必填） */
  num: string;
  /** 工具名称 */
  name?: string;
  /** 工具描述 */
  description?: string;
  /** 标签 */
  tags?: string[];

  /** MCP 配置子类型 */
  mcpConfigType?: McpConfigType;
  /** MCP 配置 JSON 原文 */
  mcpConfig?: string;
  /** 是否启用代理 */
  proxyEnabled?: boolean;
  /** 透传请求头 */
  proxyHeaders?: ProxyHeader[];

  /** 打包方式 */
  packageMode?: PackageMode;
  /** 来源 FC 工具 num */
  sourceFcToolNum?: string;
  /** OpenAPI 原文 */
  openApiSpec?: string;

  /** Base URL */
  baseUrl?: string;
  /** 端点数组 */
  endpoints?: ApiEndpoint[];
}

/** 工具列表分页查询入参（继承通用分页参数；筛选字段均可选）。 */
export interface ToolPageQueryParam extends PageParam {
  /** 按类型筛选 */
  type?: ToolType;
  /** 按创建方式筛选 */
  creationMode?: ToolCreationMode;
  /** 按状态筛选 */
  status?: ToolStatus;
  /** 按标签筛选 */
  tag?: string;
  /** 关键词（在 num / name / description 内 LIKE 匹配） */
  keyword?: string;
}

/** 单编号操作入参（publish / unpublish / republish / deleteDraft 复用，对齐后端 ToolNumParam）。 */
export interface ToolNumParam {
  /** 工具业务编号 */
  num: string;
}

/** name 唯一性校验结果（GET /api/v1/tool/query/checkName 返回 boolean：true=已存在）。 */
export type ToolNameExists = boolean;

// ============================================================
// MCP 测试连接
// ============================================================

/** MCP 测试连接入参。 */
export interface McpTestConnectionParam {
  /** MCP 配置子类型：LOCAL / REMOTE */
  mcpConfigType?: McpConfigType;
  /** MCP 配置 JSON 原文 */
  mcpConfig?: string;
  /** 是否启用平台 MCP 代理 */
  proxyEnabled?: boolean;
  /** 透传请求头 */
  proxyHeaders?: ProxyHeader[];
}

/** MCP 测试连接结果。 */
export interface McpTestConnectionResult {
  /** 是否测试成功 */
  success: boolean;
  /** 连接成功的提示 / 失败的错误信息 */
  message?: string;
  /** 失败时的错误类型 */
  errorType?: string;
  /** 失败时的详细堆栈信息 */
  stackTrace?: string;
}
