/**
 * 评测领域类型 — 对齐评测技术方案 §3.2 / §10
 */

export type EvalTargetType = 'SKILL' | 'AGENT';
export type EvalMode = 'MANUAL' | 'AUTO';
export type EvalStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type JudgeMethod = 'LLM_JUDGE' | 'KEYWORD' | 'RULE' | 'NONE';
export type EvalDimension =
  | 'ACCURACY'
  | 'RELEVANCE'
  | 'COMPLETENESS'
  | 'STYLE'
  | 'COMPLIANCE';

export interface EvaluationVO {
  num: string;
  targetType: EvalTargetType;
  targetNum: string;
  targetVersionNum?: string;
  mode: EvalMode;
  totalScore?: number;
  passRate?: number;
  status: EvalStatus;
  caseCount?: number;
  latencyMs?: number;
  gmtCreate?: string;
  judgeMethod?: JudgeMethod;
  executorAgentNum?: string;
  judgeAgentNum?: string;
}

export interface EvaluationDetailVO extends EvaluationVO {
  report?: {
    summary?: string;
    dimensions?: { name: EvalDimension; score: number }[];
  };
  radarData?: { dimension: EvalDimension; score: number }[];
}

export interface EvalCaseVO {
  seq: number;
  input: unknown;
  expected?: unknown;
  actual?: unknown;
  dimensionScores?: { dimension: EvalDimension; score: number }[];
  totalScore?: number;
  passed?: boolean;
  latencyMs?: number;
  judgeExplanation?: string;
  traceId?: string;
}

export interface EvalSeedVO {
  num: string;
  skillNum: string;
  input: unknown;
  expectedOutput?: unknown;
  origin: 'MANUAL' | 'IMPORTED';
  gmtCreate: string;
}

export interface EvalCreateManualParam {
  targetType: EvalTargetType;
  targetNum: string;
  executorAgentNum: string;
  input: unknown;
  expectedOutput?: unknown;
  judgeMethod: JudgeMethod;
}

export interface EvalCreateAutoParam {
  targetType: EvalTargetType;
  targetNum: string;
  executorAgentNum: string;
  judgeAgentNum: string;
  dimensions: EvalDimension[];
  caseCount: number;
  dataSource: 'SCHEMA_GEN' | 'HISTORY' | 'SEED';
  concurrency: number;
}

export interface EvalDashboardStats {
  totalEvalCount: number;
  passRate: number;
  avgLatencyMs: number;
  seedCount: number;
}

export interface EvalCompareVO {
  targetType: EvalTargetType;
  targetNum: string;
  vA: string;
  vB: string;
  totalScoreDiff: number;
  dimensionDiffs: { dimension: EvalDimension; diff: number }[];
  passRateDiff: number;
  latencyDiff: number;
}

export interface EvalPageQuery {
  pageNo: number;
  pageSize: number;
  targetType?: EvalTargetType;
  targetNum?: string;
  status?: EvalStatus;
  mode?: EvalMode;
}
