/**
 * MCP 远程连接表单（type=MCP, creationMode=REMOTE）— PRD §8.3 / §7.1.3 / §7.3。
 *
 * - 配置类型（远程 / 本地）segmented 胶囊切换，必选；下方一行随类型说明。
 * - 配置类型 + MCP 配置 JSON 用线框圈为一个整体。
 * - JSON 编辑器右上角提供「示例」（按类型填入样例）「复制」操作。
 * - Monaco JSON 编辑器（配置同 DebugSender）；validateMcpConfig 实时红/绿提示。
 * - 末尾嵌 ProxyHeadersEditor（代理开关；开启后才显示透传 Header 配置）。
 */
import Editor from "@monaco-editor/react";
import { Button, Segmented, Space, Tooltip, Typography, message } from "antd";
import { ApiOutlined, CodeOutlined, CopyOutlined, GlobalOutlined } from "@ant-design/icons";
import type { McpConfigType } from "@/types";
import { validateMcpConfig } from "../constants";
import type { ToolFormProps } from "./types";
import ProxyHeadersEditor from "./ProxyHeadersEditor";
import { useMcpTestConnectionMutation } from "@/services/tool";

const { Text } = Typography;

const LOCAL_SAMPLE = `{
  "command": "npx",
  "args": ["-y", "@amap/amap-maps-mcp-server"],
  "env": {
    "AMAP_MAPS_API_KEY": "your-api-key"
  }
}`;

const REMOTE_SAMPLE = `{
  "url": "https://mcp.example.com/mcp",
  "transport": "streamable-http",
  "headers": { "Authorization": "Bearer xxx" }
}`;

/** 配置类型说明（随选中类型展示一行）。 */
const CONFIG_TYPE_DESC: Record<McpConfigType, string> = {
  REMOTE: "远程 MCP 通过 SSE 或 streamable-http 协议连接已部署的 MCP 服务器。",
  LOCAL: "本地 MCP 通过 npx 或 uvx 命令启动 MCP 服务器，使用 stdio 协议通信。",
};

const sampleOf = (t: McpConfigType) =>
  t === "LOCAL" ? LOCAL_SAMPLE : REMOTE_SAMPLE;

export default function McpRemoteForm({ draft, patch }: ToolFormProps) {
  const validation = draft.mcpConfig.trim()
    ? validateMcpConfig(draft.mcpConfig, draft.mcpConfigType)
    : null;

  const testMutation = useMcpTestConnectionMutation();

  const handleTestConnection = async () => {
    if (!draft.mcpConfig.trim()) {
      message.warning("请先填写 MCP 配置");
      return;
    }
    try {
      const result = await testMutation.mutateAsync({
        mcpConfigType: draft.mcpConfigType,
        mcpConfig: draft.mcpConfig,
        proxyEnabled: draft.proxyEnabled,
        proxyHeaders: draft.proxyHeaders.filter((h) => h.name.trim()),
      });
      if (result.success) {
        message.success(result.message ?? "连接成功！");
      } else {
        // 连接失败：展示详细的错误信息，含堆栈
        message.error(
          <div>
            <div><strong>{result.errorType ?? "连接失败"}</strong></div>
            <div style={{ fontSize: 12, whiteSpace: "pre-wrap", maxHeight: 300, overflow: "auto" }}>
              {result.message}
              {result.stackTrace && (
                <>
                  <br />
                  <details>
                    <summary style={{ cursor: "pointer", color: "#1677ff" }}>查看详情</summary>
                    <pre style={{ fontSize: 11, marginTop: 4 }}>{result.stackTrace}</pre>
                  </details>
                </>
              )}
            </div>
          </div>,
          5,
        );
      }
    } catch (e: any) {
      message.error("测试连接请求失败: " + (e?.message ?? "未知错误"));
    }
  };

  const switchType = (t: McpConfigType) => {
    // 切换配置类型时若编辑器为空则填入对应样例，降低录入门槛
    patch({
      mcpConfigType: t,
      mcpConfig: draft.mcpConfig.trim() ? draft.mcpConfig : sampleOf(t),
    });
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(draft.mcpConfig ?? "");
      message.success("已复制");
    } catch {
      message.error("复制失败");
    }
  };

  const handleInsertSample = () => {
    patch({ mcpConfig: sampleOf(draft.mcpConfigType) });
  };

  const sampleLabel = draft.mcpConfigType === "LOCAL" ? "本地示例" : "远程示例";

  return (
    <div>
      {/* 配置类型 segmented 切换 */}
      <div style={{ marginBottom: 8 }}>
        <Segmented<McpConfigType>
          value={draft.mcpConfigType}
          onChange={(v) => switchType(v)}
          options={[
            { value: "REMOTE", label: "远程", icon: <GlobalOutlined /> },
            { value: "LOCAL", label: "本地", icon: <CodeOutlined /> },
          ]}
        />
      </div>
      <Text type="secondary" style={{ fontSize: 13 }}>
        {CONFIG_TYPE_DESC[draft.mcpConfigType]}
      </Text>

      {/* MCP 配置 JSON 框（带标题栏：示例 + 复制） */}
      <div
        style={{
          border: "1px solid #E2E8F0",
          borderRadius: 8,
          overflow: "hidden",
          marginTop: 12,
        }}
      >
        {/* 标题栏 */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "10px 16px",
            background: "#F8FAFC",
            borderBottom: `1px solid ${
              validation && !validation.ok ? "#FCA5A5" : "#E2E8F0"
            }`,
          }}
        >
          <Space size={8}>
            <Text strong>MCP配置</Text>
            {validation && (
              <span
                style={{
                  color: validation.ok ? "#16A34A" : "#DC2626",
                  fontSize: 12,
                }}
              >
                {validation.ok ? "✓ 配置格式正确" : `✗ ${validation.error}`}
              </span>
            )}
          </Space>
          <Space size={8}>
            <Button
              type="primary"
              size="small"
              icon={<ApiOutlined />}
              loading={testMutation.isPending}
              onClick={handleTestConnection}
            >
              测试连接
            </Button>
            <span
              onClick={handleInsertSample}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 4,
                cursor: "pointer",
                padding: "2px 10px",
                border: "1px solid #E2E8F0",
                borderRadius: 6,
                background: "#fff",
                color: "#2563EB",
                fontSize: 13,
              }}
            >
              <CodeOutlined />
              {sampleLabel}
            </span>
            <Tooltip title="复制">
              <CopyOutlined
                onClick={handleCopy}
                style={{ color: "#45556C", cursor: "pointer", fontSize: 16 }}
              />
            </Tooltip>
          </Space>
        </div>
        {/* 编辑器 */}
        <Editor
          height="240px"
          defaultLanguage="json"
          value={draft.mcpConfig}
          onChange={(v) => patch({ mcpConfig: v ?? "" })}
          options={{
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            fontSize: 13,
            tabSize: 2,
            wordWrap: "on",
            lineNumbers: "on",
            folding: true,
          }}
        />
      </div>

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
