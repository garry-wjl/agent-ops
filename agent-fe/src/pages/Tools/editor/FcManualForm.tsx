/**
 * FunctionCall 手动录入表单（type=FUNCTION_CALL, creationMode=MANUAL）
 * — Body 可视化嵌套字段（含 map）；右上角一键试连。
 */
import { useEffect, useState } from "react";
import { Button, Input, Modal, Select, Space, Switch, Typography, message } from "antd";
import {
  DeleteOutlined,
  DownOutlined,
  LinkOutlined,
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
import { TOOL_LIMITS, syncPathParamsFromPath } from "../constants";
import type { ToolFormProps } from "./types";
import { RequestBodyForm } from "./BodyFieldEditor";
import {
  fieldsToSchema,
  schemaToFields,
  type BodyFieldNode,
} from "./bodySchema";
import FcTestConnectionModal, {
  type FcTestEndpointOption,
} from "./FcTestConnectionModal";

const { Text, Link } = Typography;

const HTTP_METHODS: HttpMethod[] = ["GET", "POST", "PUT", "DELETE", "PATCH"];
const PARAM_TYPES: ApiParamType[] = ["string", "number", "boolean", "integer"];
const BODY_METHODS = new Set<HttpMethod>(["POST", "PUT", "PATCH", "DELETE"]);

/** Query：名 / 类型 / 默认值 / 描述 / 必填 / 删除 */
const PARAM_COLS =
  "minmax(110px,1.1fr) 110px minmax(100px,1fr) minmax(120px,1.3fr) 56px 40px";
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

export default function FcManualForm({ draft, patch, disabled }: ToolFormProps) {
  const endpoints = draft.endpoints;
  const readOnly = !!disabled;
  const [testOpen, setTestOpen] = useState(false);
  const [testFocusKey, setTestFocusKey] = useState<string>();
  const [testFocusIdx, setTestFocusIdx] = useState(0);

  const setEndpoints = (next: ApiEndpoint[]) => {
    if (readOnly) return;
    patch({ endpoints: next });
  };

  const updateEndpoint = (idx: number, p: Partial<ApiEndpoint>) => {
    if (readOnly) return;
    setEndpoints(endpoints.map((e, i) => (i === idx ? { ...e, ...p } : e)));
  };

  const addEndpoint = () => {
    if (readOnly) return;
    if (endpoints.length >= TOOL_LIMITS.ENDPOINT_MAX) return;
    setEndpoints([...endpoints, emptyEndpoint()]);
  };

  const removeEndpoint = (idx: number) => {
    if (readOnly) return;
    setEndpoints(endpoints.filter((_, i) => i !== idx));
  };

  const testOptions: FcTestEndpointOption[] = endpoints.map((ep, i) => ({
    label: `端点${i + 1}: ${ep.method} ${ep.path}`,
    method: ep.method,
    path: ep.path,
    endpoint: ep,
  }));

  const openTestFor = (idx: number) => {
    if (readOnly) return;
    const ep = endpoints[idx];
    if (!ep) return;
    setTestFocusIdx(idx);
    setTestFocusKey(`${ep.method} ${ep.path}`);
    setTestOpen(true);
  };

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <div style={{ marginBottom: 8 }}>
          <Text strong>Base URL</Text>
        </div>
        <Input
          placeholder="https://api.example.com"
          value={draft.baseUrl}
          disabled={readOnly}
          onChange={(e) => patch({ baseUrl: e.target.value })}
        />
      </div>

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
        {!readOnly && (
          <Button
            icon={<PlusOutlined />}
            disabled={endpoints.length >= TOOL_LIMITS.ENDPOINT_MAX}
            onClick={addEndpoint}
          >
            添加端点
          </Button>
        )}
      </div>

      {endpoints.map((ep, idx) => (
        <EndpointCard
          key={idx}
          index={idx}
          endpoint={ep}
          readOnly={readOnly}
          onChange={(p) => updateEndpoint(idx, p)}
          onRemove={() => removeEndpoint(idx)}
          onTest={() => openTestFor(idx)}
        />
      ))}

      {!readOnly && (
        <FcTestConnectionModal
          open={testOpen}
          onClose={() => setTestOpen(false)}
          baseUrl={draft.baseUrl}
          options={testOptions}
          initialSelectedKey={testFocusKey}
          onFillResponseSchema={(schema, option) => {
            const matches = endpoints
              .map((e, i) => ({ e, i }))
              .filter(
                ({ e }) => e.method === option.method && e.path === option.path,
              );
            const idx =
              matches.find((m) => m.i === testFocusIdx)?.i ?? matches[0]?.i;
            if (idx == null) return;
            updateEndpoint(idx, { responseBodySchema: schema });
            message.success("已填充返回参数，请补充字段描述");
          }}
        />
      )}
    </div>
  );
}

