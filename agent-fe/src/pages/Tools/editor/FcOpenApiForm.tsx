/**
 * FunctionCall OpenAPI Spec 导入表单（type=FUNCTION_CALL, creationMode=OPENAPI_SPEC）
 * — PRD §8.5 / §7.1.5 / §7.4。
 *
 * - 线框容器：灰底标题栏「OpenAPI 规范」+ 校验结果（✓/✗），右上角操作：示例 / 通过 URL 导入 / 复制。
 * - Monaco JSON 编辑器 + validateOpenApiSpec 实时校验；通过后标题旁显示「格式正确」。
 * - 前端不做语义提取，原样存 openApiSpec，由后端发布时解析端点元数据。
 */
import { useState } from "react";
import Editor from "@monaco-editor/react";
import { Input, Modal, Space, Tooltip, Typography, message } from "antd";
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  CopyOutlined,
  ImportOutlined,
} from "@ant-design/icons";
import { validateOpenApiSpec } from "../constants";
import type { ToolFormProps } from "./types";

const { Text } = Typography;

const OPENAPI_SAMPLE = `{
  "openapi": "3.0.1",
  "info": {
    "title": "示例 API",
    "description": "这是一个示例 API",
    "version": "v1"
  },
  "servers": [
    { "url": "https://api.example.com" }
  ],
  "paths": {
    "/users": {
      "get": {
        "summary": "查询用户列表",
        "description": "返回用户分页列表"
      }
    }
  }
}`;

export default function FcOpenApiForm({ draft, patch }: ToolFormProps) {
  const validation = draft.openApiSpec.trim()
    ? validateOpenApiSpec(draft.openApiSpec)
    : null;

  const [urlOpen, setUrlOpen] = useState(false);
  const [url, setUrl] = useState("");
  const [importing, setImporting] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(draft.openApiSpec ?? "");
      message.success("已复制");
    } catch {
      message.error("复制失败");
    }
  };

  const handleImportUrl = async () => {
    const u = url.trim();
    if (!u) {
      message.warning("请输入文档 URL");
      return;
    }
    try {
      setImporting(true);
      const resp = await fetch(u);
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const text = await resp.text();
      patch({ openApiSpec: text });
      message.success("已导入");
      setUrlOpen(false);
      setUrl("");
    } catch (e) {
      message.error(`导入失败：${(e as Error).message}`);
    } finally {
      setImporting(false);
    }
  };

  return (
    <div>
      <div
        style={{
          border: `1px solid ${validation && !validation.ok ? "#FCA5A5" : "#E2E8F0"}`,
          borderRadius: 8,
          overflow: "hidden",
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
            <Text strong>OpenAPI 规范</Text>
            {validation && (
              <span
                style={{
                  color: validation.ok ? "#16A34A" : "#DC2626",
                  fontSize: 13,
                }}
              >
                {validation.ok ? (
                  <>
                    <CheckCircleOutlined /> 格式正确
                    {validation.endpointCount != null
                      ? `（${validation.endpointCount} 个端点）`
                      : ""}
                  </>
                ) : (
                  <>
                    <CloseCircleOutlined /> {validation.error}
                  </>
                )}
              </span>
            )}
          </Space>
          <Space size={8}>
            <ActionBtn
              icon={<CodeOutlined />}
              label="示例"
              onClick={() => patch({ openApiSpec: OPENAPI_SAMPLE })}
            />
            <ActionBtn
              icon={<ImportOutlined />}
              label="通过URL导入"
              onClick={() => setUrlOpen(true)}
            />
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
          height="420px"
          defaultLanguage="json"
          value={draft.openApiSpec}
          onChange={(v) => patch({ openApiSpec: v ?? "" })}
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

      <Modal
        title="通过 URL 导入 OpenAPI 文档"
        open={urlOpen}
        onCancel={() => setUrlOpen(false)}
        onOk={handleImportUrl}
        okText="导入"
        cancelText="取消"
        confirmLoading={importing}
        destroyOnClose
      >
        <Input
          placeholder="https://example.com/openapi.json"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          onPressEnter={handleImportUrl}
        />
      </Modal>
    </div>
  );
}

/** 标题栏蓝色文字操作按钮（带图标）。 */
function ActionBtn({
  icon,
  label,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <span
      onClick={onClick}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
        cursor: "pointer",
        padding: "2px 10px",
        border: "1px solid #DBEAFE",
        borderRadius: 6,
        background: "#EFF6FF",
        color: "#2563EB",
        fontSize: 13,
      }}
    >
      {icon}
      {label}
    </span>
  );
}
