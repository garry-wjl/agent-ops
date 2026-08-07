/**
 * 工具管理展示常量与前端校验器 — 列表 / 详情 / 编辑器共用。
 *
 * 校验对齐 PRD §7.3（mcpConfig 双 schema）/ §7.4（OpenAPI）/ §7.5（代理透传）/ §7.6（手动端点）。
 * 校验器为纯函数，返回 { ok, error }，供编辑器实时校验与「发布」按钮 disabled 判定。
 */
import type {
  ApiParam,
  ToolCreationMode,
  McpConfigType,
  ProxyHeader,
  ToolStatus,
  ToolType,
} from "@/types";

/** 状态 → 中文标签 + 主色（用于 `● 标签` 胶囊）。草稿黄 / 已发布绿 / 已废弃灰。 */
export const TOOL_STATUS_META: Record<
  ToolStatus,
  { label: string; color: string }
> = {
  DRAFT: { label: "草稿", color: "#D97706" },
  PUBLISHED: { label: "已发布", color: "#16A34A" },
  DEPRECATED: { label: "已废弃", color: "#90A1B9" },
};

/** 工具类型 → 标签 + 胶囊配色（MCP 蓝 / FunctionCall 紫）。 */
export const TOOL_TYPE_META: Record<
  ToolType,
  { label: string; color: string; bg: string }
> = {
  MCP: { label: "MCP", color: "#2563EB", bg: "#EFF6FF" },
  FUNCTION_CALL: { label: "FunctionCall", color: "#7C3AED", bg: "#F5F3FF" },
};

/** 创建方式 → 中文标签。 */
export const CREATION_MODE_LABEL: Record<ToolCreationMode, string> = {
  REMOTE: "远程连接",
  API_PACKAGE: "API 打包",
  OPENAPI_SPEC: "OpenAPI 导入",
  MANUAL: "手动录入",
};

/** 给定 type 下可选的创建方式（新建 Modal 第二步用）。 */
export const CREATION_MODES_BY_TYPE: Record<ToolType, ToolCreationMode[]> = {
  MCP: ["REMOTE"],
  FUNCTION_CALL: ["OPENAPI_SPEC", "MANUAL"],
};

/** 创建方式说明（新建 Modal 第二步副标题）。 */
export const CREATION_MODE_DESC: Record<ToolCreationMode, string> = {
  REMOTE: "自备 MCP server endpoint，平台只存配置并转发",
  API_PACKAGE: "把已有 API 或一段 OpenAPI 文档包成 MCP 工具",
  OPENAPI_SPEC: "粘贴 OpenAPI / Swagger 规范文档解析",
  MANUAL: "Base URL + 多端点逐项配置",
};

/** 各类约束上限（与 PRD §9 / 技术方案 §13 一致）。 */
export const TOOL_LIMITS = {
  NAME_MAX: 128,
  DESC_MAX: 500,
  TAG_MAX: 32,
  TAG_COUNT_MAX: 20,
  ENDPOINT_MAX: 50,
  ENDPOINT_DESC_MAX: 200,
  PROXY_HEADER_MAX: 20,
  /** OpenAPI 文档上限 1MB */
  OPENAPI_MAX_BYTES: 1024 * 1024,
  /** MCP 配置上限 64KB */
  MCP_CONFIG_MAX_BYTES: 64 * 1024,
} as const;

/** 平台内置变量清单（透传 Header 值的 `{变量名}` 占位，PRD §7.5.5）。 */
export const BUILTIN_VARIABLES: { name: string; description: string }[] = [
  { name: "userToken", description: "当前调用用户的 token" },
  { name: "userId", description: "当前调用用户 ID" },
  { name: "agentNum", description: "当前 Agent 业务编号" },
  { name: "traceId", description: "本次调用 trace ID" },
];

/** MCP 远程传输类型枚举（mcpConfigType=REMOTE）。 */
const MCP_TRANSPORTS = ["sse", "streamable-http"] as const;