function EndpointCard({
  index,
  endpoint,
  onChange,
  onRemove,
  onTest,
  readOnly = false,
}: {
  index: number;
  endpoint: ApiEndpoint;
  onChange: (p: Partial<ApiEndpoint>) => void;
  onRemove: () => void;
  onTest: () => void;
  readOnly?: boolean;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const [bodyFields, setBodyFields] = useState<BodyFieldNode[]>(() =>
    schemaToFields(endpoint.requestBodySchema),
  );
  const [responseFields, setResponseFields] = useState<BodyFieldNode[]>(() =>
    schemaToFields(endpoint.responseBodySchema),
  );
  const showBody = BODY_METHODS.has(endpoint.method);

  const requestSchemaKey = JSON.stringify(endpoint.requestBodySchema ?? null);
  const responseSchemaKey = JSON.stringify(endpoint.responseBodySchema ?? null);
  useEffect(() => {
    setBodyFields(schemaToFields(endpoint.requestBodySchema));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestSchemaKey]);
  useEffect(() => {
    setResponseFields(schemaToFields(endpoint.responseBodySchema));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [responseSchemaKey]);

  const onBodyFieldsChange = (fields: BodyFieldNode[]) => {
    if (readOnly) return;
    setBodyFields(fields);
    onChange({
      requestBodySchema: fieldsToSchema(fields),
    });
  };

  const onResponseFieldsChange = (fields: BodyFieldNode[]) => {
    if (readOnly) return;
    setResponseFields(fields);
    onChange({
      responseBodySchema: fieldsToSchema(fields),
    });
  };

  const onPathChange = (path: string) => {
    if (readOnly) return;
    onChange({
      path,
      pathParams: syncPathParamsFromPath(path, endpoint.pathParams ?? []),
    });
  };

  // 编辑回填：保证 path 占位与 pathParams 同步（不再展示 Path 参数表）
  useEffect(() => {
    if (readOnly) return;
    const synced = syncPathParamsFromPath(
      endpoint.path,
      endpoint.pathParams ?? [],
    );
    const prev = endpoint.pathParams ?? [];
    const same =
      prev.length === synced.length &&
      prev.every(
        (p, i) => p.name === synced[i].name && p.required === synced[i].required,
      );
    if (!same) {
      onChange({ pathParams: synced });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅在 path / 外部 pathParams 变化时对齐
  }, [endpoint.path]);

  return (
    <div
      style={{
        border: "1px solid #E2E8F0",
        borderRadius: 8,
        marginBottom: 16,
        overflow: "hidden",
      }}
    >
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
          {readOnly && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {endpoint.method} {endpoint.path}
            </Text>
          )}
        </Space>
        {!readOnly && (
          <Space size={4}>
            <Link
              onClick={(e) => {
                e.stopPropagation();
                onTest();
              }}
              style={{ display: "inline-flex", alignItems: "center", gap: 4 }}
            >
              <LinkOutlined /> 一键测试
            </Link>
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              aria-label="删除端点"
              title="删除端点"
              onClick={(e) => {
                e.stopPropagation();
                Modal.confirm({
                  title: "删除端点",
                  content: `确认删除「端点${index + 1}」？删除后不可恢复。`,
                  okText: "删除",
                  okButtonProps: { danger: true },
                  cancelText: "取消",
                  onOk: () => onRemove(),
                });
              }}
            />
          </Space>
        )}
      </div>

      {!collapsed && (
        <div style={{ padding: 16 }}>
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
                disabled={readOnly}
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
                disabled={readOnly}
                onChange={(e) => onPathChange(e.target.value)}
              />
            </div>
          </div>

          <div style={{ marginBottom: 20 }}>
            <div style={{ marginBottom: 6 }}>
              <Text strong>端点描述</Text>
            </div>
            <Input.TextArea
              placeholder="端点的详细描述"
              maxLength={TOOL_LIMITS.ENDPOINT_DESC_MAX}
              showCount={!readOnly}
              rows={2}
              value={endpoint.description}
              disabled={readOnly}
              onChange={(e) => onChange({ description: e.target.value })}
            />
          </div>

          <HeaderTable
            rows={endpoint.headers ?? []}
            onChange={(rows) => onChange({ headers: rows })}
            readOnly={readOnly}
          />
          <ParamTable
            label="Query 参数"
            rows={endpoint.queryParams ?? []}
            onChange={(rows) => onChange({ queryParams: rows })}
            showRequired
            readOnly={readOnly}
          />
          {showBody && (
            <RequestBodyForm
              fields={bodyFields}
              required={endpoint.requestBodyRequired}
              onFieldsChange={onBodyFieldsChange}
              onRequiredChange={(requestBodyRequired) =>
                onChange({ requestBodyRequired })
              }
              readOnly={readOnly}
            />
          )}
          <RequestBodyForm
            title="返回参数"
            hint="定义响应 JSON 结构（可与 Body 同样嵌套）；试连成功后可一键填充"
            showOverallRequired={false}
            fields={responseFields}
            onFieldsChange={onResponseFieldsChange}
            readOnly={readOnly}
          />
        </div>
      )}
    </div>
  );
}

const colLabelStyle: React.CSSProperties = { fontSize: 13 };

function ParamTable({
  label,
  rows,
  onChange,
  showRequired = false,
  readOnly = false,
}: {
  label: string;
  rows: ApiParam[];
  onChange: (rows: ApiParam[]) => void;
  showRequired?: boolean;
  readOnly?: boolean;
}) {
  const update = (idx: number, p: Partial<ApiParam>) => {
    if (readOnly) return;
    onChange(rows.map((r, i) => (i === idx ? { ...r, ...p } : r)));
  };
  const add = () => {
    if (readOnly) return;
    onChange([
      ...rows,
      {
        name: "",
        type: "string",
        defaultValue: "",
        description: "",
        required: false,
      },
    ]);
  };
  const remove = (idx: number) => {
    if (readOnly) return;
    onChange(rows.filter((_, i) => i !== idx));
  };

  return (
    <div style={{ marginBottom: 20 }}>
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
        {showRequired ? (
          <Text strong style={colLabelStyle}>
            必填
          </Text>
        ) : (
          <span />
        )}
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
              gridTemplateColumns: PARAM_COLS,
              gap: 12,
              alignItems: "center",
              marginBottom: 8,
            }}
          >
            <Input
              placeholder="参数名"
              value={r.name}
              disabled={readOnly}
              onChange={(e) => update(idx, { name: e.target.value })}
            />
            <Select<ApiParamType>
              value={r.type}
              disabled={readOnly}
              onChange={(t) => update(idx, { type: t })}
              options={PARAM_TYPES.map((t) => ({ value: t, label: t }))}
            />
            <Input
              placeholder="可选"
              value={r.defaultValue}
              disabled={readOnly}
              onChange={(e) => update(idx, { defaultValue: e.target.value })}
            />
            <Input
              placeholder="描述"
              value={r.description}
              disabled={readOnly}
              onChange={(e) => update(idx, { description: e.target.value })}
            />
            {showRequired ? (
              <div style={{ display: "flex", justifyContent: "flex-start" }}>
                <Switch
                  size="small"
                  checked={!!r.required}
                  disabled={readOnly}
                  onChange={(required) => update(idx, { required })}
                />
              </div>
            ) : (
              <span />
            )}
            {readOnly ? (
              <span />
            ) : (
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                onClick={() => remove(idx)}
              />
            )}
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
          {!readOnly && (
            <Button
              type="link"
              icon={<PlusOutlined />}
              onClick={add}
              style={{ padding: 0 }}
            >
              添加参数
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function HeaderTable({
  rows,
  onChange,
  readOnly = false,
}: {
  rows: ApiHeader[];
  onChange: (rows: ApiHeader[]) => void;
  readOnly?: boolean;
}) {
  const update = (idx: number, p: Partial<ApiHeader>) => {
    if (readOnly) return;
    onChange(rows.map((r, i) => (i === idx ? { ...r, ...p } : r)));
  };
  const add = () => {
    if (readOnly) return;
    onChange([...rows, { name: "", defaultValue: "", description: "" }]);
  };
  const remove = (idx: number) => {
    if (readOnly) return;
    onChange(rows.filter((_, i) => i !== idx));
  };

  return (
    <div style={{ marginBottom: 20 }}>
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
              disabled={readOnly}
              onChange={(e) => update(idx, { name: e.target.value })}
            />
            <Input
              placeholder="application/json"
              value={r.defaultValue}
              disabled={readOnly}
              onChange={(e) => update(idx, { defaultValue: e.target.value })}
            />
            <Input
              placeholder="描述"
              value={r.description}
              disabled={readOnly}
              onChange={(e) => update(idx, { description: e.target.value })}
            />
            {readOnly ? (
              <span />
            ) : (
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                onClick={() => remove(idx)}
              />
            )}
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
          {!readOnly && (
            <Button
              type="link"
              icon={<PlusOutlined />}
              onClick={add}
              style={{ padding: 0 }}
            >
              添加参数
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
