import { startGraph, startLeader } from './simulate';
import type { AgentTeam, FlowNode, TeamMode, TeamRun } from './types';

interface Bucket {
  teams: AgentTeam[];
  runs: TeamRun[];
}

const cache = new Map<string, Bucket>();
const listeners = new Set<() => void>();

function storageKey(workspaceNum: string) {
  return `agent-team-preview:${workspaceNum || 'default'}`;
}

function nowIso() {
  return new Date().toISOString();
}

export function createId(prefix: string) {
  return `${prefix}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
}

function emptyLeader() {
  return { agentNum: '', agentName: '', instruction: '把任务拆给 Worker，汇总他们的结果后再回复。' };
}

function seed(): Bucket {
  const plan: FlowNode = {
    id: 'plan',
    kind: 'agent',
    name: '规划',
    x: 48,
    y: 140,
    agentNum: '',
    agentName: '',
    version: '',
    promptTemplate: '{{run.input}}',
    maxAttempts: 1,
    rejectTo: '',
    maxReworks: 1,
    gatePrompt: '',
  };
  const build: FlowNode = {
    id: 'build',
    kind: 'agent',
    name: '实现',
    x: 340,
    y: 140,
    agentNum: '',
    agentName: '',
    version: '',
    promptTemplate: '{{run.input}}\n\n规划结果：\n{{nodes.plan.output}}',
    maxAttempts: 2,
    rejectTo: 'plan',
    maxReworks: 1,
    gatePrompt: '',
  };
  const accept: FlowNode = {
    id: 'accept',
    kind: 'gate',
    name: '人工确认',
    x: 640,
    y: 140,
    agentNum: '',
    agentName: '',
    version: '',
    promptTemplate: '',
    maxAttempts: 1,
    rejectTo: 'build',
    maxReworks: 2,
    gatePrompt: '确认实现结果是否可以交付。',
  };
  const deliver: FlowNode = {
    id: 'deliver',
    kind: 'agent',
    name: '交付',
    x: 940,
    y: 140,
    agentNum: '',
    agentName: '',
    version: '',
    promptTemplate: '请根据已确认的结果写交付说明。\n{{nodes.build.output}}',
    maxAttempts: 1,
    rejectTo: '',
    maxReworks: 0,
    gatePrompt: '',
  };
  const flow: AgentTeam = {
    num: 'TEAM-DEMO-FLOW',
    name: '需求交付编排',
    description: '规划、实现，人工确认后再交付。打回会回到实现。',
    mode: 'orchestration',
    status: 'DRAFT',
    updatedAt: nowIso(),
    nodes: [plan, build, accept, deliver],
    edges: [
      { id: 'e-plan-build', source: 'plan', target: 'build' },
      { id: 'e-build-accept', source: 'build', target: 'accept' },
      { id: 'e-accept-deliver', source: 'accept', target: 'deliver' },
    ],
    leader: emptyLeader(),
    workers: [],
  };
  const crew: AgentTeam = {
    num: 'TEAM-DEMO-LEAD',
    name: '调研小组',
    description: 'Leader 拆任务，Worker 分别完成后再汇总。',
    mode: 'leader',
    status: 'DRAFT',
    updatedAt: nowIso(),
    nodes: [],
    edges: [],
    leader: emptyLeader(),
    workers: [
      { id: 'w-research', agentNum: '', agentName: '', duty: '收集资料并列出要点' },
      { id: 'w-write', agentNum: '', agentName: '', duty: '根据要点写成结论' },
    ],
  };
  return { teams: [flow, crew], runs: [] };
}

function read(workspaceNum: string): Bucket {
  const cached = cache.get(workspaceNum);
  if (cached) return cached;
  let bucket = seed();
  try {
    const raw = localStorage.getItem(storageKey(workspaceNum));
    if (raw) {
      const parsed = JSON.parse(raw) as Bucket;
      if (Array.isArray(parsed.teams) && Array.isArray(parsed.runs)) bucket = parsed;
    }
  } catch {
    bucket = seed();
  }
  cache.set(workspaceNum, bucket);
  return bucket;
}

function write(workspaceNum: string, bucket: Bucket) {
  cache.set(workspaceNum, bucket);
  try {
    localStorage.setItem(storageKey(workspaceNum), JSON.stringify(bucket));
  } catch {
    // 本地配额满时仍保留内存里的预览数据。
  }
  listeners.forEach((listener) => listener());
}

export function subscribeTeams(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getBucket(workspaceNum: string) {
  return read(workspaceNum);
}

export function createTeam(workspaceNum: string, mode: TeamMode): AgentTeam {
  const team: AgentTeam = {
    num: createId('TEAM'),
    name: mode === 'leader' ? '未命名 Leader 团队' : '未命名编排',
    description: '',
    mode,
    status: 'DRAFT',
    updatedAt: nowIso(),
    nodes: [],
    edges: [],
    leader: emptyLeader(),
    workers: mode === 'leader' ? [{ id: createId('W'), agentNum: '', agentName: '', duty: '' }] : [],
  };
  const bucket = read(workspaceNum);
  write(workspaceNum, { ...bucket, teams: [team, ...bucket.teams] });
  return team;
}

export function saveTeam(workspaceNum: string, team: AgentTeam) {
  const bucket = read(workspaceNum);
  const next = { ...team, updatedAt: nowIso() };
  const teams = bucket.teams.some((item) => item.num === team.num)
    ? bucket.teams.map((item) => (item.num === team.num ? next : item))
    : [next, ...bucket.teams];
  write(workspaceNum, { ...bucket, teams });
  return next;
}

export function removeTeam(workspaceNum: string, num: string) {
  const bucket = read(workspaceNum);
  write(workspaceNum, {
    teams: bucket.teams.filter((team) => team.num !== num),
    runs: bucket.runs.filter((run) => run.teamNum !== num),
  });
}

export function duplicateTeam(workspaceNum: string, num: string) {
  const source = read(workspaceNum).teams.find((team) => team.num === num);
  if (!source) return undefined;
  const copy: AgentTeam = {
    ...source,
    num: createId('TEAM'),
    name: `${source.name} 副本`,
    status: 'DRAFT',
    updatedAt: nowIso(),
    nodes: source.nodes.map((node) => ({ ...node })),
    edges: source.edges.map((edge) => ({ ...edge })),
    workers: source.workers.map((worker) => ({ ...worker })),
    leader: { ...source.leader },
  };
  const bucket = read(workspaceNum);
  write(workspaceNum, { ...bucket, teams: [copy, ...bucket.teams] });
  return copy;
}

export function startRun(workspaceNum: string, teamNum: string, input: string): TeamRun | undefined {
  const team = read(workspaceNum).teams.find((item) => item.num === teamNum);
  if (!team) return undefined;
  const snapshot: AgentTeam = JSON.parse(JSON.stringify(team)) as AgentTeam;
  const run: TeamRun = {
    num: createId('RUN'),
    teamNum: team.num,
    teamName: team.name,
    mode: team.mode,
    input,
    status: team.mode === 'leader' ? 'running' : startGraph(team).status,
    createdAt: nowIso(),
    snapshot,
    graph: team.mode === 'orchestration' ? startGraph(snapshot) : undefined,
    leader: team.mode === 'leader' ? startLeader(snapshot) : undefined,
  };
  const bucket = read(workspaceNum);
  write(workspaceNum, { ...bucket, runs: [run, ...bucket.runs] });
  return run;
}

export function saveRun(workspaceNum: string, run: TeamRun) {
  const bucket = read(workspaceNum);
  write(workspaceNum, {
    ...bucket,
    runs: bucket.runs.map((item) => (item.num === run.num ? run : item)),
  });
}
