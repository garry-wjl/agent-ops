/**
 * FunctionCall 手动录入表单（type=FUNCTION_CALL, creationMode=MANUAL）
 * — PRD §8.6 / §7.1.5 / §7.6。
 *
 * - Base URL（必填，含 scheme）。
 * - 端点列表（增删，上限 50）；每个端点为可折叠卡片：
 *   方法 + Path 同行（标签在上）/ 端点描述 / Query 参数 / Path 参数 / Headers。
 * - 参数区：列标题行 + 线框容器（参数行 + 底栏「共 N 个 / 添加参数」）。
 * - Path 参数与 path 占位符一一对应，validatePathParams 行级红字提示。
 *
 * 受控：endpoints / baseUrl 由父草稿持有，经 patch 上抛整数组。
 */
import { useState } from "react";
import { Button, Input, Select, Space, Typography } from "antd";
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from "@ant-design/icons";
import type {
  ApiHeader,
  ApiParam,
  ApiParamType,
  ApiEndpoint,
  HttpMethod,
} from "@/types";
import { TOOL_LIMITS, validatePathParams } from "../constants";
import type { ToolFormProps } from "./types";

const { Text } = Typography;

const HTTP_METHODS: HttpMethod[] = ["GET", "POST", "PUT", "DELETE", "PATCH"];
const PARAM_TYPES: ApiParamType[] = ["string", "number", "boolean", "integer"];

/** Query / Path 参数表列宽模板：名 / 类型 / 默认值 / 描述 / 删除。 */
const PARAM_COLS =
  "minmax(120px,1.2fr) 120px minmax(120px,1.2fr) minmax(140px,1.5fr) 40px";
/** Headers 表列宽模板：名 / 默认值 / 描述 / 删除。 */
const HEADER_COLS =
  "minmax(140px,1.4fr) minmax(140px,1.4fr) minmax(160px,1.6fr) 40px";

function emptyEndpoint(): ApiEndpoint {
  return {
    method: "GET",
    path: "/",
    description: "",
    queryParams: [],
    pathParams: [],
    headers: [],
  };
}

export default function FcManualForm({ draft, patch }: ToolFormProps) {
  const endpoints = draft.endpoints;

  const setEndpoints = (next: ApiEndpoint[]) => patch({ endpoints: next });

  const updateEndpoint = (idx: number, p: Partial<ApiEndpoint>) =>
    setEndpoints(endpoints.map((e, i) => (i === idx ? { ...e, ...p } : e)));

  const addEndpoint = () => {
    if (endpoints.length >= TOOL_LIMITS.ENDPOINT_MAX) return;
    setEndpoints([...endpoints, emptyEndpoint()]);
  };

  const removeEndpoint = (idx: number) =>
    setEndpoints(endpoints.filter((_, i) => i !== idx));

  return (
    <div>
      {/* Base URL */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ marginBottom: 8 }}>
          <Text strong>Base URL</Text>
        </div>
        <Input
          placeholder="https://api.example.com"
          value={draft.baseUrl}
          onChange={(e) => patch({ baseUrl: e.target.value })}
        />
      </div>

      {/* 端点区标题 + 添加端点 */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 12,
        }}
      >
        <Text strong style={{ fontSize: 16 }}>
          API 端点
        </Text>
        <Button
          icon={<PlusOutlined />}
          disabled={endpoints.length >= TOOL_LIMITS.ENDPOINT_MAX}
          onClick={addEndpoint}
        >
          添加端点
        </Button>
      </div>

      {endpoints.map((ep, idx) => (
        <EndpointCard
          key={idx}
          index={idx}
          endpoint={ep}
          onChange={(p) => updateEndpoint(idx, p)}
          onRemove={() => removeEndpoint(idx)}
        />
      ))}
    </div>
  );
}

function EndpointCard({
  index,
  endpoint,
  onChange,
  onRemove,
}: {
  index: number;
  endpoint: ApiEndpoint;
  onChange: (p: Partial<ApiEndpoint>) => void;
  onRemove: () => void;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const pathCheck = validatePathParams(
    endpoint.path,
    endpoint.pathParams ?? [],
  );

  return (
    <div
      style={{
        border: "1px solid #E2E8F0",
        borderRadius: 8,
        marginBottom: 16,
        overflow: "hidden",
      }}
    >
      {/* 卡片头：折叠 + 端点N + 删除端点 */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "12px 16px",
          borderBottom: collapsed ? "none" : "1px solid #E2E8F0",
        }}
      >
        <Space
          style={{ cursor: "pointer" }}
          onClick={() => setCollapsed((c) => !c)}
        >
          {collapsed ? <DownOutlined /> : <UpOutlined />}
          <Text strong>端点{index + 1}</Text>
        </Space>
        <Button
          type="text"
          icon={<DeleteOutlined />}
          style={{ color: "#90A1B9" }}
          onClick={onRemove}
        >
          删除端点
        </Button>
      </div>

      {!collapsed && (
        <div style={{ padding: 16 }}>
          {/* 方法 + Path */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "240px 1fr",
              gap: 16,
              marginBottom: 16,
            }}
          >
            <div>
              <div style={{ marginBottom: 6 }}>
                <Text strong>方法</Text>
              </div>
              <Select<HttpMethod>
                style={{ width: "100%" }}
                value={endpoint.method}
                onChange={(m) => onChange({ method: m })}
                options={HTTP_METHODS.map((m) => ({ value: m, label: m }))}
              />
            </div>
            <div>
              <div style={{ marginBottom: 6 }}>
                <Text strong>Path</Text>
              </div>
              <Input
                placeholder="/users/{id}"
                value={endpoint.path}
                onChange={(e) => onChange({ path: e.target.value })}
              />
            </div>
          </div>
          {!pathCheck.ok && (
            <div style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>
              ✗ {pathCheck.error}
            </div>
          )}

          {/* 端点描述 */}
          <div style={{ marginBottom: 20 }}>
            <div style={{ marginBottom: 6 }}>
              <Text strong>端点描述</Text>
            </div>
            <Input.TextArea
              placeholder="端点的详细描述"
              maxLength={TOOL_LIMITS.ENDPOINT_DESC_MAX}
              showCount
              rows={2}
              value={endpoint.description}
              onChange={(e) => onChange({ description: e.target.value })}
            />
          </div>

          <ParamTable
            label="Query 参数"
            rows={endpoint.queryParams ?? []}
            onChange={(rows) => onChange({ queryParams: rows })}
          />
          <ParamTable
            label="Path 参数"
            rows={endpoint.pathParams ?? []}
            onChange={(rows) => onChange({ pathParams: rows })}
          />
          <HeaderTable
            rows={endpoint.headers ?? []}
            onChange={(rows) => onChange({ headers: rows })}
          />
        </div>
      )}
    </div>
  );
}

