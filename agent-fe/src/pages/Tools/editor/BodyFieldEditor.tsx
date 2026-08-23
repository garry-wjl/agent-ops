/**
 * Body 参数可视化字段树：行不缩进，层级用左侧树形连线表达。
 */
import { Button, Input, Select, Space, Switch, Typography } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import type { BodyFieldKind, BodyFieldNode } from "./bodySchema";
import { emptyField } from "./bodySchema";

const { Text } = Typography;

const KIND_OPTIONS: { value: BodyFieldKind; label: string }[] = [
  { value: "string", label: "string" },
  { value: "number", label: "number" },
  { value: "integer", label: "integer" },
  { value: "boolean", label: "boolean" },
  { value: "object", label: "object" },
  { value: "array", label: "array" },
  { value: "map", label: "map" },
];

const ROW_COLS =
  "minmax(100px,1.1fr) 110px minmax(90px,1fr) minmax(100px,1.2fr) 56px 40px";

const GUIDE_W = 16;
const LINE = "#CBD5E1";

/**
 * 左侧树形连线：祖先层画竖线（非末节点），当前层画 └ / ├ + 横线。
 * @param continueDown 当前节点下方还有子内容时，竖线向下贯通（即使本层是末兄弟）
 */
function TreeGuides({
  depth,
  isLast,
  ancestorsIsLast,
  continueDown = false,
}: {
  depth: number;
  isLast: boolean;
  ancestorsIsLast: boolean[];
  continueDown?: boolean;
}) {
  if (depth <= 0) return null;
  return (
    <div
      style={{
        display: "flex",
        flexShrink: 0,
        alignSelf: "stretch",
        width: depth * GUIDE_W,
      }}
      aria-hidden
    >
      {Array.from({ length: depth }, (_, level) => {
        const isCurrent = level === depth - 1;
        const ancestorLast = ancestorsIsLast[level] === true;
        const stopAtMid = isLast && !continueDown;
        return (
          <div
            key={level}
            style={{
              position: "relative",
              width: GUIDE_W,
              flexShrink: 0,
              alignSelf: "stretch",
              minHeight: 32,
            }}
          >
            {!isCurrent && !ancestorLast && (
              <span
                style={{
                  position: "absolute",
                  left: 7,
                  top: 0,
                  bottom: 0,
                  borderLeft: `1px solid ${LINE}`,
                }}
              />
            )}
            {isCurrent && (
              <>
                <span
                  style={{
                    position: "absolute",
                    left: 7,
                    top: 0,
                    bottom: stopAtMid ? "50%" : 0,
                    borderLeft: `1px solid ${LINE}`,
                  }}
                />
                <span
                  style={{
                    position: "absolute",
                    left: 7,
                    top: "50%",
                    width: GUIDE_W - 7,
                    borderTop: `1px solid ${LINE}`,
                  }}
                />
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

function FieldRowShell({
  depth,
  isLast,
  ancestorsIsLast,
  continueDown,
  children,
}: {
  depth: number;
  isLast: boolean;
  ancestorsIsLast: boolean[];
  continueDown?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div style={{ display: "flex", alignItems: "stretch" }}>
      <TreeGuides
        depth={depth}
        isLast={isLast}
        ancestorsIsLast={ancestorsIsLast}
        continueDown={continueDown}
      />
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  );
}

export default function BodyFieldEditor({
  fields,
  onChange,
  depth = 0,
  ancestorsIsLast = [],
  readOnly = false,
}: {
  fields: BodyFieldNode[];
  onChange: (fields: BodyFieldNode[]) => void;
  depth?: number;
  ancestorsIsLast?: boolean[];
  readOnly?: boolean;
}) {
  const update = (idx: number, patch: Partial<BodyFieldNode>) => {
    if (readOnly) return;
    onChange(
      fields.map((f, i) => {
        if (i !== idx) return f;
        const next = { ...f, ...patch };
        if (patch.kind && patch.kind !== f.kind) {
          if (patch.kind === "object") {
            next.children = f.children ?? [];
            next.items = undefined;
            next.mapValue = undefined;
          } else if (patch.kind === "array") {
            next.items = f.items ?? emptyField("string");
            next.children = undefined;
            next.mapValue = undefined;
          } else if (patch.kind === "map") {
            next.mapValue = f.mapValue ?? emptyField("string");
            next.children = undefined;
            next.items = undefined;
          } else {
            next.children = undefined;
            next.items = undefined;
            next.mapValue = undefined;
          }
        }
        return next;
      }),
    );
  };

  const remove = (idx: number) => {
    if (readOnly) return;
    onChange(fields.filter((_, i) => i !== idx));
  };
  const add = () => {
    if (readOnly) return;
    onChange([...fields, emptyField("string")]);
  };

  return (
    <div>
      {depth === 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: ROW_COLS,
            gap: 8,
            padding: "0 4px 8px",
          }}
        >
          <Text strong>字段名</Text>
          <Text strong style={{ fontSize: 13 }}>
            类型
          </Text>
          <Text strong style={{ fontSize: 13 }}>
            默认值
          </Text>
          <Text strong style={{ fontSize: 13 }}>
            描述
          </Text>
          <Text strong style={{ fontSize: 13 }}>
            必填
          </Text>
          <span />
        </div>
      )}

      <div
        style={{
          border: depth === 0 ? "1px solid #E2E8F0" : "none",
          borderRadius: 8,
          padding: depth === 0 ? 12 : 0,
        }}
      >
        {fields.map((f, idx) => {
          const isLast = idx === fields.length - 1;
          const childAncestors = [...ancestorsIsLast, isLast];
          const hasNest =
            f.kind === "object" || f.kind === "array" || f.kind === "map";

          return (
            <div key={f.key} style={{ marginBottom: 8 }}>
              <FieldRowShell
                depth={depth}
                isLast={isLast}
                ancestorsIsLast={ancestorsIsLast}
                continueDown={hasNest}
              >
                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: ROW_COLS,
                    gap: 8,
                    alignItems: "center",
                  }}
                >
                  <Input
                    placeholder="字段名"
                    value={f.name}
                    onChange={(e) => update(idx, { name: e.target.value })}
                    disabled={
                      readOnly ||
                      (depth > 0 && f.name === "item" && f.kind !== "object")
                    }
                  />
                  <Select<BodyFieldKind>
                    value={f.kind}
                    options={KIND_OPTIONS}
                    onChange={(kind) => update(idx, { kind })}
                    disabled={readOnly}
                  />
                  {f.kind === "object" || f.kind === "array" ? (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      —
                    </Text>
                  ) : (
                    <Input
                      placeholder={f.kind === "map" ? '如 {"k":"v"}' : "可选"}
                      value={f.defaultValue}
                      onChange={(e) =>
                        update(idx, { defaultValue: e.target.value })
                      }
                      disabled={readOnly}
                    />
                  )}
                  <Input
                    placeholder="描述"
                    value={f.description}
                    onChange={(e) =>
                      update(idx, { description: e.target.value })
                    }
                    disabled={readOnly}
                  />
                  <div style={{ display: "flex", justifyContent: "flex-start" }}>
                    <Switch
                      size="small"
                      checked={!!f.required}
                      onChange={(required) => update(idx, { required })}
                      disabled={readOnly}
                    />
                  </div>
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
              </FieldRowShell>

              {f.kind === "object" && (
                <div style={{ marginTop: 4 }}>
                  <FieldRowShell
                    depth={depth + 1}
                    isLast={(f.children ?? []).length === 0}
                    ancestorsIsLast={childAncestors}
                    continueDown={(f.children ?? []).length > 0}
                  >
                    <Text type="secondary" style={{ fontSize: 12, lineHeight: "32px" }}>
                      对象属性
                    </Text>
                  </FieldRowShell>
                  <BodyFieldEditor
                    depth={depth + 1}
                    ancestorsIsLast={childAncestors}
                    fields={f.children ?? []}
                    onChange={(children) => update(idx, { children })}
                    readOnly={readOnly}
                  />
                </div>
              )}
              {f.kind === "array" && f.items && (
                <div style={{ marginTop: 4 }}>
                  <FieldRowShell
                    depth={depth + 1}
                    isLast={false}
                    ancestorsIsLast={childAncestors}
                    continueDown
                  >
                    <Text type="secondary" style={{ fontSize: 12, lineHeight: "32px" }}>
                      数组元素
                    </Text>
                  </FieldRowShell>
                  <BodyFieldEditor
                    depth={depth + 1}
                    ancestorsIsLast={childAncestors}
                    fields={[{ ...f.items, name: f.items.name || "item" }]}
                    onChange={(items) =>
                      update(idx, {
                        items: items[0] ?? emptyField("string"),
                      })
                    }
                    readOnly={readOnly}
                  />
                </div>
              )}
              {f.kind === "map" && f.mapValue && (
                <div style={{ marginTop: 4 }}>
                  <FieldRowShell
                    depth={depth + 1}
                    isLast={false}
                    ancestorsIsLast={childAncestors}
                    continueDown
                  >
                    <Text type="secondary" style={{ fontSize: 12, lineHeight: "32px" }}>
                      Map 值类型（key 固定为 string）
                    </Text>
                  </FieldRowShell>
                  <BodyFieldEditor
                    depth={depth + 1}
                    ancestorsIsLast={childAncestors}
                    fields={[
                      {
                        ...f.mapValue,
                        name: f.mapValue.name || "value",
                      },
                    ]}
                    onChange={(vals) =>
                      update(idx, {
                        mapValue: vals[0] ?? emptyField("string"),
                      })
                    }
                    readOnly={readOnly}
                  />
                </div>
              )}
            </div>
          );
        })}

        <FieldRowShell
          depth={depth}
          isLast
          ancestorsIsLast={ancestorsIsLast}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <Text type="secondary">共{fields.length}个</Text>
            {!readOnly && (
              <Button
                type="link"
                icon={<PlusOutlined />}
                onClick={add}
                style={{ padding: 0 }}
              >
                添加字段
              </Button>
            )}
          </div>
        </FieldRowShell>
      </div>
    </div>
  );
}

/** 顶层 Body / 返回 参数区：必填开关（仅 Body）+ 字段树 */
export function RequestBodyForm({
  fields,
  required,
  onFieldsChange,
  onRequiredChange,
  title = "Body 参数",
  hint = "可视化字段（支持 object / array / map 嵌套）；LLM 入参字段名为 body",
  showOverallRequired = true,
  readOnly = false,
}: {
  fields: BodyFieldNode[];
  required?: boolean;
  onFieldsChange: (fields: BodyFieldNode[]) => void;
  onRequiredChange?: (required: boolean) => void;
  title?: string;
  hint?: string;
  showOverallRequired?: boolean;
  readOnly?: boolean;
}) {
  return (
    <div style={{ marginBottom: 4, marginTop: 4 }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 8,
        }}
      >
        <div>
          <Text strong>{title}</Text>
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            {hint}
          </Text>
        </div>
        {showOverallRequired && onRequiredChange && (
          <Space size={8}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              整体必填
            </Text>
            <Switch
              size="small"
              checked={!!required}
              onChange={onRequiredChange}
              disabled={readOnly}
            />
          </Space>
        )}
      </div>
      <BodyFieldEditor
        fields={fields}
        onChange={onFieldsChange}
        readOnly={readOnly}
      />
    </div>
  );
}
