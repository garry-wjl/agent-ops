/**
 * 评测集 Schema 层级解析：嵌套 object / array 展开为表单节点与点路径列。
 */
export type SchemaLeafType = 'string' | 'number' | 'boolean' | 'object' | 'array' | string;

export interface SchemaNode {
  name: string;
  type: SchemaLeafType;
  description?: string;
  /** object 且有 properties */
  properties?: SchemaNode[];
  /** array 元素定义 */
  items?: SchemaNode;
}

const MAX_DEPTH = 3;

/** 详情表格展示允许更深一层，便于多层 Schema 可读 */
const MAX_TABLE_DEPTH = 8;

function asNode(name: string, raw: unknown): SchemaNode {
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    const o = raw as Record<string, unknown>;
    const type = String(o.type || 'string').toLowerCase();
    const node: SchemaNode = {
      name,
      type,
      description: o.description != null ? String(o.description) : undefined,
    };
    if (type === 'object' && o.properties && typeof o.properties === 'object') {
      const props = o.properties as Record<string, unknown>;
      node.properties = Object.keys(props).map((k) => asNode(k, props[k]));
    }
    if (type === 'array' && o.items) {
      node.items = asNode('item', o.items);
    }
    return node;
  }
  return { name, type: 'string' };
}

/** 解析 schemaJson 为顶层字段节点 */
export function parseSchemaNodes(schemaJson?: string): SchemaNode[] {
  if (!schemaJson?.trim()) {
    return [
      { name: 'input', type: 'string' },
      { name: 'reference', type: 'string' },
    ];
  }
  try {
    const arr = JSON.parse(schemaJson) as unknown[];
    if (!Array.isArray(arr)) return parseSchemaNodes(undefined);
    const nodes = arr
      .map((item) => {
        if (!item || typeof item !== 'object') return null;
        const o = item as Record<string, unknown>;
        const name = String(o.name || '').trim();
        if (!name) return null;
        return asNode(name, o);
      })
      .filter(Boolean) as SchemaNode[];
    return nodes.length ? nodes : parseSchemaNodes(undefined);
  } catch {
    return parseSchemaNodes(undefined);
  }
}

/** 是否应展开为子字段（有 properties 的 object） */
export function isExpandableObject(node: SchemaNode, depth: number): boolean {
  return (
    node.type === 'object' &&
    !!node.properties?.length &&
    depth < MAX_DEPTH
  );
}

/** 是否应展开为 Form.List（array） */
export function isExpandableArray(node: SchemaNode, depth: number): boolean {
  return node.type === 'array' && depth < MAX_DEPTH;
}

/** 无 properties 的 object / 无结构 array → 用 JSON 编辑器 */
export function isJsonLeaf(node: SchemaNode, depth: number): boolean {
  if (node.type === 'object' && !isExpandableObject(node, depth)) return true;
  if (node.type === 'array' && !isExpandableArray(node, depth)) return true;
  return false;
}

/** 清理空值，避免提交空 object/array */
export function pruneEmptyData(value: unknown): unknown {
  if (value == null) return undefined;
  if (typeof value === 'string') {
    const t = value.trim();
    return t === '' ? undefined : t;
  }
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  if (Array.isArray(value)) {
    const list = value
      .map((v) => pruneEmptyData(v))
      .filter((v) => v !== undefined);
    return list.length ? list : undefined;
  }
  if (typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      const pv = pruneEmptyData(v);
      if (pv !== undefined) out[k] = pv;
    }
    return Object.keys(out).length ? out : undefined;
  }
  return value;
}

/** 从表单值构建提交 data（嵌套） */
export function buildRowDataFromForm(
  values: Record<string, unknown>,
): Record<string, unknown> {
  const pruned = pruneEmptyData(values);
  if (!pruned || typeof pruned !== 'object' || Array.isArray(pruned)) {
    return {};
  }
  return pruned as Record<string, unknown>;
}

/** 为新增行准备初始值：可展开 object 给 {}，array 给 [默认一项] */
export function buildAddRowInitialValues(nodes: SchemaNode[]): Record<string, unknown> {
  const init: Record<string, unknown> = {};
  for (const n of nodes) {
    init[n.name] = defaultValueForNode(n, 1);
  }
  return init;
}

/**
 * 新增用例的空数据结构（真实 JSON 对象，供路径表 / JSON 编辑共用）。
 * 可展开 array 预置一项，便于在路径表中直接填写。
 */