/** 校验结果通用形态。 */
export interface ValidateResult {
  ok: boolean;
  error?: string;
}

/** UTF-8 字节长度（用于大小上限校验）。 */
function byteLength(s: string): number {
  return new TextEncoder().encode(s).length;
}

/**
 * 校验 MCP 配置 JSON（PRD §7.3）。
 * - LOCAL：command(必填,string) + args(string[],可选) + env(object<string,string>,可选)
 * - REMOTE：url(必填,合法 http/https) + transport(sse|streamable-http) + headers(object,可选)
 */
export function validateMcpConfig(
  text: string,
  configType: McpConfigType,
): ValidateResult {
  if (!text.trim()) return { ok: false, error: "请填写 MCP 配置 JSON" };
  if (byteLength(text) > TOOL_LIMITS.MCP_CONFIG_MAX_BYTES) {
    return { ok: false, error: "MCP 配置超过 64KB 上限" };
  }
  let obj: unknown;
  try {
    obj = JSON.parse(text);
  } catch (e) {
    return { ok: false, error: `JSON 格式错误：${(e as Error).message}` };
  }
  if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
    return { ok: false, error: "配置根节点必须是 JSON 对象" };
  }
  const o = obj as Record<string, unknown>;

  if (configType === "LOCAL") {
    if (typeof o.command !== "string" || !o.command.trim()) {
      return { ok: false, error: "本地配置缺少必填字段 command（string）" };
    }
    if (o.args !== undefined) {
      if (!Array.isArray(o.args) || o.args.some((a) => typeof a !== "string")) {
        return { ok: false, error: "args 必须是字符串数组" };
      }
    }
    if (o.env !== undefined) {
      if (
        typeof o.env !== "object" ||
        o.env === null ||
        Array.isArray(o.env) ||
        Object.values(o.env as Record<string, unknown>).some(
          (v) => typeof v !== "string",
        )
      ) {
        return { ok: false, error: "env 必须是 string→string 对象" };
      }
    }
    return { ok: true };
  }

  // REMOTE
  if (typeof o.url !== "string" || !/^https?:\/\//.test(o.url)) {
    return {
      ok: false,
      error: "远程配置 url 必填且需以 http:// 或 https:// 开头",
    };
  }
  if (
    typeof o.transport !== "string" ||
    !MCP_TRANSPORTS.includes(o.transport as (typeof MCP_TRANSPORTS)[number])
  ) {
    return {
      ok: false,
      error: "transport 必须为 sse 或 streamable-http",
    };
  }
  if (o.headers !== undefined) {
    if (
      typeof o.headers !== "object" ||
      o.headers === null ||
      Array.isArray(o.headers)
    ) {
      return { ok: false, error: "headers 必须是对象" };
    }
  }
  return { ok: true };
}

/** OpenAPI 校验结果（额外带识别到的端点数）。 */
export interface OpenApiValidateResult extends ValidateResult {
  endpointCount?: number;
}

/**
 * 校验 OpenAPI / Swagger 文档（PRD §7.4）。
 * 通过后前端不做语义提取，原样保存到 openApiSpec，由后端解析端点元数据。
 */
