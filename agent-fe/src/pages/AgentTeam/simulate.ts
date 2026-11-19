import type {
  AgentTeam,
  GraphRuntime,
  LeaderRuntime,
  NodeRuntime,
  RunStatus,
} from './types';

function blank(): NodeRuntime {
  return { status: 'pending', output: '', error: '', attempt: 0, reworkCount: 0 };
}

function incoming(team: AgentTeam, id: string): string[] {
  return team.edges.filter((edge) => edge.target === id).map((edge) => edge.source);
}

function descendants(team: AgentTeam, root: string): string[] {
  const adj = new Map<string, string[]>();
  for (const edge of team.edges) {
    const next = adj.get(edge.source) ?? [];
    next.push(edge.target);
    adj.set(edge.source, next);
  }
  const seen = new Set<string>();
  const stack = [...(adj.get(root) ?? [])];
  while (stack.length) {
    const id = stack.pop();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    for (const next of adj.get(id) ?? []) stack.push(next);
  }
  return [...seen];
}

function summarize(team: AgentTeam, nodes: Record<string, NodeRuntime>): RunStatus {
  const list = team.nodes.map((node) => nodes[node.id]).filter(Boolean);
  if (list.some((node) => node.status === 'failed')) return 'failed';
  if (list.some((node) => node.status === 'waiting_approval')) return 'waiting';
  if (list.length > 0 && list.every((node) => node.status === 'succeeded')) return 'succeeded';
  return 'running';
}

function promote(team: AgentTeam, nodes: Record<string, NodeRuntime>): Record<string, NodeRuntime> {
  const next = { ...nodes };
  for (const node of team.nodes) {
    const current = next[node.id];
    if (!current || current.status !== 'pending') continue;
    const ready = incoming(team, node.id).every((id) => next[id]?.status === 'succeeded');
    if (!ready) continue;
    next[node.id] = {
      ...current,
      status: node.kind === 'gate' ? 'waiting_approval' : 'running',
      error: '',
    };
  }
  return next;
}

function finish(team: AgentTeam, nodes: Record<string, NodeRuntime>): GraphRuntime {
  const promoted = promote(team, nodes);
  return { status: summarize(team, promoted), nodes: promoted };
}

/** 没有入边的节点先进入执行或等待审批。 */
export function startGraph(team: AgentTeam): GraphRuntime {
  const nodes: Record<string, NodeRuntime> = {};
  for (const node of team.nodes) nodes[node.id] = blank();
  return finish(team, nodes);
}

/** 把正在执行的 Agent 节点或待审批闸门标成成功，并放开下游。 */
export function succeedNode(
  team: AgentTeam,
  state: GraphRuntime,
  nodeId: string,
  output: string,
): GraphRuntime {
  const current = state.nodes[nodeId];
  if (!current) return state;
  if (current.status !== 'running' && current.status !== 'waiting_approval') return state;
  return finish(team, {
    ...state.nodes,
    [nodeId]: { ...current, status: 'succeeded', output, error: '' },
  });
}

function rework(
  team: AgentTeam,
  state: GraphRuntime,
  fromId: string,
  targetId: string,
  comment: string,
): GraphRuntime {
  const source = team.nodes.find((node) => node.id === fromId);
  const current = state.nodes[fromId];
  const target = state.nodes[targetId];
  if (!source || !current || !target) return state;
  const nextCount = target.reworkCount + 1;
  if (nextCount > (source.maxReworks ?? 0)) {
    return {
      status: 'failed',
      nodes: {
        ...state.nodes,
        [fromId]: { ...current, status: 'failed', error: comment || '打回次数已用完' },
      },
    };
  }
  const resetIds = new Set([targetId, ...descendants(team, targetId), fromId]);
  const nodes = { ...state.nodes };
  for (const id of resetIds) {
    const prev = nodes[id] ?? blank();
    nodes[id] = {
      ...prev,
      status: 'pending',
      error: '',
      attempt: 0,
      reworkCount: id === targetId ? nextCount : prev.reworkCount,
    };
  }
  return finish(team, nodes);
}

/** 失败先重试；重试耗尽后按打回目标回退，否则整次运行失败。 */
export function failNode(
  team: AgentTeam,
  state: GraphRuntime,
  nodeId: string,
  error: string,
): GraphRuntime {
  const node = team.nodes.find((item) => item.id === nodeId);
  const current = state.nodes[nodeId];
  if (!node || !current || current.status !== 'running') return state;
  const attempt = current.attempt + 1;
  if (attempt < (node.maxAttempts ?? 1)) {
    return {
      status: 'running',
      nodes: {
        ...state.nodes,
        [nodeId]: { ...current, attempt, status: 'running', error },
      },
    };
  }
  const failed = {
    ...state,
    nodes: { ...state.nodes, [nodeId]: { ...current, attempt, error } },
  };
  if (node.rejectTo && current.reworkCount < (node.maxReworks ?? 0)) {
    return rework(team, failed, nodeId, node.rejectTo, error);
  }
  return {
    status: 'failed',
    nodes: {
      ...failed.nodes,
      [nodeId]: { ...failed.nodes[nodeId], status: 'failed', error },
    },
  };
}

export function approveGate(
  team: AgentTeam,
  state: GraphRuntime,
  nodeId: string,
  comment: string,
): GraphRuntime {
  return succeedNode(team, state, nodeId, comment || '已通过');
}

export function rejectGate(
  team: AgentTeam,
  state: GraphRuntime,
  nodeId: string,
  comment: string,
): GraphRuntime {
  const node = team.nodes.find((item) => item.id === nodeId);
  const current = state.nodes[nodeId];
  if (!node || !current || current.status !== 'waiting_approval' || !node.rejectTo) return state;
  return rework(team, state, nodeId, node.rejectTo, comment);
}

export function startLeader(team: AgentTeam): LeaderRuntime {
  const workers: LeaderRuntime['workers'] = {};
  for (const worker of team.workers) {
    workers[worker.id] = { status: 'pending', task: '', output: '' };
  }
  return { phase: 'assigning', summary: '', workers };
}

/** Leader 把本次输入按职责分给每个 Worker。 */
export function assignLeader(team: AgentTeam, state: LeaderRuntime, input: string): LeaderRuntime {
  const workers: LeaderRuntime['workers'] = {};
  for (const worker of team.workers) {
    workers[worker.id] = {
      status: 'working',
      task: `${worker.duty || '处理任务'}：${input}`,
      output: '',
    };
  }
  return {
    phase: team.workers.length ? 'working' : 'succeeded',
    summary: state.summary,
    workers,
  };
}

export function finishWorkers(team: AgentTeam, state: LeaderRuntime): LeaderRuntime {
  const workers: LeaderRuntime['workers'] = {};
  const lines: string[] = [];
  for (const worker of team.workers) {
    const current = state.workers[worker.id];
    const output = `${worker.agentName || 'Worker'} 已完成「${worker.duty || '任务'}」`;
    workers[worker.id] = { status: 'succeeded', task: current?.task ?? '', output };
    lines.push(output);
  }
  return {
    phase: 'succeeded',
    summary: lines.join('\n') || '没有 Worker，Leader 直接结束。',
    workers,
  };
}
