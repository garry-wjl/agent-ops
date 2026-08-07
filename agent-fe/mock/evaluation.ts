/**
 * mock — 评测域接口
 * query.list / query.detail / query.cases / query.dashboardStats / query.compare / seed.* / command.*
 */
import type { MockMethod } from 'vite-plugin-mock';
import { ok, paginate, isoMinutesAgo } from './_helpers';

const EVALS = [
  {
    num: 'EVL-0001',
    targetType: 'AGENT',
    targetNum: 'AGT-0001',
    targetVersionNum: 'AVN-00012',
    mode: 'AUTO',
    totalScore: 87,
    passRate: 0.92,
    status: 'SUCCESS',
    caseCount: 25,
    latencyMs: 1200,
    judgeMethod: 'LLM_JUDGE',
    executorAgentNum: 'AGT-0001',
    judgeAgentNum: 'AGT-0003',
  },
  {
    num: 'EVL-0002',
    targetType: 'SKILL',
    targetNum: 'SKL-0002',
    targetVersionNum: 'SVN-0021',
    mode: 'AUTO',
    totalScore: 76,
    passRate: 0.78,
    status: 'SUCCESS',
    caseCount: 18,
    latencyMs: 800,
    judgeMethod: 'LLM_JUDGE',
    executorAgentNum: 'AGT-0001',
    judgeAgentNum: 'AGT-0003',
  },
  {
    num: 'EVL-0003',
    targetType: 'AGENT',
    targetNum: 'AGT-0002',
    targetVersionNum: 'AVN-0021',
    mode: 'MANUAL',
    totalScore: 92,
    passRate: 0.95,
    status: 'SUCCESS',
    caseCount: 8,
    latencyMs: 950,
    judgeMethod: 'KEYWORD',
    executorAgentNum: 'AGT-0002',
  },
  {
    num: 'EVL-0004',
    targetType: 'SKILL',
    targetNum: 'SKL-0003',
    targetVersionNum: 'SVN-0031',
    mode: 'AUTO',
    status: 'RUNNING',
    caseCount: 30,
    judgeMethod: 'LLM_JUDGE',
    executorAgentNum: 'AGT-0003',
    judgeAgentNum: 'AGT-0001',
  },
  {
    num: 'EVL-0005',
    targetType: 'AGENT',
    targetNum: 'AGT-0004',
    targetVersionNum: 'AVN-0041',
    mode: 'AUTO',
    totalScore: 64,
    passRate: 0.6,
    status: 'FAILED',
    caseCount: 12,
    latencyMs: 1500,
    judgeMethod: 'LLM_JUDGE',
    executorAgentNum: 'AGT-0004',
    judgeAgentNum: 'AGT-0003',
  },
  {
    num: 'EVL-0006',
    targetType: 'SKILL',
    targetNum: 'SKL-0001',
    mode: 'MANUAL',
    status: 'PENDING',
    caseCount: 5,
    judgeMethod: 'NONE',
  },
];

EVALS.forEach((e, i) => {
  (e as any).gmtCreate = isoMinutesAgo((i + 1) * 60);
});

const SEEDS = [
  {
    num: 'EVC-0001',
    skillNum: 'SKL-0001',
    input: { query: '今日 KPI 文档汇总' },
    expectedOutput: { content: '应包含 5 项核心 KPI' },
    origin: 'MANUAL',
    gmtCreate: isoMinutesAgo(60 * 24),
  },
  {
    num: 'EVC-0002',
    skillNum: 'SKL-0002',
    input: { text: '一段超过 1000 字的产品需求文档...' },
    expectedOutput: { summary: '不超过 200 字' },
    origin: 'IMPORTED',
    gmtCreate: isoMinutesAgo(60 * 12),
  },
  {
    num: 'EVC-0003',
    skillNum: 'SKL-0003',
    input: { logLines: ['ERROR: timeout', 'WARN: retry'] },
    origin: 'MANUAL',
    gmtCreate: isoMinutesAgo(60 * 6),
  },
];

const dimensionScores = (base: number) => [
  { dimension: 'ACCURACY', score: base + 2 },
  { dimension: 'RELEVANCE', score: base + 5 },
  { dimension: 'COMPLETENESS', score: base - 3 },
  { dimension: 'STYLE', score: base + 1 },
  { dimension: 'COMPLIANCE', score: base + 4 },
];

