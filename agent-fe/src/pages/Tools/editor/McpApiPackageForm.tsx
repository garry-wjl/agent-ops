/**
 * MCP API 打包表单（type=MCP, creationMode=API_PACKAGE）— PRD §8.4 / §7.1.4 / §7.4。
 *
 * 打包方式 radio：
 * - EXISTING_API：从已发布 FC 工具下拉选（useToolMountableQuery 过滤 type=FC），选中预览端点；
 *                  若来源工具已被弃用，顶部 Alert 提示重选（PRD §8.7）。
 * - OPENAPI_PASTE：Monaco 粘贴 OpenAPI + validateOpenApiSpec。
 * 末尾嵌 ProxyHeadersEditor（API 打包同样支持代理与 Header 透传）。
 *
 * 注：API 打包模式不存在 mcpConfig 字段（MCP 协议层由后端按打包来源自动生成）。
 */
import Editor from "@monaco-editor/react";
import { Alert, Empty, Radio, Select, Tag, Typography } from "antd";
import { useToolMountableQuery } from "@/services/tool";
import type { PackageMode } from "@/types";
import { validateOpenApiSpec } from "../constants";
import type { ToolFormProps } from "./types";
import ProxyHeadersEditor from "./ProxyHeadersEditor";

const { Text } = Typography;

export default function McpApiPackageForm({ draft, patch }: ToolFormProps) {
  const isExisting = draft.packageMode === "EXISTING_API";

  // 仅「已有 API」模式才拉取可选工具列表
  const { data: mountable = [], isFetching } =
    useToolMountableQuery(isExisting);
  // 候选：FunctionCall 类型的已发布工具
  const fcTools = mountable.filter((t) => t.type === "FUNCTION_CALL");
  const selected = fcTools.find((t) => t.num === draft.sourceFcToolNum);
  // 选中的来源 num 在候选中找不到（可能已被弃用 / 删除）
  const sourceMissing = !!draft.sourceFcToolNum && !selected && !isFetching;

  const validation =
    !isExisting && draft.openApiSpec.trim()
      ? validateOpenApiSpec(draft.openApiSpec)
      : null;

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <div style={{ marginBottom: 8 }}>
          <Text strong>打包方式</Text>
        </div>
        <Radio.Group
          value={draft.packageMode}
          onChange={(e) =>
            patch({ packageMode: e.target.value as PackageMode })
          }
        >
          <Radio value="EXISTING_API">选择已有 API</Radio>
          <Radio value="OPENAPI_PASTE">粘贴 OpenAPI / Swagger</Radio>
        </Radio.Group>
      </div>

      {isExisting ? (
        <div>
          {sourceMissing && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="来源 FC 工具已不可用（可能被弃用或删除），请重新选择或切换打包方式。"
            />
          )}
          <Text strong>从已发布 FunctionCall 工具中选择</Text>
          <Select
            style={{ width: "100%", marginTop: 8 }}
            placeholder="选择来源 FunctionCall 工具"
            loading={isFetching}
            value={draft.sourceFcToolNum}
            onChange={(v) => patch({ sourceFcToolNum: v })}
            options={fcTools.map((t) => ({
              value: t.num,
              label: `${t.name}（${t.num}${
                t.endpointMeta ? `，${t.endpointMeta.endpointCount} 端点` : ""
              }）`,
            }))}
            notFoundContent={
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无已发布的 FunctionCall 工具"
              />
            }
          />

          {selected && (
            <div
              style={{
                marginTop: 12,
                padding: 12,
                background: "#F8FAFC",
                borderRadius: 6,
              }}
            >
              <Text type="secondary">端点预览</Text>
              <div style={{ marginTop: 8 }}>
                {selected.endpointMeta?.summaries?.length ? (
                  selected.endpointMeta.summaries.map((s, i) => (
                    <div key={i} style={{ fontSize: 12, marginBottom: 4 }}>
                      <Tag color="blue">{s.method}</Tag>
                      <Text code>{s.path}</Text>{" "}
                      <Text type="secondary">{s.summary}</Text>
                    </div>
                  ))
                ) : (
                  <Text type="secondary">该工具暂无解析端点摘要</Text>
                )}
              </div>
            </div>
          )}
        </div>
      ) : (
        <div>
          <Text strong>OpenAPI / Swagger 文档（JSON）</Text>
          <div
            style={{
              border: `1px solid ${
                validation && !validation.ok ? "#DC2626" : "#E2E8F0"
              }`,
              borderRadius: 6,
              overflow: "hidden",
              marginTop: 8,
            }}
          >
            <Editor
              height="300px"
              defaultLanguage="json"
              value={draft.openApiSpec}
              onChange={(v) => patch({ openApiSpec: v ?? "" })}
              options={{
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                fontSize: 12,
                tabSize: 2,
                wordWrap: "on",
                lineNumbers: "on",
                folding: true,
              }}
            />
          </div>
          {validation && (
            <div
              style={{
                color: validation.ok ? "#16A34A" : "#DC2626",
                fontSize: 12,
                marginTop: 6,
              }}
            >
              {validation.ok
                ? `✓ 校验通过（识别到 ${validation.endpointCount} 个端点）`
                : `✗ ${validation.error}`}
            </div>
          )}
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <ProxyHeadersEditor
          proxyEnabled={draft.proxyEnabled}
          proxyHeaders={draft.proxyHeaders}
          onChange={({ proxyEnabled, proxyHeaders }) =>
            patch({ proxyEnabled, proxyHeaders })
          }
        />
      </div>
    </div>
  );
}
