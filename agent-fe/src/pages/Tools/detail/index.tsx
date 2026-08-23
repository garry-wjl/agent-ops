/**
 * 工具详情页 — `/tool/manage/detail/:num`
 *
 * 布局对齐 Agent 评测任务详情：EditorBreadcrumb → 标题区 →「基本信息」卡片（Descriptions）
 * → 下方专有内容（如 MCP 工具列表）。
 */
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Descriptions,
  Modal,
  Space,
  Tag,
  Typography,
  message,
} from "antd";
import {
  useToolDetailQuery,
  useMcpTestConnectionMutation,
} from "@/services/tool";
import type { McpRemoteToolInfo, ToolVO } from "@/types";
import { prettyJson } from "@/types";
import {
  CREATION_MODE_LABEL,
  TOOL_STATUS_META,
  TOOL_TYPE_META,
} from "../constants";
import MountedAgentsModal from "../list/MountedAgentsModal";
import McpToolListSection from "../editor/McpToolListSection";
import FcManualForm from "../editor/FcManualForm";
import { emptyDraft } from "../editor/types";
import { normalizeMcpTools } from "../editor/mcpTools";
import EditorBreadcrumb from "@/components/EditorBreadcrumb";
import JsonEditor from "@/components/JsonEditor";
import UserName from "@/components/UserName";
import { useBreadcrumbName } from "@/hooks/useBreadcrumbName";
import PermissionGate from "@/components/PermissionGate";

const { Text, Title, Link } = Typography;

const COLOR = {
  border: "#E2E8F0",
  headerBg: "#ffffff",
  textPrimary: "#0F172B",
  textSecondary: "#45556C",
  textMuted: "#90A1B9",
} as const;

const STATUS_TAG_COLOR: Record<string, string> = {
  DRAFT: "warning",
  PUBLISHED: "success",
  DEPRECATED: "default",
};

