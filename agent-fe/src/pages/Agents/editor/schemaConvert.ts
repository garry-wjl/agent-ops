/** 字段表能表达的 JSON Schema 子集，和文本模式互相转换。 */

export type SchemaFieldType =
  | 'string'
  | 'number'
  | 'integer'
  | 'boolean'
  | 'object'
  | 'array';

export type SchemaItemType = 'string' | 'number' | 'integer' | 'boolean' | 'object';

export interface SchemaField {
  name: string;
  type: SchemaFieldType;
  required: boolean;
  description?: string;
  /** 标量枚举，逗号分隔。 */
  enumText?: string;
  itemType?: SchemaItemType;
  children?: SchemaField[];
}

const FIELD_TYPES = new Set<string>([
  'string',
  'number',
  'integer',
  'boolean',
  'object',
  'array',
]);

const ITEM_TYPES = new Set<string>(['string', 'number', 'integer', 'boolean', 'object']);

const UNSUPPORTED_KEYS = [
  '$ref',
  'anyOf',
  'oneOf',
  'allOf',
  'not',
  'pattern',
  'patternProperties',
  'if',
  'then',
  'else',
  'dependentSchemas',
  'prefixItems',
];

export type SchemaConvertResult =
  | { ok: true; fields: SchemaField[] }
  | { ok: false; reason: string };

/** 字段表 → JSON Schema。根始终是 object。 */
export function fieldsToSchema(fields: SchemaField[]): Record<string, unknown> {
  return objectSchema(fields);
}

/** JSON Schema → 字段表。表达不了时返回原因，调用方留在文本模式。 */
export function schemaToFields(schema: unknown): SchemaConvertResult {
  if (!isRecord(schema)) {
    return { ok: false, reason: 'JSON Schema 必须是对象' };
  }
  if (schema.type !== 'object') {
    return { ok: false, reason: '字段表只编辑根类型为 object 的 Schema' };
  }
  const blocked = unsupportedKey(schema);
  if (blocked) {
    return { ok: false, reason: `字段表不能表达 ${blocked}` };
  }
  if (schema.properties != null && !isRecord(schema.properties)) {
    return { ok: false, reason: 'properties 必须是对象' };
  }
  try {
    return { ok: true, fields: readObjectFields(schema) };
  } catch (error) {
    return {
      ok: false,
      reason: error instanceof Error ? error.message : 'Schema 不能转成字段表',
    };
  }
}

function objectSchema(fields: SchemaField[]): Record<string, unknown> {
  const properties: Record<string, unknown> = {};
  const required: string[] = [];
  for (const field of fields) {
    const name = field.name.trim();
    if (!name) continue;
    properties[name] = fieldSchema(field);
    if (field.required) required.push(name);
  }
  const schema: Record<string, unknown> = {
    type: 'object',
    properties,
    additionalProperties: false,
  };
  if (required.length > 0) schema.required = required;
  return schema;
}

function fieldSchema(field: SchemaField): Record<string, unknown> {
  const schema: Record<string, unknown> = { type: field.type };
  if (field.description?.trim()) schema.description = field.description.trim();
  const enumValues = parseEnum(field.enumText, field.type);
  if (enumValues) schema.enum = enumValues;
  if (field.type === 'object') {
    return { ...objectSchema(field.children ?? []), ...pickDescription(schema) };
  }
  if (field.type === 'array') {
    const itemType = field.itemType ?? 'string';
    schema.items =
      itemType === 'object'
        ? objectSchema(field.children ?? [])
        : { type: itemType };
  }
  return schema;
}

function pickDescription(schema: Record<string, unknown>): Record<string, unknown> {
  return schema.description ? { description: schema.description } : {};
}

function readObjectFields(schema: Record<string, unknown>): SchemaField[] {
  const blocked = unsupportedKey(schema);
  if (blocked) throw new Error(`字段表不能表达 ${blocked}`);
  const properties = isRecord(schema.properties) ? schema.properties : {};
  const required = new Set(
    Array.isArray(schema.required)
      ? schema.required.filter((item): item is string => typeof item === 'string')
      : [],
  );
  return Object.entries(properties).map(([name, value]) => {
    if (!isRecord(value)) throw new Error(`${name} 不是对象`);
    return readField(name, value, required.has(name));
  });
}

function readField(name: string, schema: Record<string, unknown>, required: boolean): SchemaField {
  const blocked = unsupportedKey(schema);
  if (blocked) throw new Error(`${name} 含有 ${blocked}`);
  const type = schema.type;
  if (typeof type !== 'string' || !FIELD_TYPES.has(type)) {
    throw new Error(`${name} 的类型字段表无法表达`);
  }
  const field: SchemaField = {
    name,
    type: type as SchemaFieldType,
    required,
    description: typeof schema.description === 'string' ? schema.description : undefined,
    enumText: formatEnum(schema.enum),
  };
  if (type === 'object') {
    field.children = readObjectFields(schema);
  }
  if (type === 'array') {
    if (!isRecord(schema.items)) throw new Error(`${name} 的 items 字段表无法表达`);
    const itemType = schema.items.type;
    if (typeof itemType !== 'string' || !ITEM_TYPES.has(itemType)) {
      throw new Error(`${name} 的数组元素类型字段表无法表达`);
    }
    field.itemType = itemType as SchemaItemType;
    if (itemType === 'object') field.children = readObjectFields(schema.items);
  }
  return field;
}

function unsupportedKey(schema: Record<string, unknown>): string | undefined {
  return UNSUPPORTED_KEYS.find((key) => schema[key] != null);
}

function parseEnum(text: string | undefined, type: SchemaFieldType): Array<string | number> | undefined {
  if (!text?.trim()) return undefined;
  if (type !== 'string' && type !== 'number' && type !== 'integer') return undefined;
  const parts = text.split(',').map((item) => item.trim()).filter(Boolean);
  if (parts.length === 0) return undefined;
  if (type === 'string') return parts;
  return parts.map((item) => (type === 'integer' ? parseInt(item, 10) : Number(item)));
}

function formatEnum(value: unknown): string | undefined {
  if (!Array.isArray(value) || value.length === 0) return undefined;
  return value.map((item) => String(item)).join(', ');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
