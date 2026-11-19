import { describe, expect, it } from 'vitest';
import { assignLeader, failNode, finishWorkers, rejectGate, startGraph, startLeader, succeedNode } from './simulate';
import type { AgentTeam } from './types';
import { hasSuccessCycle, validateTeam } from './validate';

function node(partial: Partial<AgentTeam['nodes'][number]> & { id: string; kind: 'agent' | 'gate' }): AgentTeam['nodes'][number] {
  return {
    name: partial.id,
    x: 0,
    y: 0,
    agentNum: partial.kind === 'agent' ? 'AGT-1' : '',
    agentName: '示例',
    version: '',
    promptTemplate: '',
    maxAttempts: 1,
    rejectTo: '',
    maxReworks: 1,
    gatePrompt: partial.kind === 'gate' ? '请确认' : '',
    ...partial,
  };
}

function flow(overrides: Partial<AgentTeam> = {}): AgentTeam {
  return {
    num: 'TEAM-1',
    name: '交付',
    description: '',
    mode: 'orchestration',
    status: 'DRAFT',
    updatedAt: '',
    nodes: [node({ id: 'plan', kind: 'agent' }), node({ id: 'build', kind: 'agent' }), node({ id: 'accept', kind: 'gate', rejectTo: 'build', maxReworks: 2 })],
    edges: [
      { id: 'e1', source: 'plan', target: 'build' },
      { id: 'e2', source: 'build', target: 'accept' },
    ],
    leader: { agentNum: '', agentName: '', instruction: '' },
    workers: [],
    ...overrides,
  };
}

describe('validateTeam', () => {
  it('rejects a success cycle and a template that skips upstream', () => {
    const cyclic = flow({
      edges: [
        { id: 'e1', source: 'plan', target: 'build' },
        { id: 'e2', source: 'build', target: 'plan' },
      ],
    });
    expect(hasSuccessCycle(cyclic)).toBe(true);
    expect(validateTeam(cyclic)).toContain('成功依赖不能成环');

    const skipped = flow({
      nodes: [
        node({ id: 'plan', kind: 'agent' }),
        node({ id: 'build', kind: 'agent', promptTemplate: '{{nodes.accept.output}}' }),
        node({ id: 'accept', kind: 'gate' }),
      ],
    });
    expect(validateTeam(skipped).some((item) => item.includes('上游'))).toBe(true);
  });

  it('requires a leader, workers and distinct agents', () => {
    const team = flow({
      mode: 'leader',
      leader: { agentNum: 'AGT-1', agentName: '领队', instruction: '拆分任务' },
      workers: [{ id: 'w1', agentNum: 'AGT-1', agentName: '领队', duty: '写结论' }],
    });
    expect(validateTeam(team)).toContain('Leader 和 Worker 不能重复绑定同一个 Agent');
  });
});

describe('orchestration runtime', () => {
  it('walks success edges and waits at the gate', () => {
    const team = flow();
    let state = startGraph(team);
    expect(state.nodes.plan.status).toBe('running');
    expect(state.nodes.build.status).toBe('pending');
    state = succeedNode(team, state, 'plan', '计划');
    expect(state.nodes.build.status).toBe('running');
    state = succeedNode(team, state, 'build', '结果');
    expect(state.status).toBe('waiting');
    expect(state.nodes.accept.status).toBe('waiting_approval');
  });

  it('retries before sending work back', () => {
    const team = flow({
      nodes: [
        node({ id: 'plan', kind: 'agent', maxAttempts: 2, rejectTo: '', maxReworks: 0 }),
        node({ id: 'build', kind: 'agent' }),
        node({ id: 'accept', kind: 'gate' }),
      ],
    });
    let state = startGraph(team);
    state = failNode(team, state, 'plan', '第一次失败');
    expect(state.status).toBe('running');
    expect(state.nodes.plan.attempt).toBe(1);
    state = failNode(team, state, 'plan', '第二次失败');
    expect(state.status).toBe('failed');
  });

  it('sends the gate back to the chosen node and resets downstream', () => {
    const team = flow();
    let state = startGraph(team);
    state = succeedNode(team, state, 'plan', '计划');
    state = succeedNode(team, state, 'build', '结果');
    state = rejectGate(team, state, 'accept', '请重做');
    expect(state.status).toBe('running');
    expect(state.nodes.build.status).toBe('running');
    expect(state.nodes.build.reworkCount).toBe(1);
    expect(state.nodes.accept.status).toBe('pending');
  });
});

describe('leader runtime', () => {
  it('assigns work and then summarizes worker results', () => {
    const team = flow({
      mode: 'leader',
      workers: [
        { id: 'w1', agentNum: 'AGT-2', agentName: '调研', duty: '查资料' },
      ],
    });
    const assigned = assignLeader(team, startLeader(team), '了解竞品');
    expect(assigned.phase).toBe('working');
    expect(assigned.workers.w1.task).toContain('了解竞品');
    const done = finishWorkers(team, assigned);
    expect(done.phase).toBe('succeeded');
    expect(done.summary).toContain('调研');
  });
});