export function validateOpenApiSpec(text: string): OpenApiValidateResult {
  if (!text.trim())
    return { ok: false, error: "请粘贴 OpenAPI / Swagger 文档" };
  if (byteLength(text) > TOOL_LIMITS.OPENAPI_MAX_BYTES) {
    return { ok: false, error: "OpenAPI 文档超过 1MB 上限，请拆分" };
  }
  let doc: unknown;
  try {
    doc = JSON.parse(text);
  } catch (e) {
    return { ok: false, error: `JSON 格式错误：${(e as Error).message}` };
  }
  if (typeof doc !== "object" || doc === null || Array.isArray(doc)) {
    return { ok: false, error: "文档根节点必须是 JSON 对象" };
  }
  const o = doc as Record<string, unknown>;
  const isV3 = typeof o.openapi === "string";
  const isV2 = typeof o.swagger === "string";
  if (!isV3 && !isV2) {
    return { ok: false, error: "缺少 openapi (3.x) 或 swagger (2.0) 版本字段" };
  }
  if (typeof o.info !== "object" || o.info === null) {
    return { ok: false, error: "缺少必需字段：info" };
  }
  if (typeof o.paths !== "object" || o.paths === null) {
    return { ok: false, error: "缺少必需字段：paths" };
  }
  if (isV2 && typeof o.host !== "string") {
    return { ok: false, error: "Swagger 2.0 缺少必需字段：host" };
  }
  const paths = o.paths as Record<string, unknown>;
  const pathKeys = Object.keys(paths);
  if (pathKeys.length === 0) {
    return { ok: false, error: "至少需要一个 API 端点定义" };
  }
  // 统计端点数（path × method）并校验每端点有描述
  const HTTP_METHODS = [
    "get",
    "post",
    "put",
    "delete",
    "patch",
    "options",
    "head",
  ];
  let endpointCount = 0;
  for (const p of pathKeys) {
    const item = paths[p];
    if (typeof item !== "object" || item === null) continue;
    const ops = item as Record<string, unknown>;
    for (const m of HTTP_METHODS) {
      const op = ops[m];
      if (typeof op !== "object" || op === null) continue;
      endpointCount += 1;
      const opObj = op as Record<string, unknown>;
      if (
        typeof opObj.summary !== "string" &&
        typeof opObj.description !== "string"
      ) {
        return { ok: false, error: `端点 ${m.toUpperCase()} ${p} 缺少描述` };
      }
    }
  }
  if (endpointCount === 0) {
    return { ok: false, error: "至少需要一个 API 端点定义" };
  }
  return { ok: true, endpointCount };
}

/**
 * 校验透传请求头（PRD §7.5）。
 * - Header 名工具内不重复（大小写不敏感）；
 * - value 占位符仅允许 `{字母数字下划线}` 形式（可与字面值混排）。
 */
export function validateProxyHeaders(headers: ProxyHeader[]): ValidateResult {
  const seen = new Set<string>();
  for (const h of headers) {
    if (!h.name?.trim()) return { ok: false, error: "存在未填写名称的 Header" };
    const key = h.name.trim().toLowerCase();
    if (seen.has(key)) {
      return { ok: false, error: `Header 名「${h.name}」重复（大小写不敏感）` };
    }
    seen.add(key);
    // 校验占位符：提取所有 { ... }，每个必须匹配 {\w+}
    const placeholders = (h.value ?? "").match(/\{[^}]*\}/g) ?? [];
    for (const ph of placeholders) {
      if (!/^\{\w+\}$/.test(ph)) {
        return {
          ok: false,
          error: `Header「${h.name}」的占位符 ${ph} 非法，仅支持 {字母数字下划线}`,
        };
      }
    }
  }
  if (headers.length > TOOL_LIMITS.PROXY_HEADER_MAX) {
    return {
      ok: false,
      error: `透传 Header 不超过 ${TOOL_LIMITS.PROXY_HEADER_MAX} 条`,
    };
  }
  return { ok: true };
}

/**
 * 校验 path 占位符与 pathParams 一一对应（PRD §7.6 / §8.7）。
 * 返回缺失/多余的占位符名，供端点行级红字提示。
 */
export function validatePathParams(
  path: string,
  pathParams: ApiParam[] = [],
): ValidateResult {
  const inPath = new Set(
    (path.match(/\{(\w+)\}/g) ?? []).map((s) => s.slice(1, -1)),
  );
  const declared = new Set(
    pathParams.map((p) => p.name?.trim()).filter(Boolean),
  );
  for (const name of inPath) {
    if (!declared.has(name)) {
      return { ok: false, error: `Path 中 {${name}} 未在参数表定义` };
    }
  }
  for (const name of declared) {
    if (!inPath.has(name as string)) {
      return { ok: false, error: `参数「${name}」在 Path 中未声明` };
    }
  }
  return { ok: true };
}
