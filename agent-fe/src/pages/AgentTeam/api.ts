import { get, post } from '@/services/request';
import type { AgentTeam, TeamMode, TeamRun } from './types';

interface TeamVO {
  num: string;
  name: string;
  description?: string;
  mode: TeamMode;
  status: AgentTeam['status'];
  definitionJson?: string;
  updatedAt?: string;
}

interface RunVO {
  num: string;
  teamNum: string;
  teamName: string;
  mode: TeamMode;
  input?: string;
  status: TeamRun['status'];
  ossSubPath?: string;
  snapshotJson?: string;
  runtimeJson?: string;
  createdAt?: string;
}

interface BucketVO {
  teams?: TeamVO[];
  runs?: RunVO[];
}

interface Definition {
  nodes?: AgentTeam['nodes'];
  edges?: AgentTeam['edges'];
  leader?: AgentTeam['leader'];
  workers?: AgentTeam['workers'];
}

const emptyLeader = { agentNum: '', agentName: '', instruction: '' };

function parseDefinition(raw?: string): Definition {
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Definition;
  } catch {
    return {};
  }
}

export function definitionOf(team: AgentTeam) {
  return {
    mode: team.mode,
    nodes: team.nodes,
    edges: team.edges,
    leader: team.leader,
    workers: team.workers,
  };
}

export function toTeam(vo: TeamVO): AgentTeam {
  const definition = parseDefinition(vo.definitionJson);
  return {
    num: vo.num,
    name: vo.name,
    description: vo.description || '',
    mode: vo.mode,
    status: vo.status,
    updatedAt: vo.updatedAt || '',
    nodes: definition.nodes || [],
    edges: definition.edges || [],
    leader: definition.leader || emptyLeader,
    workers: definition.workers || [],
  };
}

export function toRun(vo: RunVO): TeamRun {
  const snapshot = parseDefinition(vo.snapshotJson);
  const runtime = parseDefinition(vo.runtimeJson) as unknown as NonNullable<TeamRun['graph']> & NonNullable<TeamRun['leader']>;
  return {
    num: vo.num,
    teamNum: vo.teamNum,
    teamName: vo.teamName,
    mode: vo.mode,
    input: vo.input || '',
    status: vo.status,
    createdAt: vo.createdAt || '',
    ossSubPath: vo.ossSubPath,
    snapshot: {
      num: vo.teamNum,
      name: vo.teamName,
      description: '',
      mode: vo.mode,
      status: 'PUBLISHED',
      updatedAt: vo.createdAt || '',
      nodes: snapshot.nodes || [],
      edges: snapshot.edges || [],
      leader: snapshot.leader || emptyLeader,
      workers: snapshot.workers || [],
    },
    graph: vo.mode === 'orchestration' ? (runtime as TeamRun['graph']) : undefined,
    leader: vo.mode === 'leader' ? (runtime as TeamRun['leader']) : undefined,
  };
}

export function fetchBucket() {
  return get<BucketVO>('/api/v1/agent-team/query/bucket').then((bucket) => ({
    teams: (bucket.teams || []).map(toTeam),
    runs: (bucket.runs || []).map(toRun),
  }));
}

export function fetchTeam(num: string) {
  return get<TeamVO>('/api/v1/agent-team/query/detail', { num }).then(toTeam);
}

export function fetchRun(num: string) {
  return get<RunVO>('/api/v1/agent-team/query/run', { num }).then(toRun);
}

export function saveTeam(team: AgentTeam) {
  return post<TeamVO>('/api/v1/agent-team/command/save', {
    num: team.num || undefined,
    name: team.name,
    description: team.description,
    mode: team.mode,
    definitionJson: JSON.stringify(definitionOf(team)),
  }).then(toTeam);
}

export function publishTeam(num: string) {
  return post<TeamVO>('/api/v1/agent-team/command/publish', { num }).then(toTeam);
}

export function duplicateTeam(num: string) {
  return post<TeamVO>('/api/v1/agent-team/command/duplicate', { num }).then(toTeam);
}

export function deleteTeam(num: string) {
  return post<void>('/api/v1/agent-team/command/delete', { num });
}

export function startTeamRun(teamNum: string, input: string) {
  return post<RunVO>('/api/v1/agent-team/command/run', { teamNum, input }).then(toRun);
}

export function approveGate(runNum: string, nodeId: string, comment: string) {
  return post<RunVO>('/api/v1/agent-team/command/approve', { runNum, nodeId, comment }).then(toRun);
}

export function rejectGate(runNum: string, nodeId: string, comment: string) {
  return post<RunVO>('/api/v1/agent-team/command/reject', { runNum, nodeId, comment }).then(toRun);
}

export function cancelRun(num: string) {
  return post<RunVO>('/api/v1/agent-team/command/cancel', { num }).then(toRun);
}
