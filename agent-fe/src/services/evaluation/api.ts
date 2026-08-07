/**
 * 评测服务 — 对应评测技术方案 §5.2.1
 */
import { get, post } from '../request';
import type {
  EvaluationVO,
  EvaluationDetailVO,
  EvalCaseVO,
  EvalSeedVO,
  EvalCreateManualParam,
  EvalCreateAutoParam,
  EvalDashboardStats,
  EvalCompareVO,
  EvalPageQuery,
  PageVO,
} from '@/types';

export const evalApi = {
  createManual: (param: EvalCreateManualParam) =>
    post<EvaluationVO>('/api/v1/evaluation/command/createManual', param),
  createAuto: (param: EvalCreateAutoParam) =>
    post<{ num: string; status: 'PENDING' }>(
      '/api/v1/evaluation/command/createAuto',
      param,
    ),
  rerun: (evalNum: string) =>
    post<{ newEvalNum: string; status: 'PENDING' | 'SUCCESS' }>(
      '/api/v1/evaluation/command/rerun',
      { evalNum },
    ),
  delete: (evalNum: string) =>
    post<void>('/api/v1/evaluation/command/delete', { evalNum }),

  saveSeed: (skillNum: string, input: unknown, expectedOutput?: unknown) =>
    post<{ num: string }>('/api/v1/evaluation/seed/save', {
      skillNum,
      input,
      expectedOutput,
    }),
  deleteSeed: (seedNum: string) =>
    post<void>('/api/v1/evaluation/seed/delete', { seedNum }),
  seedList: (skillNum?: string) =>
    get<EvalSeedVO[]>('/api/v1/evaluation/seed/list', { skillNum }),

  pageList: (query: EvalPageQuery) =>
    post<PageVO<EvaluationVO>>('/api/v1/evaluation/query/list', query),
  detail: (num: string) =>
    get<EvaluationDetailVO>('/api/v1/evaluation/query/detail', { num }),
  cases: (evalNum: string) =>
    get<EvalCaseVO[]>('/api/v1/evaluation/query/cases', { evalNum }),
  dashboardStats: () =>
    get<EvalDashboardStats>('/api/v1/evaluation/query/dashboardStats'),
  compare: (evalNumA: string, evalNumB: string) =>
    post<EvalCompareVO>('/api/v1/evaluation/query/compare', {
      evalNumA,
      evalNumB,
    }),
};