export default function ToolDetailPage() {
  const { num = "" } = useParams();
  const navigate = useNavigate();
  const { data, isLoading } = useToolDetailQuery(num || undefined);
  const tool = data?.tool;
  useBreadcrumbName(tool?.name);

  const [mountedOpen, setMountedOpen] = useState(false);
  const [mcpConfigViewOpen, setMcpConfigViewOpen] = useState(false);
  const [mcpProxyViewOpen, setMcpProxyViewOpen] = useState(false);
  const [mcpTools, setMcpTools] = useState<McpRemoteToolInfo[]>([]);
  const [toolsLoaded, setToolsLoaded] = useState(false);
  const testMut = useMcpTestConnectionMutation();

  useEffect(() => {
    setMcpTools([]);
    setToolsLoaded(false);
  }, [num]);

  useEffect(() => {
    if (!tool) return;
    if (tool.type !== "MCP" || tool.creationMode !== "REMOTE") return;
    if (!tool.mcpConfig?.trim()) return;
    let cancelled = false;
    (async () => {
      try {
        const result = await testMut.mutateAsync({
          mcpConfigType: tool.mcpConfigType,
          mcpConfig: tool.mcpConfig,
          proxyEnabled: tool.proxyEnabled,
          proxyHeaders: tool.proxyHeaders,
        });
        if (cancelled) return;
        if (result.success) {
          setMcpTools(normalizeMcpTools(result.tools));
          setToolsLoaded(true);
        } else {
          setToolsLoaded(false);
        }
      } catch {
        if (!cancelled) setToolsLoaded(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tool?.num, tool?.mcpConfig]);

  if (isLoading || !tool) {
    return (
      <div style={{ padding: 32 }}>
        <Text type="secondary">加载中…</Text>
      </div>
    );
  }

  const statusMeta =
    TOOL_STATUS_META[tool.status] ?? { label: tool.status, color: "#90A1B9" };
  const typeMeta = TOOL_TYPE_META[tool.type];
  const isMcpRemote = tool.type === "MCP" && tool.creationMode === "REMOTE";
  const isFcManual =
    tool.type === "FUNCTION_CALL" && tool.creationMode === "MANUAL";
  const isFcOpenApi =
    tool.type === "FUNCTION_CALL" && tool.creationMode === "OPENAPI_SPEC";

  const fcManualDraft = {
    ...emptyDraft(),
    baseUrl: tool.baseUrl ?? "",
    endpoints: tool.endpoints ?? [],
  };

  return (
    <div style={{ padding: 32, background: "#fff", minHeight: "100%" }}>
      <EditorBreadcrumb
        listPath="/tool/manage"
        moduleName="工具管理"
        current={tool.name}
      />

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          marginBottom: 24,
          gap: 16,
          flexWrap: "wrap",
        }}
      >
        <div>
          <Space align="center" style={{ marginBottom: 4 }} wrap>
            <Title
              level={3}
              style={{ margin: 0, color: COLOR.textPrimary, fontWeight: 700 }}
            >
              {tool.name}
            </Title>
            <Tag
              style={{
                background: typeMeta.bg,
                color: typeMeta.color,
                border: 0,
              }}
            >
              {typeMeta.label}
            </Tag>
            <Tag color={STATUS_TAG_COLOR[tool.status] ?? "default"}>
              {statusMeta.label}
            </Tag>
          </Space>
          <Text
            style={{
              fontFamily: "ui-monospace, monospace",
              fontSize: 13,
              color: COLOR.textMuted,
              display: "block",
            }}
          >
            {tool.num}
          </Text>
          {tool.description ? (
            <Text
              style={{
                color: COLOR.textSecondary,
                display: "block",
                marginTop: 8,
                maxWidth: 640,
              }}
            >
              {tool.description}
            </Text>
          ) : null}
        </div>
        <Space wrap>
          {tool.status !== "DEPRECATED" && (
            <PermissionGate anyOf={["tool:update"]}>
              <Button
                type="primary"
                onClick={() => navigate(`/tool/manage/editor/${tool.num}`)}
              >
                编辑
              </Button>
            </PermissionGate>
          )}
        </Space>
      </div>

      <div
        style={{
          marginBottom: 20,
          padding: "16px 20px",
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          background: COLOR.headerBg,
        }}
      >
        <Text
          strong
          style={{
            display: "block",
            marginBottom: 12,
            color: COLOR.textPrimary,
            fontSize: 13,
          }}
        >
          基本信息
        </Text>
        <Descriptions
          size="small"
          column={{ xs: 1, sm: 2, md: 3 }}
          styles={{
            label: {
              color: COLOR.textMuted,
              whiteSpace: "nowrap",
              width: 108,
            },
          }}
        >
          <Descriptions.Item label="工具编号">
            <Text
              copyable
              style={{ fontFamily: "ui-monospace, monospace", fontSize: 12 }}
            >
              {tool.num}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="类型">
            <Tag
              style={{
                background: typeMeta.bg,
                color: typeMeta.color,
                border: 0,
                marginInlineEnd: 0,
              }}
            >
              {typeMeta.label}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={STATUS_TAG_COLOR[tool.status] ?? "default"}>
              {statusMeta.label}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="创建方式">
            {CREATION_MODE_LABEL[tool.creationMode]}
          </Descriptions.Item>
          <Descriptions.Item label="标签">
            {(tool.tags ?? []).length === 0 ? (
              <Text type="secondary">—</Text>
            ) : (
              <Space size={[6, 6]} wrap>
                {(tool.tags ?? []).map((t) => (
                  <Tag key={t} style={{ marginInlineEnd: 0 }}>
                    {t}
                  </Tag>
                ))}
              </Space>
            )}
          </Descriptions.Item>
          {tool.type === "MCP" ? (
            <Descriptions.Item label="复用数">
              <a onClick={() => setMountedOpen(true)}>
                {tool.reuseCount ?? 0} 个 Agent
              </a>
            </Descriptions.Item>
          ) : null}
          <Descriptions.Item label="创建人">
            <UserName userNum={tool.createNo} />
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {tool.createTime || "—"}
          </Descriptions.Item>
          <Descriptions.Item label="更新人">
            <UserName userNum={tool.updateNo} />
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {tool.updateTime || "—"}
          </Descriptions.Item>
          {tool.description ? (
            <Descriptions.Item label="描述" span={3}>
              {tool.description}
            </Descriptions.Item>
          ) : null}
          {renderShapeItems(tool, {
            onViewMcpConfig: () => setMcpConfigViewOpen(true),
            onViewMcpProxy: () => setMcpProxyViewOpen(true),
          })}
        </Descriptions>
      </div>

      {isFcManual ? (
        <div style={{ marginBottom: 20 }}>
          <FcManualForm draft={fcManualDraft} patch={() => {}} disabled />
        </div>
      ) : null}

      {isFcOpenApi ? (
        <div style={{ marginBottom: 20 }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: 12,
              gap: 12,
              flexWrap: "wrap",
            }}
          >
            <Text strong>OpenAPI 规范</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              端点数：
              {tool.endpointMeta?.endpointCount ?? "—（发布后解析）"}
            </Text>
          </div>
          {tool.openApiSpec?.trim() ? (
            <div
              style={{
                border: `1px solid ${COLOR.border}`,
                borderRadius: 8,
                overflow: "hidden",
              }}
            >
              <JsonEditor
                value={prettyJson(tool.openApiSpec, tool.openApiSpec)}
                readOnly
                height={420}
              />
            </div>
          ) : (
            <Text type="secondary">暂无 OpenAPI 原文</Text>
          )}
        </div>
      ) : null}

      {isMcpRemote && (toolsLoaded || testMut.isPending) ? (
        <div style={{ marginBottom: 20 }}>
          <McpToolListSection
            tools={mcpTools}
            loading={testMut.isPending}
            hint="进入详情后自动拉取，可手动刷新"
            extra={
              <Button
                size="small"
                loading={testMut.isPending}
                onClick={async () => {
                  try {
                    const result = await testMut.mutateAsync({
                      mcpConfigType: tool.mcpConfigType,
                      mcpConfig: tool.mcpConfig,
                      proxyEnabled: tool.proxyEnabled,
                      proxyHeaders: tool.proxyHeaders,
                    });
                    if (result.success) {
                      setMcpTools(normalizeMcpTools(result.tools));
                      setToolsLoaded(true);
                      message.success(result.message || "已刷新工具列表");
                    } else {
                      message.error(result.message || "刷新失败");
                    }
                  } catch (e: unknown) {
                    const err = e as { message?: string };
                    message.error(err?.message || "刷新失败");
                  }
                }}
              >
                刷新
              </Button>
            }
          />
        </div>
      ) : null}

      <MountedAgentsModal
        open={mountedOpen}
        toolNum={tool.num}
        toolName={tool.name}
        onClose={() => setMountedOpen(false)}
      />

      <Modal
        title="MCP 配置"
        open={mcpConfigViewOpen}
        onCancel={() => setMcpConfigViewOpen(false)}
        footer={
          <Button type="primary" onClick={() => setMcpConfigViewOpen(false)}>
            关闭
          </Button>
        }
        width={720}
        destroyOnHidden
      >
        <JsonEditor
          value={prettyJson(tool.mcpConfig, tool.mcpConfig || "{}")}
          readOnly
          height={360}
        />
      </Modal>

      <Modal
        title="MCP 代理"
        open={mcpProxyViewOpen}
        onCancel={() => setMcpProxyViewOpen(false)}
        footer={
          <Button type="primary" onClick={() => setMcpProxyViewOpen(false)}>
            关闭
          </Button>
        }
        width={560}
        destroyOnHidden
      >
        {(tool.proxyHeaders ?? []).length === 0 ? (
          <Text type="secondary">已启用，暂无透传请求头</Text>
        ) : (
          <Descriptions
            size="small"
            column={1}
            bordered
            styles={{
              label: {
                color: COLOR.textMuted,
                width: 140,
                whiteSpace: "nowrap",
              },
            }}
          >
            {(tool.proxyHeaders ?? []).map((h, i) => (
              <Descriptions.Item label={h.name || `Header ${i + 1}`} key={i}>
                <Text
                  style={{
                    fontFamily: "ui-monospace, monospace",
                    fontSize: 12,
                    wordBreak: "break-all",
                  }}
                >
                  {h.value}
                </Text>
              </Descriptions.Item>
            ))}
          </Descriptions>
        )}
      </Modal>
    </div>
  );
}

function renderShapeItems(
  tool: ToolVO,
  actions: {
    onViewMcpConfig: () => void;
    onViewMcpProxy: () => void;
  },
): React.ReactNode[] {
  const items: React.ReactNode[] = [];

  if (tool.type === "MCP" && tool.creationMode === "REMOTE") {
    items.push(
      <Descriptions.Item label="MCP 配置类型" key="mct">
        {tool.mcpConfigType === "LOCAL"
          ? "本地（stdio）"
          : "远程（sse / streamable-http）"}
      </Descriptions.Item>,
      <Descriptions.Item label="MCP 配置" key="mc">
        {tool.mcpConfig?.trim() ? (
          <Link onClick={actions.onViewMcpConfig}>点击查看</Link>
        ) : (
          <Text type="secondary">—</Text>
        )}
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
        <Descriptions.Item label="OpenAPI 原文" key="oas" span={3}>
          <CodeBlock text={tool.openApiSpec} />
        </Descriptions.Item>,
      );
    }
  }

  if (tool.type === "MCP") {
    items.push(
      <Descriptions.Item label="MCP 代理" key="proxy">
        {tool.proxyEnabled ? (
          <Link onClick={actions.onViewMcpProxy}>点击查看</Link>
        ) : (
          "未启用"
        )}
      </Descriptions.Item>,
    );
  }

  return items;
}

function CodeBlock({ text }: { text?: string }) {
  if (!text) return <Text type="secondary">—</Text>;
  return (
    <Text
      style={{
        display: "block",
        margin: 0,
        maxHeight: 240,
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
    </Text>
  );
}
