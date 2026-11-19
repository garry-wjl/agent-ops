import type { AgentTeam, FlowNode } from './types';

const NODE_REF = /\{\{nodes\.([A-Za-z0-9_-]+)\.output\}\}/g;

/** 成功依赖边上的环。打回不参与这条判断。 */
export function hasSuccessCycle(team: AgentTeam): boolean {
  const adj = new Map<string, string[]>();
  for (const edge of team.edges) {
    const next = adj.get(edge.source) ?? [];
    next.push(edge.target);
    adj.set(edge.source, next);
  }
  const color = new Map<string, 0 | 1 | 2>();
  const visit = (id: string): boolean => {
    const mark = color.get(id) ?? 0;
    if (mark === 1) return true;
    if (mark === 2) return false;
    color.set(id, 1);
    for (const next of adj.get(id) ?? []) {
      if (visit(next)) return true;
    }
    color.set(id, 2);
    return false;
  };
  return team.nodes.some((node) => visit(node.id));
}

/** 沿成功边能到达当前节点的上游，不含自己。 */
export function upstreamIds(team: AgentTeam, nodeId: string): Set<string> {
  const reverse = new Map<string, string[]>();
  for (const edge of team.edges) {
    const prev = reverse.get(edge.target) ?? [];
    prev.push(edge.source);
    reverse.set(edge.target, prev);
  }
  const seen = new Set<string>();
  const stack = [...(reverse.get(nodeId) ?? [])];
  while (stack.length) {
    const id = stack.pop();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    for (const prev of reverse.get(id) ?? []) stack.push(prev);
  }
  return seen;
}

function templateIssues(team: AgentTeam, node: FlowNode): string[] {
  const issues: string[] = [];
  const upstream = upstreamIds(team, node.id);
  const template = node.promptTemplate || '';
  for (const match of template.matchAll(NODE_REF)) {
    const ref = match[1];
    const exists = team.nodes.some((item) => item.id === ref);
    if (!exists) {
      issues.push(`「${node.name}」引用了不存在的节点 ${ref}`);
    } else if (!upstream.has(ref)) {
      issues.push(`「${node.name}」只能引用上游节点的输出`);
    }
  }
  return issues;
}

/** 发布前校验。返回可读错误，空数组表示可以通过。 */
export function validateTeam(team: AgentTeam): string[] {
  const errors: string[] = [];
  if (!team.name.trim()) errors.push('请填写 Team 名称');

  if (team.mode === 'leader') {
    if (!team.leader.agentNum) errors.push('请选择 Leader');
    if (team.workers.length === 0) errors.push('至少添加一个 Worker');
    const seen = new Set<string>();
    if (team.leader.agentNum) seen.add(team.leader.agentNum);
    for (const worker of team.workers) {
      if (!worker.agentNum) {
        errors.push(`请为 Worker「${worker.duty.trim() || '未命名'}」选择 Agent`);
        continue;
      }
      if (!worker.duty.trim()) errors.push(`请填写 Worker「${worker.agentName || worker.agentNum}」的职责`);
      if (seen.has(worker.agentNum)) errors.push('Leader 和 Worker 不能重复绑定同一个 Agent');
      seen.add(worker.agentNum);
    }
    if (!team.leader.instruction.trim()) errors.push('请填写 Leader 的分工说明');
    return errors;
  }

  if (team.nodes.length === 0) errors.push('至少添加一个节点');
  if (hasSuccessCycle(team)) errors.push('成功依赖不能成环');
  const ids = new Set(team.nodes.map((node) => node.id));
  for (const node of team.nodes) {
    if (!node.name.trim()) errors.push('节点名称不能为空');
    if (node.kind === 'agent' && !node.agentNum) {
      errors.push(`请为「${node.name || node.id}」选择 Agent`);
    }
    if (node.kind === 'gate' && !node.gatePrompt.trim()) {
      errors.push(`请填写闸门「${node.name || node.id}」要确认的事项`);
    }
    if (node.rejectTo) {
      if (node.rejectTo === node.id || !ids.has(node.rejectTo)) {
        errors.push(`「${node.name}」的打回目标无效`);
      }
    }
    errors.push(...templateIssues(team, node));
  }
  return errors;
}