const colLabelStyle: React.CSSProperties = { fontSize: 13 };

/** Query / Path 参数表（名 / 类型 / 默认值 / 描述）。 */
function ParamTable({
  label,
  rows,
  onChange,
}: {
  label: string;
  rows: ApiParam[];
  onChange: (rows: ApiParam[]) => void;
}) {
  const update = (idx: number, p: Partial<ApiParam>) =>
    onChange(rows.map((r, i) => (i === idx ? { ...r, ...p } : r)));
  const add = () =>
    onChange([
      ...rows,
      { name: "", type: "string", defaultValue: "", description: "" },
    ]);
  const remove = (idx: number) => onChange(rows.filter((_, i) => i !== idx));

  return (
    <div style={{ marginBottom: 20 }}>
      {/* 列标题行 */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: PARAM_COLS,
          gap: 12,
          padding: "0 4px 8px",
        }}
      >
        <Text strong>{label}</Text>
        <Text strong style={colLabelStyle}>
          类型
        </Text>
        <Text strong style={colLabelStyle}>
          默认值
        </Text>
        <Text strong style={colLabelStyle}>
          描述
        </Text>
        <span />
      </div>

      {/* 线框容器：参数行 + 底栏 */}
      <div
        style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12 }}
      >
        {rows.map((r, idx) => (
          <div
            key={idx}
            style={{
              display: "grid",
              gridTemplateColumns: PARAM_COLS,
              gap: 12,
              alignItems: "center",
              marginBottom: 8,
            }}
          >
            <Input
              placeholder="参数名"
              value={r.name}
              onChange={(e) => update(idx, { name: e.target.value })}
            />
            <Select<ApiParamType>
              value={r.type}
              onChange={(t) => update(idx, { type: t })}
              options={PARAM_TYPES.map((t) => ({ value: t, label: t }))}
            />
            <Input
              placeholder="可选"
              value={r.defaultValue}
              onChange={(e) => update(idx, { defaultValue: e.target.value })}
            />
            <Input
              placeholder="描述"
              value={r.description}
              onChange={(e) => update(idx, { description: e.target.value })}
            />
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              onClick={() => remove(idx)}
            />
          </div>
        ))}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <Text type="secondary">共{rows.length}个</Text>
          <Button
            type="link"
            icon={<PlusOutlined />}
            onClick={add}
            style={{ padding: 0 }}
          >
            添加参数
          </Button>
        </div>
      </div>
    </div>
  );
}

/** Headers 表（名 / 默认值 / 描述，不支持变量占位）。 */
function HeaderTable({
  rows,
  onChange,
}: {
  rows: ApiHeader[];
  onChange: (rows: ApiHeader[]) => void;
}) {
  const update = (idx: number, p: Partial<ApiHeader>) =>
    onChange(rows.map((r, i) => (i === idx ? { ...r, ...p } : r)));
  const add = () =>
    onChange([...rows, { name: "", defaultValue: "", description: "" }]);
  const remove = (idx: number) => onChange(rows.filter((_, i) => i !== idx));

  return (
    <div>
      {/* 列标题行 */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: HEADER_COLS,
          gap: 12,
          padding: "0 4px 8px",
        }}
      >
        <Text strong>Headers</Text>
        <Text strong style={colLabelStyle}>
          默认值
        </Text>
        <Text strong style={colLabelStyle}>
          描述
        </Text>
        <span />
      </div>

      <div
        style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12 }}
      >
        {rows.map((r, idx) => (
          <div
            key={idx}
            style={{
              display: "grid",
              gridTemplateColumns: HEADER_COLS,
              gap: 12,
              alignItems: "center",
              marginBottom: 8,
            }}
          >
            <Input
              placeholder="如 Accept"
              value={r.name}
              onChange={(e) => update(idx, { name: e.target.value })}
            />
            <Input
              placeholder="application/json"
              value={r.defaultValue}
              onChange={(e) => update(idx, { defaultValue: e.target.value })}
            />
            <Input
              placeholder="描述"
              value={r.description}
              onChange={(e) => update(idx, { description: e.target.value })}
            />
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              onClick={() => remove(idx)}
            />
          </div>
        ))}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <Text type="secondary">共{rows.length}个</Text>
          <Button
            type="link"
            icon={<PlusOutlined />}
            onClick={add}
            style={{ padding: 0 }}
          >
            添加参数
          </Button>
        </div>
      </div>
    </div>
  );
}
