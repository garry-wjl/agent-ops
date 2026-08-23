import type { McpRemoteToolInfo } from "@/types";

/** 试连结果 tools 字段规范化（过滤空行）。 */
export function normalizeMcpTools(
  tools?: McpRemoteToolInfo[] | null,
): McpRemoteToolInfo[] {
  if (!tools || !Array.isArray(tools)) return [];
  return tools.filter((t) => t && (t.name || t.title || t.description));
}
