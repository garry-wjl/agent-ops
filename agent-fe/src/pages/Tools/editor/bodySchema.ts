/**
 * FunctionCall Request Body 可视化字段树 ↔ JSON Schema。
 * 支持 string/number/integer/boolean/object/array/map（map → additionalProperties）。
 */
export type BodyFieldKind =
  | "string"
  | "number"
  | "integer"
  | "boolean"
  | "object"
  | "array"
  | "map";

export interface BodyFieldNode {
  /** React 列表稳定 key */
  key: string;
  name: string;
  kind: BodyFieldKind;
  description?: string;
  /** 标量 / map 样例值（字符串形式，提交时按 kind 解析） */
  defaultValue?: string;
  required?: boolean;
  /** object 子字段 */
  children?: BodyFieldNode[];
  /** array 元素模板（name 可空） */
  items?: BodyFieldNode;
  /** map 的 value 模板 */
  mapValue?: BodyFieldNode;
}

let keySeq = 0;
export function newFieldKey(): string {
  keySeq += 1;
  return `bf_${Date.now()}_${keySeq}`;
}

export function emptyField(
  kind: BodyFieldKind = "string",
  name = "",
): BodyFieldNode {
  const node: BodyFieldNode = {
    key: newFieldKey(),
    name,
    kind,
    description: "",
    defaultValue: "",
    required: false,
  };
  if (kind === "object") node.children = [];
  if (kind === "array") node.items = emptyField("string");
  if (kind === "map") node.mapValue = emptyField("string");
  return node;
}

export function schemaToFields(
  schema?: Record<string, unknown> | null,
): BodyFieldNode[] {
  if (!schema || typeof schema !== "object") return [];
  const kind = detectKind(schema);
  if (kind === "object" && !isMapSchema(schema)) {
    const props = (schema.properties ?? {}) as Record<string, unknown>;
    const required = new Set(
      Array.isArray(schema.required)
        ? (schema.required as unknown[]).map(String)
        : [],
    );
    return Object.entries(props).map(([name, raw]) =>
      schemaNodeToField(name, asRecord(raw), required.has(name)),
    );
  }
  // 根不是 object properties 时，包一层匿名字段（少见）
  return [schemaNodeToField("body", schema, true)];
}

export function fieldsToSchema(
  fields: BodyFieldNode[],
): Record<string, unknown> | undefined {
  if (!fields.length) return undefined;
  const properties: Record<string, unknown> = {};
  const required: string[] = [];
  for (const f of fields) {
    const n = f.name?.trim();
    if (!n) continue;
    properties[n] = fieldToSchemaNode(f);
    if (f.required) required.push(n);
  }
  if (!Object.keys(properties).length) return undefined;
  const schema: Record<string, unknown> = {
    type: "object",
    properties,
  };
  if (required.length) schema.required = required;
  return schema;
}

/** 按字段树默认值生成试连用 body 对象。 */
export function buildDefaultBody(
  fields: BodyFieldNode[],
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const f of fields) {
    const n = f.name?.trim();
    if (!n) continue;
    const v = buildDefaultValue(f);
    if (v !== undefined) out[n] = v;
  }
  return out;
}

function buildDefaultValue(f: BodyFieldNode): unknown {
  switch (f.kind) {
    case "object": {
      const o: Record<string, unknown> = {};
      for (const c of f.children ?? []) {
        const n = c.name?.trim();
        if (!n) continue;
        const v = buildDefaultValue(c);
        if (v !== undefined) o[n] = v;
      }
      return o;
    }
    case "array": {
      if (!f.items) return [];
      const item = buildDefaultValue(f.items);
      return item === undefined ? [] : [item];
    }
    case "map": {
      const raw = f.defaultValue?.trim();
      if (raw) {
        try {
          const parsed = JSON.parse(raw) as unknown;
          if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
            return parsed;
          }
        } catch {
          /* fallthrough */
        }
      }
      if (f.mapValue) {
        const v = buildDefaultValue(f.mapValue);
        if (v !== undefined) return { key: v };
      }
      return {};
    }
    case "boolean": {
      const raw = f.defaultValue?.trim().toLowerCase();
      if (raw === "true") return true;
      if (raw === "false") return false;
      return undefined;
    }
    case "number":
    case "integer": {
      const raw = f.defaultValue?.trim();
      if (!raw) return undefined;
      const n = Number(raw);
      return Number.isFinite(n) ? n : undefined;
    }
    default: {
      const raw = f.defaultValue;
      return raw === undefined || raw === "" ? undefined : raw;
    }
  }
}

function schemaNodeToField(
  name: string,
  schema: Record<string, unknown>,
  required: boolean,
): BodyFieldNode {
  const kind = detectKind(schema);
  const node: BodyFieldNode = {
    key: newFieldKey(),
    name,
    kind,
    description: typeof schema.description === "string" ? schema.description : "",
    defaultValue:
      schema.default === undefined || schema.default === null
        ? ""
        : typeof schema.default === "object"
          ? JSON.stringify(schema.default)
          : String(schema.default),
    required,
  };
  if (kind === "object") {
    const props = (schema.properties ?? {}) as Record<string, unknown>;
    const req = new Set(
      Array.isArray(schema.required)
        ? (schema.required as unknown[]).map(String)
        : [],
    );
    node.children = Object.entries(props).map(([n, raw]) =>
      schemaNodeToField(n, asRecord(raw), req.has(n)),
    );
  } else if (kind === "array") {
    node.items = schemaNodeToField(
      "item",
      asRecord(schema.items) || { type: "string" },
      false,
    );
  } else if (kind === "map") {
    node.mapValue = schemaNodeToField(
      "value",
      asRecord(schema.additionalProperties) || { type: "string" },
      false,
    );
  }
  return node;
}

