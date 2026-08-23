/**
 * MCP 工具列表：名称 / 标题 / 描述，可展开查看入参与返回 schema。
 * 布局对齐 Agent 评测详情：标题行无边框，下方表格包一层描边容器。
 */
import type { ReactNode } from "react";
import { Empty, Table, Typography } from "antd";
import type { McpRemoteToolInfo } from "@/types";

const { Text, Paragraph } = Typography;

const COLOR = {
  border: "#E2E8F0",
  headerBg: "#ffffff",
  textMuted: "#90A1B9",
} as const;

const TABLE_STYLE = `
  .mcp-tool-list-row > td {
    padding: 14px 16px !important;
    border-bottom: 1px solid ${COLOR.border} !important;
  }
  .mcp-tool-list-table .ant-table-thead > tr > th {
    background: ${COLOR.headerBg} !important;
    color: ${COLOR.textMuted} !important;
    font-size: 11px !important;
    font-weight: 700 !important;
    letter-spacing: 0.06em !important;
    text-transform: uppercase;
    padding: 10px 16px !important;
    border-bottom: 1px solid ${COLOR.border} !important;
    white-space: nowrap !important;
  }
  .mcp-tool-list-table .ant-table-thead > tr > th::before { display: none !important; }
`;

function SchemaBlock({
  title,
  schema,
}: {
  title: string;
  schema?: Record<string, unknown> | null;
}) {
  if (!schema || Object.keys(schema).length === 0) {
    return (
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {title}：无
        </Text>
      </div>
    );
  }
  const props = schema.properties as Record<string, unknown> | undefined;
  const required = Array.isArray(schema.required)
    ? (schema.required as string[])
    : [];
  const paramRows =
    props && typeof props === "object"
      ? Object.entries(props).map(([name, raw]) => {
          const node =
            raw && typeof raw === "object"
              ? (raw as Record<string, unknown>)
              : {};
          return {
            name,
            type: String(node.type ?? "-"),
            required: required.includes(name),
            description: String(node.description ?? ""),
          };
        })
      : [];

  return (
    <div style={{ marginBottom: 12 }}>
      <Text strong style={{ fontSize: 12 }}>
        {title}
      </Text>
      {paramRows.length > 0 ? (
        <Table
          size="small"
          pagination={false}
          style={{ marginTop: 6 }}
          rowKey="name"
          dataSource={paramRows}
          columns={[
            {
              title: "参数名",
              dataIndex: "name",
              width: 140,
              render: (v: string) => (
                <Text code style={{ fontSize: 12 }}>
                  {v}
                </Text>
              ),
            },
            { title: "类型", dataIndex: "type", width: 100 },
            {
              title: "必填",
              dataIndex: "required",
              width: 64,
              render: (v: boolean) => (v ? "是" : "否"),
            },
            {
              title: "描述",
              dataIndex: "description",
              ellipsis: true,
              render: (v: string) => v || "-",
            },
          ]}
        />
      ) : null}
      <Paragraph
        style={{
          margin: paramRows.length ? "8px 0 0" : "6px 0 0",
          maxHeight: 160,
          overflow: "auto",
          background: "#F8FAFC",
          padding: 8,
          borderRadius: 4,
          fontSize: 11,
          fontFamily:
            'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
          whiteSpace: "pre-wrap",
        }}
      >
        {JSON.stringify(schema, null, 2)}
      </Paragraph>
    </div>
  );
}

function countParams(schema?: Record<string, unknown>): number {
  const props = schema?.properties;
  if (!props || typeof props !== "object") return 0;
  return Object.keys(props).length;
}

export default function McpToolListSection({
  tools,
  loading,
  hint = "连接认证通过后自动拉取",
  extra,
}: {
  tools: McpRemoteToolInfo[];
  loading?: boolean;
  hint?: string;
  /** 标题行右侧附加操作（如刷新按钮） */
  extra?: ReactNode;
}) {
  return (
    <div>
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
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <Text strong>工具列表</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 {tools.length} 个（{hint}）
          </Text>
        </div>
        {extra ? <div>{extra}</div> : null}
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: "hidden",
        }}
      >
        <Table<McpRemoteToolInfo>
          className="mcp-tool-list-table"
          size="middle"
          loading={loading}
          rowKey={(r, i) => r.name || `mcp-tool-${i}`}
          rowClassName={() => "mcp-tool-list-row"}
          pagination={
            tools.length > 8
              ? { pageSize: 8, size: "small", showSizeChanger: false }
              : false
          }
          dataSource={tools}
          locale={{
            emptyText: (
              <Empty
                description="该 MCP 服务器未返回任何工具"
                style={{ padding: 32 }}
              />
            ),
          }}
          expandable={{
            expandedRowRender: (record) => (
              <div style={{ padding: "4px 8px 8px" }}>
                <SchemaBlock
                  title="入参（inputSchema）"
                  schema={record.inputSchema}
                />
                <SchemaBlock
                  title="返回值（outputSchema）"
                  schema={record.outputSchema}
                />
              </div>
            ),
            rowExpandable: (record) =>
              !!(
                (record.inputSchema &&
                  Object.keys(record.inputSchema).length) ||
                (record.outputSchema &&
                  Object.keys(record.outputSchema).length)
              ),
          }}
          columns={[
            {
              title: "名称",
              dataIndex: "name",
              width: 180,
              render: (v: string) => (
                <Text code style={{ fontSize: 12 }}>
                  {v || "-"}
                </Text>
              ),
            },
            {
              title: "标题",
              dataIndex: "title",
              width: 140,
              render: (v?: string) => v || "-",
            },
            {
              title: "描述",
              dataIndex: "description",
              ellipsis: true,
              render: (v?: string) => v || "-",
            },
            {
              title: "参数",
              key: "params",
              width: 72,
              render: (_: unknown, r: McpRemoteToolInfo) => {
                const n = countParams(r.inputSchema);
                return n > 0 ? `${n} 个` : "-";
              },
            },
            {
              title: "返回",
              key: "output",
              width: 72,
              render: (_: unknown, r: McpRemoteToolInfo) =>
                r.outputSchema && Object.keys(r.outputSchema).length > 0
                  ? "有"
                  : "-",
            },
          ]}
        />
      </div>
      <style>{TABLE_STYLE}</style>
    </div>
  );
}
