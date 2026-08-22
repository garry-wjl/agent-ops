/**
 * Agent 应用评测类型 — 对齐 BE client/evaluation/{dataset,grader,task} VOs
 */
import type { PageParam } from './common';

// ---- 枚举 / 字面量 ----

export type DatasetType = 'AGENT' | 'CUSTOM';
export type DatasetStatus = 'DRAFT' | 'PUBLISHED';
export type GraderKind = 'BUILTIN' | 'CODE' | 'LLM';
export type TaskBindMode = 'AGENT' | 'NONE';
export type TaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'FINISHED'
  | 'FAILED'
  | 'CANCELLED';
export type TaskItemStatus = 'PENDING' | 'RUNNING' | 'FINISHED' | 'FAILED';
export type CompareVerdict = 'uplift' | 'regress' | 'same' | 'missing';

/** 默认评测集 schema（CUSTOM / 未选 Agent） */
export const DEFAULT_DATASET_SCHEMA_JSON =
  '[{"name":"input","type":"string"},{"name":"reference","type":"string"},{"name":"context","type":"string"}]';

/**
 * AGENT 型基础 schema：对齐 Agent invoke（input / context）+ 评测常用 reference。
 * 选中具体 Agent 后由 {@link buildAgentDatasetSchema} 带出（可含提示词变量）。
 */
export const AGENT_DATASET_SCHEMA_JSON = `[
  {
    "name": "input",
    "type": "string",
    "description": "用户输入，对应 Agent invoke 内容"
  },
  {
    "name": "reference",
    "type": "string",
    "description": "参考答案（可选，供评估器比对）"
  },
  {
    "name": "context",
    "type": "object",
    "description": "调用上下文，对应 Agent invoke context"
  }
]`;

/** 从系统提示词提取 {{var}}（跳过内置 SESSION_NUM） */
export function extractPromptContextKeys(systemPrompt?: string | null): string[] {
  if (!systemPrompt) return [];
  const re = /\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}/g;
  const keys = new Set<string>();
  let m: RegExpExecArray | null;
  while ((m = re.exec(systemPrompt)) !== null) {
    if (m[1] === 'SESSION_NUM') continue;
    keys.add(m[1]);
  }
  return [...keys];
}

/**
 * 按 Agent 信息生成评测集 Schema。
 * context.properties 会带上提示词中解析出的变量，便于填行时对照。
 */
export function buildAgentDatasetSchema(opts: {
  agentNum: string;
  agentName?: string;
  systemPrompt?: string | null;
}): string {
  const label = opts.agentName?.trim() || opts.agentNum;
  const ctxKeys = extractPromptContextKeys(opts.systemPrompt);
  const contextField: Record<string, unknown> = {
    name: 'context',
    type: 'object',
    description: ctxKeys.length
      ? `调用上下文 · Agent「${label}」提示词变量: ${ctxKeys.join(', ')}`
      : `调用上下文 · Agent「${label}」（提示词暂无 {{变量}}）`,
  };
  if (ctxKeys.length) {
    contextField.properties = Object.fromEntries(
      ctxKeys.map((k) => [
        k,
        { type: 'string', description: `系统提示词变量 {{${k}}}` },
      ]),
    );
  }
  return prettyJson(
    JSON.stringify([
      {
        name: 'input',
        type: 'string',
        description: `用户输入 · Agent「${label}」invoke 内容`,
      },
      {
        name: 'reference',
        type: 'string',
        description: '参考答案（可选，供评估器比对）',
      },
      contextField,
    ]),
  );
}

/** 美化 JSON 字符串；失败返回原串 */
export function prettyJson(raw?: string | null, fallback = '[]'): string {
  const t = (raw ?? '').trim() || fallback;
  try {
    return JSON.stringify(JSON.parse(t), null, 2);
  } catch {
    return raw ?? fallback;
  }
}

/** 任务创建时评估器字段映射默认值 */
export const DEFAULT_GRADER_MAPPING: Record<string, string> = {
  response: '$actual_output',
  reference: '$row.reference',
};

// ---- Dataset ----

