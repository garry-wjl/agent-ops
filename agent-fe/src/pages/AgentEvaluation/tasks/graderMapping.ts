/**
 * 评测任务评估器变量 mapping：默认约定与行编辑辅助。
 */
import { DEFAULT_GRADER_MAPPING } from '@/types';

export type MappingRow = { id: string; name: string; source: string };

/** 数据源快捷选项（仍可手填自定义，如 $row.expected_answer） */
export const MAPPING_SOURCE_OPTIONS: { value: string; label: string }[] = [
  { value: '$actual_output', label: '$actual_output（Agent 实际输出）' },
  { value: '$trace', label: '$trace（运行轨迹）' },
  { value: '$row.reference', label: '$row.reference（评测集 reference 列）' },
];

export function defaultMappingRows(): MappingRow[] {
  return Object.entries(DEFAULT_GRADER_MAPPING).map(([name, source], i) => ({
    id: `def-${name}-${i}`,
    name,
    source,
  }));
}

export function recordToMappingRows(
  mapping: Record<string, string> | undefined,
): MappingRow[] {
  if (!mapping || Object.keys(mapping).length === 0) {
    return defaultMappingRows();
  }
  return Object.entries(mapping).map(([name, source], i) => ({
    id: `row-${name}-${i}`,
    name,
    source,
  }));
}

export function mappingRowsToRecord(
  rows: MappingRow[] | undefined,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (!rows) return { ...DEFAULT_GRADER_MAPPING };
  for (const row of rows) {
    const name = row.name?.trim();
    const source = row.source?.trim();
    if (!name || !source) continue;
    out[name] = source;
  }
  return Object.keys(out).length > 0 ? out : { ...DEFAULT_GRADER_MAPPING };
}

/**
 * 评估器多选变更时同步各评估器 mapping：保留仍选中的自定义，新选中的用默认。
 */
export function syncMappingsByGraders(
  prev: Record<string, MappingRow[]>,
  graderNums: string[],
): Record<string, MappingRow[]> {
  const next: Record<string, MappingRow[]> = {};
  for (const num of graderNums) {
    next[num] = prev[num]?.length ? prev[num] : defaultMappingRows();
  }
  return next;
}

export function newMappingRow(): MappingRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    name: '',
    source: '',
  };
}

export type GraderBindingPayload = {
  graderNum: string;
  mapping: Record<string, string>;
};

/** 组装创建任务 API 的 graders 载荷（与 Create 页提交一致） */
export function buildGraderBindingsPayload(
  graderNums: string[],
  mappingsByGrader: Record<string, MappingRow[]>,
): GraderBindingPayload[] {
  return graderNums.map((graderNum) => ({
    graderNum,
    mapping: mappingRowsToRecord(mappingsByGrader[graderNum]),
  }));
}