export function buildEmptyCaseData(nodes: SchemaNode[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const n of nodes) {
    out[n.name] = emptyCaseValueForNode(n, 1);
  }
  return out;
}

function emptyCaseValueForNode(node: SchemaNode, depth: number): unknown {
  if (isExpandableObject(node, depth)) {
    const o: Record<string, unknown> = {};
    for (const c of node.properties || []) {
      o[c.name] = emptyCaseValueForNode(c, depth + 1);
    }
    return o;
  }
  if (isExpandableArray(node, depth)) {
    const item = node.items || { name: 'item', type: 'string' };
    if (item.type === 'object' && item.properties?.length) {
      return [emptyCaseValueForNode({ ...item, name: 'item' }, depth + 1)];
    }
    if (isJsonLeaf(item, depth + 1)) {
      return [item.type === 'array' ? [] : {}];
    }
    return [''];
  }
  if (isJsonLeaf(node, depth)) {
    return node.type === 'array' ? [] : {};
  }
  return '';
}

function defaultValueForNode(node: SchemaNode, depth: number): unknown {
  if (isExpandableObject(node, depth)) {
    const o: Record<string, unknown> = {};
    for (const c of node.properties || []) {
      o[c.name] = defaultValueForNode(c, depth + 1);
    }
    return o;
  }
  if (isExpandableArray(node, depth)) {
    const item = node.items;
    if (item && item.type === 'object' && item.properties?.length) {
      return [defaultValueForNode({ ...item, name: 'item' }, depth + 1)];
    }
    if (isJsonLeaf(item || { name: 'item', type: 'string' }, depth + 1)) {
      return ['{\n  \n}'];
    }
    return [''];
  }
  if (isJsonLeaf(node, depth)) {
    return node.type === 'array' ? '[]' : '{\n  \n}';
  }
  return undefined;
}

/** 提交前：把 JSON 叶字段的字符串 parse 成对象/数组 */
export function hydrateJsonLeaves(
  values: Record<string, unknown>,
  nodes: SchemaNode[],
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...values };
  for (const n of nodes) {
    out[n.name] = hydrateNode(out[n.name], n, 1);
  }
  return out;
}

/**
 * 将行 dataJson 转为表单初值（与 hydrateJsonLeaves 互逆：JSON 叶字段 stringify）。
 */
export function buildFormValuesFromDataJson(
  dataJson: string | undefined | null,
  nodes: SchemaNode[],
): Record<string, unknown> {
  let data: Record<string, unknown> = {};
  if (dataJson?.trim()) {
    try {
      const parsed = JSON.parse(dataJson) as unknown;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        data = parsed as Record<string, unknown>;
      }
    } catch {
      data = {};
    }
  }
  const out: Record<string, unknown> = {};
  for (const n of nodes) {
    out[n.name] = formValueForNode(data[n.name], n, 1);
  }
  return out;
}

function formValueForNode(
  value: unknown,
  node: SchemaNode,
  depth: number,
): unknown {
  if (isJsonLeaf(node, depth)) {
    if (value == null) {
      return node.type === 'array' ? '[]' : '{\n  \n}';
    }
    if (typeof value === 'string') return value;
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }
  if (isExpandableObject(node, depth)) {
    const src =
      value && typeof value === 'object' && !Array.isArray(value)
        ? (value as Record<string, unknown>)
        : {};
    const o: Record<string, unknown> = {};
    for (const c of node.properties || []) {
      o[c.name] = formValueForNode(src[c.name], c, depth + 1);
    }
    return o;
  }
  if (isExpandableArray(node, depth)) {
    const item = node.items || { name: 'item', type: 'string' };
    if (!Array.isArray(value) || value.length === 0) {
      return [];
    }
    return value.map((v) => formValueForNode(v, item, depth + 1));
  }
  if (value == null) return undefined;
  return typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
    ? value
    : String(value);
}

export interface SchemaTableRow {
  key: string;
  /** 点路径，数组元素用 [] / [].field */
  path: string;
  type: string;
  description?: string;
  depth: number;
}

/**
 * 将嵌套 Schema 展平为表格行（兼容 object / array 多层）。
 */
