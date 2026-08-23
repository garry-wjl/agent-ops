/**
 * FunctionCall 一键试连弹窗：选择端点 → 用默认值发请求 → 展示连通结果。
 * HTTP 200 且响应可解析为 JSON 时，提供「一键填充」写入返回参数。
 */
import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Modal, Select, Space, Typography, message } from "antd";
import { LinkOutlined } from "@ant-design/icons";
import { toolApi } from "@/services/tool/api";
import type { ApiEndpoint, FcTestConnectionResult } from "@/types";
import {
  buildDefaultBody,
  inferSchemaFromJson,
  schemaToFields,
} from "./bodySchema";

const { Text, Paragraph, Link } = Typography;

export type FcTestEndpointOption = {
  label: string;
  method: string;
  path: string;
  /** MANUAL 时带完整端点；OPENAPI 仅 method/path，由后端解析 schema */
  endpoint?: ApiEndpoint;
};

export default function FcTestConnectionModal({
  open,
  onClose,
  baseUrl,
  openApiSpec,
  options,
  initialSelectedKey,
  onFillResponseSchema,
}: {
  open: boolean;
  onClose: () => void;
  baseUrl?: string;
  openApiSpec?: string;
  options: FcTestEndpointOption[];
  /** 打开时预选端点（method + path） */
  initialSelectedKey?: string;
  /** 一键填充返回参数（JSON Schema）；option 为当前选中的试连端点 */
  onFillResponseSchema?: (
    schema: Record<string, unknown>,
    option: FcTestEndpointOption,
  ) => void;
}) {
  const [selected, setSelected] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<FcTestConnectionResult | null>(null);

  const optionKey = (o: FcTestEndpointOption) => `${o.method} ${o.path}`;

  useEffect(() => {
    if (!open) return;
    setResult(null);
    const preferred =
      initialSelectedKey &&
      options.some((o) => optionKey(o) === initialSelectedKey)
        ? initialSelectedKey
        : options[0]
          ? optionKey(options[0])
          : undefined;
    setSelected(preferred);
  }, [open, options, initialSelectedKey]);

  const current = useMemo(
    () => options.find((o) => optionKey(o) === selected),
    [options, selected],
  );

  const fillableSchema = useMemo(() => {
    if (!result || result.httpStatus !== 200) return undefined;
    const raw = result.responseSample || result.responsePreview;
    if (!raw) return undefined;
    try {
      const text = (result.responseSample
        ? raw
        : raw.replace(/\.\.\.$/, "")
      ).trim();
      const parsed = JSON.parse(text) as unknown;
      return inferSchemaFromJson(parsed);
    } catch {
      return undefined;
    }
  }, [result]);

  const runTest = async () => {
    if (!current) {
      message.warning("请选择要测试的端点");
      return;
    }
    setLoading(true);
    setResult(null);
    try {
      const body =
        current.endpoint?.requestBodySchema != null
          ? buildDefaultBody(schemaToFields(current.endpoint.requestBodySchema))
          : undefined;
      const res = await toolApi.testFunctionCall({
        baseUrl,
        openApiSpec,
        endpoint: current.endpoint ?? {
          method: current.method as ApiEndpoint["method"],
          path: current.path,
          description: current.label,
        },
        body: body && Object.keys(body).length ? body : undefined,
      });
      setResult(res);
      if (res.success) message.success(res.message || "连通成功");
      else message.error(res.message || "连通失败");
    } catch (e) {
      message.error((e as Error).message || "试连请求失败");
    } finally {
      setLoading(false);
    }
  };

  const handleFill = () => {
    if (!fillableSchema || !onFillResponseSchema || !current) return;
    onFillResponseSchema(fillableSchema, current);
  };

  return (
    <Modal
      title="一键测试（连通性）"
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          关闭
        </Button>,
        <Button key="run" type="primary" loading={loading} onClick={runTest}>
          开始测试
        </Button>,
      ]}
      width={640}
      destroyOnClose
    >
      <Space direction="vertical" style={{ width: "100%" }} size={12}>
        <div>
          <Text type="secondary">
            将使用 Path/Query/Header/Body 的默认值发起真实 HTTP 请求。
          </Text>
        </div>
        <div>
          <div style={{ marginBottom: 6 }}>
            <Text strong>测试端点</Text>
          </div>
          <Select
            style={{ width: "100%" }}
            value={selected}
            options={options.map((o) => ({
              value: optionKey(o),
              label: o.label,
            }))}
            onChange={setSelected}
            placeholder="选择端点"
          />
        </div>
        {baseUrl && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Base URL: {baseUrl}
          </Text>
        )}
        {result && (
          <Alert
            type={result.success ? "success" : "error"}
            showIcon
            message={
              <Space>
                <span>{result.message}</span>
                {fillableSchema && onFillResponseSchema && (
                  <Link
                    onClick={handleFill}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      gap: 4,
                    }}
                  >
                    <LinkOutlined /> 一键填充返回参数
                  </Link>
                )}
              </Space>
            }
            description={
              <div>
                <div>
                  {result.requestMethod} {result.requestUrl}
                </div>
                <div>
                  HTTP {result.httpStatus ?? "-"} · {result.latencyMs ?? "-"} ms
                </div>
                {result.responsePreview && (
                  <Paragraph
                    style={{
                      marginTop: 8,
                      marginBottom: 0,
                      maxHeight: 200,
                      overflow: "auto",
                      fontSize: 12,
                      whiteSpace: "pre-wrap",
                    }}
                  >
                    {result.responsePreview}
                  </Paragraph>
                )}
              </div>
            }
          />
        )}
      </Space>
    </Modal>
  );
}

/** 从 OpenAPI JSON 粗解析端点列表（供试连下拉）。 */
export function parseOpenApiEndpointOptions(spec: string): FcTestEndpointOption[] {
  try {
    const root = JSON.parse(spec) as {
      paths?: Record<
        string,
        Record<string, { summary?: string; description?: string }>
      >;
    };
    const paths = root.paths ?? {};
    const methods = new Set(["get", "post", "put", "delete", "patch"]);
    const out: FcTestEndpointOption[] = [];
    for (const [path, item] of Object.entries(paths)) {
      if (!item || typeof item !== "object") continue;
      for (const [method, op] of Object.entries(item)) {
        if (!methods.has(method.toLowerCase())) continue;
        const summary = op?.summary || op?.description || path;
        out.push({
          label: `${method.toUpperCase()} ${path} — ${summary}`,
          method: method.toUpperCase(),
          path,
        });
      }
    }
    return out;
  } catch {
    return [];
  }
}
