import type { MountableToolItem, ToolRefParam } from '@/types';

/** 绑定键（与后端 ToolRef.bindingKey 对齐） */
export function toolBindingKey(ref: ToolRefParam): string {
  if (!ref.itemKind) return ref.toolNum;
  if (String(ref.itemKind).toUpperCase() === 'FC_ENDPOINT') {
    return `${ref.toolNum}|FC_ENDPOINT|${(ref.method || 'GET').toUpperCase()}|${ref.path || ''}`;
  }
  if (String(ref.itemKind).toUpperCase() === 'MCP_TOOL') {
    return `${ref.toolNum}|MCP_TOOL|${ref.mcpToolName || ''}`;
  }
  return ref.toolNum;
}

/** 是否为整组绑定键（无 itemKind 段）。 */
export function isWholeGroupBindingKey(key: string): boolean {
  return Boolean(key) && !key.includes('|');
}

/**
 * 同一父资产下整组与具体互斥：若存在整组（无 itemKind），丢弃该组下具体项。
 * 不同父资产可并存（整组 + 他组具体）。
 */
export function normalizeToolRefs(refs: ToolRefParam[]): ToolRefParam[] {
  const wholeGroupNums = new Set(
    refs.filter((r) => !r.itemKind).map((r) => r.toolNum).filter(Boolean),
  );
  return refs.filter((r) => !r.itemKind || !wholeGroupNums.has(r.toolNum));
}

/** 整组展开：按父资产编号取可挂载具体项。 */
export function groupMountableChildren(
  items: MountableToolItem[] | undefined,
  toolNum: string,
): MountableToolItem[] {
  if (!toolNum || !items?.length) return [];
  return items.filter((it) => it.toolNum === toolNum);
}

/**
 * 勾选「整组」：写入组键，并移除该组下所有具体项键。
 */
export function selectWholeGroup(
  selected: string[],
  groupNum: string,
  childKeys: string[],
): string[] {
  const childSet = new Set(childKeys);
  const next = selected.filter((k) => k !== groupNum && !childSet.has(k));
  next.push(groupNum);
  return next;
}

/**
 * 取消整组：仅移除组键。
 */
export function deselectWholeGroup(selected: string[], groupNum: string): string[] {
  return selected.filter((k) => k !== groupNum);
}

/**
 * 勾选/取消具体工具：
 * - 勾选时若该组处于整组模式，先取消整组再只保留本次子项（及他组已选）
 * - 取消时仅移除该子项键
 */
export function toggleChildToolKey(
  selected: string[],
  groupNum: string,
  childKey: string,
  childKeysOfGroup: string[],
): string[] {
  const childSet = new Set(childKeysOfGroup);
  const groupSelected = selected.includes(groupNum);
  if (selected.includes(childKey)) {
    return selected.filter((k) => k !== childKey);
  }
  // 新增子项：退出整组，去掉同组其它具体项中与整组冲突的逻辑——保留其它已选具体项
  let next = selected.filter((k) => k !== groupNum);
  if (groupSelected) {
    // 从整组切到具体：只勾当前这一项（用户意图是挑选子集）
    next = next.filter((k) => !childSet.has(k));
  }
  next.push(childKey);
  return next;
}

/** 统计选择摘要：整组数 + 具体项数 */
export function summarizeToolSelection(selected: string[]): {
  groupCount: number;
  itemCount: number;
} {
  let groupCount = 0;
  let itemCount = 0;
  for (const k of selected) {
    if (isWholeGroupBindingKey(k)) groupCount += 1;
    else itemCount += 1;
  }
  return { groupCount, itemCount };
}