export function flattenSchemaToTableRows(
  nodes: SchemaNode[],
  options?: { maxDepth?: number },
): SchemaTableRow[] {
  const maxDepth = options?.maxDepth ?? MAX_TABLE_DEPTH;
  const rows: SchemaTableRow[] = [];

  const walk = (node: SchemaNode, path: string, depth: number) => {
    const typeLabel = formatSchemaType(node);
    rows.push({
      key: `${path}@${depth}`,
      path,
      type: typeLabel,
      description: node.description,
      depth,
    });
    if (depth >= maxDepth) return;

    if (node.type === 'object' && node.properties?.length) {
      for (const child of node.properties) {
        walk(child, path ? `${path}.${child.name}` : child.name, depth + 1);
      }
    }
    if (node.type === 'array' && node.items) {
      const item = node.items;
      const itemPath = `${path}[]`;
      if (item.type === 'object' && item.properties?.length) {
        // 数组元素对象：先一行说明 item，再展开属性
        rows.push({
          key: `${itemPath}@${depth + 1}`,
          path: itemPath,
          type: formatSchemaType(item),
          description: item.description || '数组元素',
          depth: depth + 1,
        });
        if (depth + 1 < maxDepth) {
          for (const child of item.properties) {
            walk(child, `${itemPath}.${child.name}`, depth + 2);
          }
        }
      } else {
        walk(item, itemPath, depth + 1);
      }
    }
  };

  for (const n of nodes) {
    walk(n, n.name, 1);
  }
  return rows;
}

function formatSchemaType(node: SchemaNode): string {
  if (node.type === 'array') {
    const itemType = node.items?.type || 'unknown';
    if (node.items?.type === 'object' && node.items.properties?.length) {
      return `array<object>`;
    }
    return `array<${itemType}>`;
  }
  if (node.type === 'object' && node.properties?.length) {
    return 'object';
  }
  return node.type || 'string';
}

/** 用例数据展平行（对齐 Schema 路径表，并带上实际值） */
export interface CaseDataTableRow {
  key: string;
  path: string;
  type: string;
  description?: string;
  depth: number;
  /** 展示/编辑用字符串 */
  value: string;
  /** 是否为可编辑叶节点 */
  editable: boolean;
}

/**
 * 将 Schema + 行 dataJson 展平为路径表（数组按实例下标展开）。
 */
export function flattenCaseDataToTableRows(
  nodes: SchemaNode[],
  dataJson?: string | null,
  options?: { maxDepth?: number },
): CaseDataTableRow[] {
  const maxDepth = options?.maxDepth ?? MAX_TABLE_DEPTH;
  let data: Record<string, unknown> = {};
  if (dataJson?.trim()) {
    try {
      const parsed = JSON.parse(dataJson) as unknown;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        data = parsed as Record<string, unknown>;
      }
    } catch {
      data = {};
    }
  }
  const rows: CaseDataTableRow[] = [];

  const walk = (
    node: SchemaNode,
    path: string,
    depth: number,
    value: unknown,
  ) => {
    const typeLabel = formatSchemaType(node);
    if (depth >= maxDepth) {
      rows.push({
        key: `${path}@${depth}`,
        path,
        type: typeLabel,
        description: node.description,
        depth,
        value: formatCaseCellValue(value),
        editable: true,
      });
      return;
    }

    if (node.type === 'object' && node.properties?.length) {
      rows.push({
        key: `${path}@${depth}`,
        path,
        type: typeLabel,
        description: node.description,
        depth,
        value: '',
        editable: false,
      });
      const src =
        value && typeof value === 'object' && !Array.isArray(value)
          ? (value as Record<string, unknown>)
          : {};
      for (const child of node.properties) {
        walk(
          child,
          path ? `${path}.${child.name}` : child.name,
          depth + 1,
          src[child.name],
        );
      }
      return;
    }

    if (node.type === 'array') {
      const arr = Array.isArray(value) ? value : [];
      rows.push({
        key: `${path}@${depth}`,
        path,
        type: typeLabel,
        description: node.description,
        depth,
        value: arr.length ? `${arr.length} 项` : '[]',
        editable: false,
      });
      const item = node.items || { name: 'item', type: 'string' };
      if (item.type === 'object' && item.properties?.length) {
        arr.forEach((el, i) => {
          const itemPath = `${path}[${i}]`;
          rows.push({
            key: `${itemPath}@${depth + 1}`,
            path: itemPath,
            type: formatSchemaType(item),
            description: item.description || '数组元素',
            depth: depth + 1,
            value: '',
            editable: false,
          });
          const src =
            el && typeof el === 'object' && !Array.isArray(el)
              ? (el as Record<string, unknown>)
              : {};
          for (const child of item.properties || []) {
            walk(
              child,
              `${itemPath}.${child.name}`,
              depth + 2,
              src[child.name],
            );
          }
        });
      } else {
        arr.forEach((el, i) => {
          walk(item, `${path}[${i}]`, depth + 1, el);
        });
      }
      return;
    }

    rows.push({
      key: `${path}@${depth}`,
      path,
      type: typeLabel,
      description: node.description,
      depth,
      value: formatCaseCellValue(value),
      editable: true,
    });
  };

  for (const n of nodes) {
    walk(n, n.name, 1, data[n.name]);
  }
  return rows;
}

