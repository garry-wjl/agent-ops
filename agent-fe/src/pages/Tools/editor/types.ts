/**
 * 工具编辑器内部共享草稿模型。
 *
 * 父编辑器（editor/index.tsx）持有单一 ToolDraft；按 creationMode 把相关字段切片
 * 透传给 4 个表单子组件。子组件经 patch(partial) 上抛局部变更，父统一在保存/发布时
 * 组装 ToolCreateParam / ToolUpdateParam。
 */
import type {
  ApiEndpoint,
  McpConfigType,
  PackageMode,
  ProxyHeader,
} from "@/types";

export interface ToolDraft {
  // —— 公共字段 ——
  name: string;
  description: string;
  tags: string[];

  // —— MCP 远程连接 ——
  mcpConfigType: McpConfigType;
  mcpConfig: string;

  // —— MCP 代理（MCP 两形态共用）——
  proxyEnabled: boolean;
  proxyHeaders: ProxyHeader[];

  // —— MCP API 打包 ——
  packageMode: PackageMode;
  sourceFcToolNum?: string;

  // —— OpenAPI 原文（API_PACKAGE-OPENAPI_PASTE / FC-OPENAPI_SPEC 共用）——
  openApiSpec: string;

  // —— FC 手动录入 ——
  baseUrl: string;
  endpoints: ApiEndpoint[];
}

/** 子表单统一 props：拿到完整草稿 + 局部更新回调。 */
export interface ToolFormProps {
  draft: ToolDraft;
  patch: (partial: Partial<ToolDraft>) => void;
  /** 编辑态字段是否只读（type/creationMode 锁定不在此控制，此处控形态字段是否禁改场景预留） */
  disabled?: boolean;
}

/** 空草稿（新建默认值）。 */
export function emptyDraft(): ToolDraft {
  return {
    name: "",
    description: "",
    tags: [],
    mcpConfigType: "REMOTE",
    mcpConfig: "",
    proxyEnabled: false,
    proxyHeaders: [],
    packageMode: "EXISTING_API",
    sourceFcToolNum: undefined,
    openApiSpec: "",
    baseUrl: "",
    endpoints: [],
  };
}
