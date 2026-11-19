/** Agent Team。定义存在服务端，每次运行挂同一块 OSS 目录。 */

export type TeamMode = 'orchestration' | 'leader';

export type TeamStatus = 'DRAFT' | 'PUBLISHED';

export type FlowNodeKind = 'agent' | 'gate';

export interface FlowNode {
  id: string;
  kind: FlowNodeKind;
  name: string;
  x: number;
  y: number;
  /** 绑定的已发布 Agent。空字符串表示还没选。 */
  agentNum: string;
  agentName: string;
  /** 空字符串表示跟随当前在线版本。 */
  version: string;
  promptTemplate: string;
  maxAttempts: number;
  /** 失败或人工打回时回到的节点。空字符串表示不打回。 */
  rejectTo: string;
  maxReworks: number;
  /** 人工闸门要确认的事项。 */
  gatePrompt: string;
}

export interface FlowEdge {
  id: string;
  source: string;
  target: string;
}

export interface TeamLeader {
  agentNum: string;
  agentName: string;
  instruction: string;
}

export interface TeamWorker {
  id: string;
  agentNum: string;
  agentName: string;
  duty: string;
}

export interface AgentTeam {
  num: string;
  name: string;
  description: string;
  mode: TeamMode;
  status: TeamStatus;
  updatedAt: string;
  nodes: FlowNode[];
  edges: FlowEdge[];
  leader: TeamLeader;
  workers: TeamWorker[];
}

export type RunStatus = 'running' | 'waiting' | 'succeeded' | 'failed' | 'cancelled';

export type NodeRunStatus =
  | 'pending'
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'waiting_approval';

export interface NodeRuntime {
  status: NodeRunStatus;
  output: string;
  error: string;
  attempt: number;
  reworkCount: number;
}

export interface GraphRuntime {
  status: RunStatus;
  nodes: Record<string, NodeRuntime>;
}

export type LeaderPhase = 'assigning' | 'working' | 'succeeded' | 'cancelled';

export interface LeaderWorkerRuntime {
  status: 'pending' | 'working' | 'succeeded';
  task: string;
  output: string;
}

export interface LeaderRuntime {
  phase: LeaderPhase;
  summary: string;
  workers: Record<string, LeaderWorkerRuntime>;
}

export interface TeamRun {
  num: string;
  teamNum: string;
  teamName: string;
  mode: TeamMode;
  input: string;
  status: RunStatus;
  createdAt: string;
  /** 这次运行的 OSS 前缀，每个 Agent 容器都挂这块目录。 */
  ossSubPath?: string;
  /** 开跑时冻住的团队定义，之后改画布不影响这一次。 */
  snapshot: AgentTeam;
  graph?: GraphRuntime;
  leader?: LeaderRuntime;
}