export interface EvalDatasetVO {
  num: string;
  workspaceNum?: string;
  name: string;
  description?: string;
  type: DatasetType | string;
  agentNum?: string;
  status: DatasetStatus | string;
  latestVersion?: number;
  createNo?: string;
  updateNo?: string;
  createTime?: string;
  updateTime?: string;
}

export interface EvalDatasetVersionVO {
  version: number;
  rowCount?: number;
  publishNo?: string;
  createTime?: string;
}

export interface EvalDatasetDetailVO extends EvalDatasetVO {
  schemaJson?: string;
  versions?: EvalDatasetVersionVO[];
}

export interface EvalDatasetRowVO {
  num: string;
  rowIndex: number;
  version?: number;
  dataJson?: string;
}

export interface CreateDatasetParam {
  name: string;
  description?: string;
  type: DatasetType | string;
  agentNum?: string;
  schemaJson: string;
}

export interface UpdateDatasetParam {
  num: string;
  name?: string;
  description?: string;
  schemaJson?: string;
}

export interface DatasetPageQuery extends PageParam {
  keyword?: string;
  type?: string;
  status?: string;
  agentNum?: string;
}

export interface CreateDatasetResultVO {
  num: string;
}

export interface PublishDatasetResultVO {
  version: number;
}

export interface AppendFromDebugParam {
  datasetNum: string;
  input: string;
  reference?: string;
  context?: string;
  output?: string;
}

export interface AddDatasetRowParam {
  datasetNum: string;
  data?: Record<string, unknown>;
  dataJson?: string;
}

export interface AddDatasetRowResultVO {
  rowNum: string;
  rowIndex: number;
}

export interface UpdateDatasetRowParam {
  datasetNum: string;
  rowNum: string;
  data?: Record<string, unknown>;
  dataJson?: string;
}

export interface DeleteDatasetRowParam {
  datasetNum: string;
  rowNum: string;
}

export interface ImportFromSessionsParam {
  datasetNum: string;
  sessionNums: string[];
}

export interface DatasetNumParam {
  num: string;
}

/** 自动生成 Case 任务状态 */
export type CaseGenJobStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'FINISHED'
  | 'FAILED'
  | 'CANCELLED';

export interface StartCaseGenParam {
  datasetNum: string;
  generatorAgentNum: string;
  generatorAgentVersionNum?: string;
  targetCount?: number | null;
  clearDraft?: boolean;
  instructionMode?: 'APPEND' | 'OVERRIDE' | string;
  userInstruction?: string;
}

export interface StartCaseGenResultVO {
  jobNum: string;
}

export interface RetryCaseGenParam {
  jobNum: string;
}

export interface CaseGenJobVO {
  num: string;
  workspaceNum?: string;
  datasetNum: string;
  generatorAgentNum: string;
  generatorAgentVersionNum?: string;
  targetCount?: number | null;
  clearDraft?: boolean;
  instructionMode?: string;
  userInstruction?: string;
  status: CaseGenJobStatus | string;
  progressPct?: number;
  progressMessage?: string;
  parsedCount?: number;
  writtenCount?: number;
  skippedCount?: number;
  errorMessage?: string;
  retryOfNum?: string;
  createNo?: string;
  createTime?: string;
  updateTime?: string;
}

export interface CaseGenJobPageQuery extends PageParam {
  datasetNum: string;
  status?: string;
}

// ---- Grader ----

export interface EvalGraderVO {
  num: string;
  workspaceNum?: string;
  name: string;
  description?: string;
  kind: GraderKind | string;
  builtinCode?: string;
  configJson?: string;
  version?: number;
  createTime?: string;
  updateTime?: string;
}

export interface GraderPresetVO {
  presetCode: string;
  name: string;
  description?: string;
  defaultConfigJson?: string;
}

export interface CreateBuiltinGraderParam {
  presetCode: string;
  name: string;
  description?: string;
  configJson?: string;
}

export interface CreateLlmGraderParam {
  name: string;
  description?: string;
  modelNum: string;
  promptTemplate: string;
  scoreMin?: number;
  scoreMax?: number;
  passThreshold?: number;
  variableNames?: string[];
}