function fieldToSchemaNode(f: BodyFieldNode): Record<string, unknown> {
  const node: Record<string, unknown> = {};
  if (f.description?.trim()) node.description = f.description.trim();

  switch (f.kind) {
    case "object": {
      node.type = "object";
      const properties: Record<string, unknown> = {};
      const required: string[] = [];
      for (const c of f.children ?? []) {
        const n = c.name?.trim();
        if (!n) continue;
        properties[n] = fieldToSchemaNode(c);
        if (c.required) required.push(n);
      }
      node.properties = properties;
      if (required.length) node.required = required;
      break;
    }
    case "array": {
      node.type = "array";
      node.items = f.items
        ? fieldToSchemaNode(f.items)
        : { type: "string" };
      break;
    }
    case "map": {
      node.type = "object";
      node["x-field-kind"] = "map";
      node.additionalProperties = f.mapValue
        ? fieldToSchemaNode(f.mapValue)
        : { type: "string" };
      if (f.defaultValue?.trim()) {
        try {
          node.default = JSON.parse(f.defaultValue.trim());
        } catch {
          /* ignore invalid default */
        }
      }
      break;
    }
    case "integer":
    case "number":
    case "boolean":
    case "string":
    default: {
      node.type = f.kind === "integer" || f.kind === "number" || f.kind === "boolean"
        ? f.kind
        : "string";
      if (f.defaultValue !== undefined && f.defaultValue !== "") {
        if (f.kind === "boolean") {
          node.default = f.defaultValue.trim().toLowerCase() === "true";
        } else if (f.kind === "number" || f.kind === "integer") {
          const n = Number(f.defaultValue);
          if (Number.isFinite(n)) node.default = n;
        } else {
          node.default = f.defaultValue;
        }
      }
      break;
    }
  }
  return node;
}

function detectKind(schema: Record<string, unknown>): BodyFieldKind {
  if (isMapSchema(schema)) return "map";
  const t = schema.type;
  if (t === "array") return "array";
  if (t === "object") return "object";
  if (t === "integer") return "integer";
  if (t === "number") return "number";
  if (t === "boolean") return "boolean";
  if (t === "string") return "string";
  if (schema.properties) return "object";
  if (schema.items) return "array";
  return "string";
}

function isMapSchema(schema: Record<string, unknown>): boolean {
  if (schema["x-field-kind"] === "map") return true;
  if (schema.type === "object" && schema.additionalProperties != null) {
    const props = schema.properties as Record<string, unknown> | undefined;
    if (!props || Object.keys(props).length === 0) return true;
  }
  return false;
}

function asRecord(v: unknown): Record<string, unknown> {
  if (v && typeof v === "object" && !Array.isArray(v)) {
    return v as Record<string, unknown>;
  }
  return { type: "string" };
}

/**
 * 从试连响应 JSON 推断字段树（描述留空，供用户补充）。
 * 根为 object → 顶层 properties；根为 array → 单字段 items；标量 → 单字段 value。
 */
export function inferFieldsFromJson(value: unknown): BodyFieldNode[] {
  if (value === null || value === undefined) return [];
  if (Array.isArray(value)) {
    const item = inferFieldFromValue("item", value[0] ?? "");
    return [
      {
        ...emptyField("array", "items"),
        items: item,
        required: false,
        description: "",
      },
    ];
  }
  if (typeof value === "object") {
    return Object.entries(value as Record<string, unknown>).map(([k, v]) =>
      inferFieldFromValue(k, v),
    );
  }
  return [inferFieldFromValue("value", value)];
}

/** 响应 JSON → JSON Schema（object）。 */
export function inferSchemaFromJson(
  value: unknown,
): Record<string, unknown> | undefined {
  return fieldsToSchema(inferFieldsFromJson(value));
}

function inferFieldFromValue(name: string, value: unknown): BodyFieldNode {
  if (value === null || value === undefined) {
    return { ...emptyField("string", name), description: "", required: false };
  }
  if (typeof value === "boolean") {
    return {
      ...emptyField("boolean", name),
      defaultValue: String(value),
      description: "",
      required: false,
    };
  }
  if (typeof value === "number") {
    const kind: BodyFieldKind = Number.isInteger(value) ? "integer" : "number";
    return {
      ...emptyField(kind, name),
      defaultValue: String(value),
      description: "",
      required: false,
    };
  }
  if (typeof value === "string") {
    return {
      ...emptyField("string", name),
      defaultValue: value.length > 80 ? "" : value,
      description: "",
      required: false,
    };
  }
  if (Array.isArray(value)) {
    const sample = value.find((x) => x !== null && x !== undefined);
    return {
      ...emptyField("array", name),
      items: inferFieldFromValue("item", sample ?? ""),
      description: "",
      required: false,
    };
  }
  // plain object
  const children = Object.entries(value as Record<string, unknown>).map(
    ([k, v]) => inferFieldFromValue(k, v),
  );
  return {
    ...emptyField("object", name),
    children,
    description: "",
    required: false,
  };
}
