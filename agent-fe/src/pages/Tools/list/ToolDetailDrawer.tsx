/**
 * 工具详情抽屉（PRD §8 / §7.2.4）。
 *
 * 全字段只读 + 按形态条件渲染：
 * - 通用：编号 / 名称 / 描述 / 类型 / 创建方式 / 状态 / 标签 / 复用数（可点下钻）/ 审计字段
 * - MCP-REMOTE：mcpConfigType + mcpConfig(JSON 折叠) + 代理 / 透传 Header
 * - MCP-API_PACKAGE：packageMode + sourceFcToolNum / openApiSpec + 代理
 * - FC-OPENAPI_SPEC：openApiSpec + endpointMeta
 * - FC-MANUAL：baseUrl + endpoints
 */
import { useState } from "react";
import { Descriptions, Drawer, Spin, Tag, Typography } from "antd";
import { useToolDetailQuery } from "@/services/tool";
import type { ToolVO } from "@/types";
import {
  CREATION_MODE_LABEL,
  TOOL_STATUS_META,
  TOOL_TYPE_META,
} from "../constants";
import MountedAgentsModal from "./MountedAgentsModal";

const { Text, Paragraph } = Typography;

interface ToolDetailDrawerProps {
  num?: string;
  open: boolean;
  onClose: () => void;
}

export default function ToolDetailDrawer({
  num,
  open,
  onClose,
}: ToolDetailDrawerProps) {
  const { data, isLoading } = useToolDetailQuery(open ? num : undefined);
  const tool = data?.tool;
  const [mountedOpen, setMountedOpen] = useState(false);

  return (
    <Drawer
      title="工具详情"
      width={640}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      {isLoading || !tool ? (
        <div style={{ textAlign: "center", padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <>
          <Descriptions column={1} bordered size="middle">
            <Descriptions.Item label="编号">{tool.num}</Descriptions.Item>
            <Descriptions.Item label="名称">{tool.name}</Descriptions.Item>
            <Descriptions.Item label="描述">
              {tool.description}
            </Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag
                color={TOOL_TYPE_META[tool.type].color}
                style={{ background: TOOL_TYPE_META[tool.type].bg, border: 0 }}
              >
                {TOOL_TYPE_META[tool.type].label}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建方式">
              {CREATION_MODE_LABEL[tool.creationMode]}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusPill status={tool.status} />
            </Descriptions.Item>
            <Descriptions.Item label="标签">
              {tool.tags?.length
                ? tool.tags.map((t) => <Tag key={t}>{t}</Tag>)
                : "—"}
            </Descriptions.Item>
            {/* 仅 MCP 可被 Agent 挂载，才展示复用数 */}
            {tool.type === "MCP" && (
              <Descriptions.Item label="复用数">
                <a onClick={() => setMountedOpen(true)}>
                  {tool.reuseCount} 个 Agent
                </a>
              </Descriptions.Item>
            )}

            {renderShapeFields(tool)}

            <Descriptions.Item label="创建人">
              {tool.createNo}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {tool.createTime}
            </Descriptions.Item>
            <Descriptions.Item label="更新人">
              {tool.updateNo}
            </Descriptions.Item>
            <Descriptions.Item label="更新时间">
              {tool.updateTime}
            </Descriptions.Item>
          </Descriptions>

          <MountedAgentsModal
            open={mountedOpen}
            toolNum={tool.num}
            toolName={tool.name}
            onClose={() => setMountedOpen(false)}
          />
        </>
      )}
    </Drawer>
  );
}

/** 按 type + creationMode 渲染形态专有字段。 */
function renderShapeFields(tool: ToolVO) {
  const items: React.ReactNode[] = [];

  if (tool.type === "MCP" && tool.creationMode === "REMOTE") {
    items.push(
      <Descriptions.Item label="MCP 配置类型" key="mct">
        {tool.mcpConfigType === "LOCAL"
          ? "本地（stdio）"
          : "远程（sse / streamable-http）"}
      </Descriptions.Item>,
      <Descriptions.Item label="MCP 配置" key="mc">
        <CodeBlock text={tool.mcpConfig} />
      </Descriptions.Item>,
    );
  }

  if (tool.type === "MCP" && tool.creationMode === "API_PACKAGE") {
    items.push(
      <Descriptions.Item label="打包方式" key="pm">
        {tool.packageMode === "EXISTING_API" ? "选择已有 API" : "粘贴 OpenAPI"}
      </Descriptions.Item>,
    );
    if (tool.packageMode === "EXISTING_API") {
      items.push(
        <Descriptions.Item label="来源 FC 工具" key="src">
          {tool.sourceFcToolNum || "—"}
        </Descriptions.Item>,
      );
    } else {
      items.push(
        <Descriptions.Item label="OpenAPI 原文" key="oas">
          <CodeBlock text={tool.openApiSpec} />
        </Descriptions.Item>,
      );
    }
  }

  if (tool.type === "MCP") {
    items.push(
      <Descriptions.Item label="MCP 代理" key="proxy">
        {tool.proxyEnabled ? "已启用" : "未启用"}
        {tool.proxyEnabled && tool.proxyHeaders?.length ? (
          <div style={{ marginTop: 6 }}>
            {tool.proxyHeaders.map((h, i) => (
              <div key={i} style={{ fontSize: 12 }}>
                <Text code>{h.name}</Text>: {h.value}
              </div>
            ))}
          </div>
        ) : null}
      </Descriptions.Item>,
    );
  }

  if (tool.type === "FUNCTION_CALL" && tool.creationMode === "OPENAPI_SPEC") {
    items.push(
      <Descriptions.Item label="端点数" key="ec">
        {tool.endpointMeta?.endpointCount ?? "—（发布后解析）"}
      </Descriptions.Item>,
      <Descriptions.Item label="OpenAPI 原文" key="oas2">
        <CodeBlock text={tool.openApiSpec} />
      </Descriptions.Item>,
    );
  }

  if (tool.type === "FUNCTION_CALL" && tool.creationMode === "MANUAL") {
    items.push(
      <Descriptions.Item label="Base URL" key="bu">
        {tool.baseUrl || "—"}
      </Descriptions.Item>,
      <Descriptions.Item label="端点" key="eps">
        {tool.endpoints?.length
          ? tool.endpoints.map((ep, i) => (
              <div key={i} style={{ fontSize: 12, marginBottom: 4 }}>
                <Tag color="blue">{ep.method}</Tag>
                <Text code>{ep.path}</Text>{" "}
                <Text type="secondary">{ep.description}</Text>
              </div>
            ))
          : "—"}
      </Descriptions.Item>,
    );
  }

  return items;
}

function CodeBlock({ text }: { text?: string }) {
  if (!text) return <Text type="secondary">—</Text>;
  return (
    <Paragraph
      style={{
        margin: 0,
        maxHeight: 200,
        overflow: "auto",
        background: "#F8FAFC",
        padding: 8,
        borderRadius: 4,
        fontSize: 12,
        fontFamily:
          'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
        whiteSpace: "pre-wrap",
      }}
    >
      {text}
    </Paragraph>
  );
}

function StatusPill({ status }: { status: keyof typeof TOOL_STATUS_META }) {
  const meta = TOOL_STATUS_META[status] ?? { label: status, color: "#90A1B9" };
  return (
    <span style={{ color: meta.color, fontWeight: 500 }}>● {meta.label}</span>
  );
}
