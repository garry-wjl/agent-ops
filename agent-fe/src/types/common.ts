/**
 * 通用类型 — 与后端 facade/client 层 Result/PageVO/ErrorCode 对齐
 * 详见 S2 总体方案 §5.3 / §5.4 / §6.1
 */

export interface Result<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

export interface PageParam {
  pageNo: number;
  pageSize: number;
}

export interface PageVO<T> {
  total: number;
  list: T[];
  pageNo?: number;
  pageSize?: number;
}

/** 业务编号前缀 — 见总体方案 §10.3 */
export const NumPrefix = {
  AGENT: 'AGT',
  AGENT_VERSION: 'AVN',
  SKILL: 'SKL',
  SKILL_VERSION: 'SVN',
  SESSION: 'SES',
  MESSAGE: 'MSG',
  EVALUATION: 'EVL',
  EVALUATION_CASE: 'EVC',
  TRACE: 'TRC',
} as const;

/** 错误码段 — 见 S2 总体方案 §5.3 */
export const BizCode = {
  SUCCESS: 0,
  PARAM_INVALID: 1001,
  UNAUTHORIZED: 1002,
  FORBIDDEN: 1003,
  NOT_FOUND: 1004,
  CONFLICT: 1005,
  AGENT_NOT_FOUND: 2001,
  AGENT_OFFLINE: 2002,
  AGENT_MODE_NOT_SUPPORTED: 2003,
  AGENT_REMOTE_FAIL: 2004,
  /** v2.6 A2A 接入：远端 Nacos AI Registry 不可达或未找到该 Agent */
  A2A_REMOTE_UNREACHABLE: 2011,
  /** v2.6 A2A 接入：同 nacosAgentName 已被订阅 */
  A2A_AGENT_ALREADY_SUBSCRIBED: 2012,
  /** v2.6 A2A 接入：草稿不存在或已转正 */
  A2A_AGENT_DRAFT_NOT_FOUND: 2013,
  SKILL_NOT_FOUND: 3001,
  SKILL_DEPRECATED: 3002,
  SKILL_SCHEMA_INVALID: 3003,
  SKILL_BREAKING_REQUIRES_MAJOR: 3004,
  INVOKE_TIMEOUT: 4001,
  LLM_QUOTA: 4002,
  SSE_INTERRUPTED: 4003,
  EVAL_RUNNING: 5001,
  EVAL_CASE_GEN_FAIL: 5002,
  EVAL_JUDGE_FAIL: 5003,
  DRAFT_NOT_FOUND: 6001,
  DRAFT_LOCKED: 6002,
  VERSION_CONFLICT: 6003,
  SYSTEM_BUSY: 9001,
  THIRD_PARTY_FAIL: 9002,
} as const;