export default [
  {
    url: '/api/v1/evaluation/query/list',
    method: 'post',
    response: ({ body }) => {
      const { pageNo = 1, pageSize = 20, status, mode, targetType, targetNum } =
        body ?? {};
      let list = EVALS.slice();
      if (status) list = list.filter((e) => e.status === status);
      if (mode) list = list.filter((e) => e.mode === mode);
      if (targetType) list = list.filter((e) => e.targetType === targetType);
      if (targetNum) list = list.filter((e) => e.targetNum === targetNum);
      return ok(paginate(list, pageNo, pageSize));
    },
  },
  {
    url: '/api/v1/evaluation/query/detail',
    method: 'get',
    response: ({ query }) => {
      const ev = EVALS.find((e) => e.num === query.num) ?? EVALS[0];
      const base = ev.totalScore ?? 75;
      return ok({
        ...ev,
        report: {
          summary: `mock 报告：整体评分 ${ev.totalScore ?? '-'}，主要短板在完整性维度。`,
          dimensions: dimensionScores(base),
        },
        radarData: dimensionScores(base).map((d) => ({
          dimension: d.dimension,
          score: d.score,
        })),
      });
    },
  },
  {
    url: '/api/v1/evaluation/query/cases',
    method: 'get',
    response: ({ query }) => {
      const ev = EVALS.find((e) => e.num === query.evalNum) ?? EVALS[0];
      const cases = Array.from({ length: Math.min(8, ev.caseCount ?? 5) }).map(
        (_, i) => ({
          seq: i + 1,
          input: { question: `mock 用例 ${i + 1}` },
          expected: { answer: '期望输出 N/A' },
          actual: { answer: `mock actual ${i + 1}` },
          dimensionScores: dimensionScores(70 + i),
          totalScore: 70 + i,
          passed: i % 4 !== 3,
          latencyMs: 800 + i * 50,
          judgeExplanation: i % 4 === 3 ? '完整性不足' : '符合预期',
          traceId: `trace-${ev.num}-${i + 1}`,
        }),
      );
      return ok(cases);
    },
  },
  {
    url: '/api/v1/evaluation/query/dashboardStats',
    method: 'get',
    response: () =>
      ok({
        totalEvalCount: EVALS.length,
        passRate: 0.83,
        avgLatencyMs: 1080,
        seedCount: SEEDS.length,
      }),
  },
  {
    url: '/api/v1/evaluation/query/compare',
    method: 'post',
    response: ({ body }) =>
      ok({
        targetType: 'AGENT',
        targetNum: body?.evalNumA?.startsWith('EVL') ? 'AGT-0001' : 'SKL-0001',
        vA: body?.evalNumA ?? 'EVL-0001',
        vB: body?.evalNumB ?? 'EVL-0002',
        totalScoreDiff: 11,
        dimensionDiffs: [
          { dimension: 'ACCURACY', diff: 4 },
          { dimension: 'RELEVANCE', diff: 6 },
          { dimension: 'COMPLETENESS', diff: -2 },
          { dimension: 'STYLE', diff: 3 },
          { dimension: 'COMPLIANCE', diff: 0 },
        ],
        passRateDiff: 0.14,
        latencyDiff: -200,
      }),
  },
  {
    url: '/api/v1/evaluation/seed/list',
    method: 'get',
    response: ({ query }) => {
      const list = query.skillNum
        ? SEEDS.filter((s) => s.skillNum === query.skillNum)
        : SEEDS;
      return ok(list);
    },
  },
  {
    url: '/api/v1/evaluation/seed/save',
    method: 'post',
    response: () => ok({ num: `EVC-${Date.now().toString().slice(-4)}` }),
  },
  { url: '/api/v1/evaluation/seed/delete', method: 'post', response: () => ok(null) },
  // 写操作
  {
    url: '/api/v1/evaluation/command/createManual',
    method: 'post',
    response: ({ body }) =>
      ok({
        num: `EVL-${Date.now().toString().slice(-4)}`,
        targetType: body?.targetType ?? 'SKILL',
        targetNum: body?.targetNum ?? 'SKL-0001',
        mode: 'MANUAL',
        status: 'PENDING',
        caseCount: 1,
        judgeMethod: body?.judgeMethod ?? 'NONE',
      }),
  },
  {
    url: '/api/v1/evaluation/command/createAuto',
    method: 'post',
    response: () =>
      ok({ num: `EVL-${Date.now().toString().slice(-4)}`, status: 'PENDING' }),
  },
  {
    url: '/api/v1/evaluation/command/rerun',
    method: 'post',
    response: () =>
      ok({ newEvalNum: `EVL-${Date.now().toString().slice(-4)}`, status: 'PENDING' }),
  },
  { url: '/api/v1/evaluation/command/delete', method: 'post', response: () => ok(null) },
] as MockMethod[];