function formatCaseCellValue(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/**
 * 根据展平表编辑结果重建行对象（保留未出现在 edits 中的原数组结构）。
 */
export function buildDataFromCaseTableEdits(
  nodes: SchemaNode[],
  dataJson: string | undefined | null,
  edits: Record<string, string>,
): Record<string, unknown> {
  let original: Record<string, unknown> = {};
  if (dataJson?.trim()) {
    try {
      const parsed = JSON.parse(dataJson) as unknown;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        original = parsed as Record<string, unknown>;
      }
    } catch {
      original = {};
    }
  }
  const out: Record<string, unknown> = {};
  for (const n of nodes) {
    out[n.name] = buildCaseNodeValue(n, n.name, 1, original[n.name], edits);
  }
  return out;
}

function buildCaseNodeValue(
  node: SchemaNode,
  path: string,
  depth: number,
  original: unknown,
  edits: Record<string, string>,
): unknown {
  if (depth >= MAX_TABLE_DEPTH) {
    return parseEditedLeaf(node, edits[path] ?? formatCaseCellValue(original));
  }
  if (node.type === 'object' && node.properties?.length) {
    const src =
      original && typeof original === 'object' && !Array.isArray(original)
        ? (original as Record<string, unknown>)
        : {};
    const o: Record<string, unknown> = {};
    for (const child of node.properties) {
      const childPath = path ? `${path}.${child.name}` : child.name;
      o[child.name] = buildCaseNodeValue(
        child,
        childPath,
        depth + 1,
        src[child.name],
        edits,
      );
    }
    return o;
  }
  if (node.type === 'array') {
    const item = node.items || { name: 'item', type: 'string' };
    const arr = Array.isArray(original) ? original : [];
    return arr.map((el, i) => {
      const itemPath = `${path}[${i}]`;
      if (item.type === 'object' && item.properties?.length) {
        const src =
          el && typeof el === 'object' && !Array.isArray(el)
            ? (el as Record<string, unknown>)
            : {};
        const o: Record<string, unknown> = {};
        for (const child of item.properties) {
          o[child.name] = buildCaseNodeValue(
            child,
            `${itemPath}.${child.name}`,
            depth + 2,
            src[child.name],
            edits,
          );
        }
        return o;
      }
      return buildCaseNodeValue(item, itemPath, depth + 1, el, edits);
    });
  }
  return parseEditedLeaf(node, edits[path] ?? formatCaseCellValue(original));
}

function parseEditedLeaf(node: SchemaNode, raw: string): unknown {
  const text = raw ?? '';
  if (node.type === 'number') {
    if (!text.trim()) return undefined;
    const n = Number(text);
    if (Number.isNaN(n)) throw new Error(`字段须为数字`);
    return n;
  }
  if (node.type === 'boolean') {
    if (!text.trim()) return undefined;
    if (text === 'true' || text === 'false') return text === 'true';
    throw new Error(`字段须为 true/false`);
  }
  if (node.type === 'object' || node.type === 'array') {
    if (!text.trim()) return node.type === 'array' ? [] : {};
    try {
      return JSON.parse(text);
    } catch {
      throw new Error(`字段须为合法 JSON`);
    }
  }
  return text;
}

function hydrateNode(value: unknown, node: SchemaNode, depth: number): unknown {
  if (isJsonLeaf(node, depth)) {
    if (typeof value !== 'string' || !value.trim()) return undefined;
    try {
      return JSON.parse(value);
    } catch {
      throw new Error(`字段 ${node.name} 须为合法 JSON`);
    }
  }
  if (isExpandableObject(node, depth) && value && typeof value === 'object') {
    const src = value as Record<string, unknown>;
    const o: Record<string, unknown> = {};
    for (const c of node.properties || []) {
      o[c.name] = hydrateNode(src[c.name], c, depth + 1);
    }
    return o;
  }
  if (isExpandableArray(node, depth) && Array.isArray(value)) {
    const item = node.items || { name: 'item', type: 'string' };
    return value.map((v) => hydrateNode(v, item, depth + 1));
  }
  return value;
}