export interface CreateCodeGraderParam {
  name: string;
  description?: string;
  script: string;
  timeoutMs?: number;
}

export interface DistillGraderFromTaskParam {
  taskNum: string;
  name: string;
  modelNum: string;
  description?: string;
}

export interface UpdateGraderParam {
  num: string;
  name?: string;
  description?: string;
  configJson?: string;
}

export interface GraderPageQuery extends PageParam {
  keyword?: string;
  kind?: string;
}

export interface GraderNumParam {
  num: string;
}

export interface CreateGraderResultVO {
  num: string;
}

export interface GraderTrialRunParam {
  graderNum: string;
  variables: Record<string, unknown>;
}

export interface GraderTrialResultVO {
  score?: number;
  passed?: boolean;
  explanation?: string;
}

// ---- Task ----

export interface EvalTaskGraderBriefVO {
  graderNum: string;
  graderVersion?: number;
  kind?: string;
  name?: string;
}

export interface EvalTaskVO {
  num: string;
  workspaceNum?: string;
  name: string;
  description?: string;
  datasetNum: string;
  datasetVersion: number;
  bindMode: TaskBindMode | string;
  agentNum?: string;
  agentVersionNum?: string;
  status: TaskStatus | string;
  totalCount?: number;
  passedCount?: number;
  failedCount?: number;
  /** 任务绑定的评估器摘要 */
  graders?: EvalTaskGraderBriefVO[];
  creatorUserId?: string;
  createTime?: string;
  updateTime?: string;
}

export interface EvalTaskDetailVO extends EvalTaskVO {
  graderBindingsJson?: string;
  labelConfigJson?: string;
}

export interface EvalTaskItemScoreVO {
  graderNum: string;
  /** 评估器名称 */
  graderName?: string;
  graderVersion?: number;
  score?: number;
  passed?: boolean;
  explanation?: string;
}

export interface EvalTaskItemVO {
  num: string;
  rowIndex: number;
  inputJson?: string;
  actualOutput?: string;
  traceSummaryJson?: string;
  labelJson?: string;
  overallPass?: boolean;
  status: TaskItemStatus | string;
  latencyMs?: number;
  errorMessage?: string;
  scores?: EvalTaskItemScoreVO[];
}

export interface SaveTaskLabelsParam {
  taskNum: string;
  labelConfigJson?: string;
  items: Array<{ itemNum: string; labelJson?: string }>;
}

/** 评测空间统计摘要（对齐 BE EvalStatsVO） */
export interface EvalTaskStatsVO {
  datasetCount?: number;
  graderCount?: number;
  taskCount?: number;
  runningTaskCount?: number;
  finishedTaskCount?: number;
  failedTaskCount?: number;
  /** 已完成任务平均用例通过率（0～100） */
  avgPassRate?: number | null;
}

export interface PublishGateVO {
  passed?: boolean;
  blockers?: string[];
  message?: string;
}

export interface PublishGateParam {
  agentNum: string;
  agentVersionNum: string;
}

export interface GraderBindingParam {
  graderNum: string;
  mapping?: Record<string, string>;
}

export interface CreateAndStartTaskParam {
  name: string;
  description?: string;
  datasetNum: string;
  datasetVersion: number;
  bindMode: TaskBindMode | string;
  agentNum?: string;
  agentVersionNum?: string;
  graders: GraderBindingParam[];
}

export interface CreateTaskResultVO {
  num: string;
}

export interface TaskPageQuery extends PageParam {
  keyword?: string;
  status?: string;
  datasetNum?: string;
  agentNum?: string;
}

export interface TaskNumParam {
  num: string;
}

export interface TaskCompareParam {
  leftTaskNum: string;
  rightTaskNum: string;
}

export interface TaskCompareRowVO {
  rowIndex: number;
  leftPass?: boolean;
  rightPass?: boolean;
  verdict: CompareVerdict | string;
}

export interface TaskCompareVO {
  leftTaskNum: string;
  rightTaskNum: string;
  leftPassRate?: number;
  rightPassRate?: number;
  passRateDiff?: number;
  rows?: TaskCompareRowVO[];
}
