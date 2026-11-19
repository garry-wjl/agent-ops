import { useCallback, useEffect, useRef, type DragEvent } from 'react';
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  type Connection,
  type NodeMouseHandler,
  type NodeTypes,
} from '@xyflow/react';
import { message } from 'antd';
import { AuditOutlined, RobotOutlined } from '@ant-design/icons';
import '@xyflow/react/dist/style.css';
import '../team.css';
import type { AgentTeam, FlowNode, NodeRunStatus } from '../types';
import { hasSuccessCycle } from '../validate';

export interface CanvasNodeData extends FlowNode {
  runtimeStatus?: NodeRunStatus;
}

const STATUS_COLOR: Record<NodeRunStatus, string> = {
  pending: '#94A3B8',
  running: '#1677FF',
  succeeded: '#059669',
  failed: '#DC2626',
  waiting_approval: '#D97706',
};

const STATUS_LABEL: Record<NodeRunStatus, string> = {
  pending: '等待',
  running: '进行中',
  succeeded: '已完成',
  failed: '失败',
  waiting_approval: '待审批',
};

const handleStyle = {
  width: 9,
  height: 9,
  background: '#fff',
  border: '2px solid #1677FF',
};

function Card({
  data,
  selected,
}: {
  data: CanvasNodeData;
  selected?: boolean;
}) {
  const gate = data.kind === 'gate';
  const accent = data.runtimeStatus ? STATUS_COLOR[data.runtimeStatus] : gate ? '#D97706' : '#1677FF';
  const subtitle = gate ? data.gatePrompt || '等待确认' : data.agentName || '未绑定 Agent';
  return (
    <div
      style={{
        width: 228,
        padding: '10px 12px 10px 11px',
        borderRadius: 10,
        background: '#fff',
        border: '1px solid #E2E8F0',
        borderLeft: `3px solid ${accent}`,
        boxShadow: selected
          ? '0 0 0 2px rgba(22,119,255,0.22), 0 8px 20px rgba(15,23,43,0.08)'
          : '0 1px 2px rgba(15,23,43,0.06)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          style={{
            width: 22,
            height: 22,
            borderRadius: 6,
            display: 'grid',
            placeItems: 'center',
            background: gate ? '#FFF7ED' : '#EFF6FF',
            color: accent,
            fontSize: 12,
          }}
        >
          {gate ? <AuditOutlined /> : <RobotOutlined />}
        </span>
        <span style={{ color: '#90A1B9', fontSize: 11, letterSpacing: '0.04em' }}>
          {gate ? '人工闸门' : 'AGENT'}
        </span>
      </div>
      <div style={{ color: '#0F172B', fontWeight: 600, marginTop: 8 }}>{data.name}</div>
      <div
        style={{
          color: '#45556C',
          fontSize: 12,
          marginTop: 2,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {subtitle}
      </div>
      {data.runtimeStatus && (
        <div style={{ marginTop: 8, color: accent, fontSize: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 6, height: 6, borderRadius: 99, background: accent }} />
          {STATUS_LABEL[data.runtimeStatus]}
        </div>
      )}
    </div>
  );
}

function AgentCard({ data, selected }: { data: CanvasNodeData; selected?: boolean }) {
  return (
    <>
      <Handle type="target" position={Position.Left} style={handleStyle} />
      <Handle id="rework-in" type="target" position={Position.Top} isConnectable={false} style={{ opacity: 0 }} />
      <Card data={data} selected={selected} />
      <Handle type="source" position={Position.Right} style={handleStyle} />
      <Handle id="rework-out" type="source" position={Position.Bottom} isConnectable={false} style={{ opacity: 0 }} />
    </>
  );
}

function GateCard({ data, selected }: { data: CanvasNodeData; selected?: boolean }) {
  return (
    <>
      <Handle type="target" position={Position.Left} style={handleStyle} />
      <Handle id="rework-in" type="target" position={Position.Top} isConnectable={false} style={{ opacity: 0 }} />
      <Card data={data} selected={selected} />
      <Handle type="source" position={Position.Right} style={handleStyle} />
      <Handle id="rework-out" type="source" position={Position.Bottom} isConnectable={false} style={{ opacity: 0 }} />
    </>
  );
}

const nodeTypes = {
  agent: AgentCard,
  gate: GateCard,
} as unknown as NodeTypes;

function CanvasBody({
  team,
  runtime,
  readonly,
  onChange,
  onSelect,
}: {
  team: AgentTeam;
  runtime?: Record<string, NodeRunStatus>;
  readonly?: boolean;
  onChange?: (team: AgentTeam) => void;
  onSelect?: (nodeId: string) => void;
}) {
  const { screenToFlowPosition, fitView } = useReactFlow();
  const fitted = useRef(false);
  useEffect(() => {
    if (fitted.current) return;
    fitted.current = true;
    const frame = requestAnimationFrame(() => fitView());
    return () => cancelAnimationFrame(frame);
  }, [fitView]);
  const nodes = team.nodes.map((node) => ({
    id: node.id,
    type: node.kind,
    position: { x: node.x, y: node.y },
    data: { ...node, runtimeStatus: runtime?.[node.id] } satisfies CanvasNodeData,
    draggable: !readonly,
    selectable: true,
  }));
  const edges = [
    ...team.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      style: { stroke: '#64748B' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#64748B' },
    })),
    ...team.nodes
      .filter((node) => node.rejectTo)
      .map((node) => ({
        id: `rework-${node.id}`,
        source: node.id,
        target: node.rejectTo,
        sourceHandle: 'rework-out',
        targetHandle: 'rework-in',
        type: 'smoothstep',
        label: '打回',
        labelStyle: { fill: '#D97706', fontSize: 12 },
        style: { stroke: '#D97706', strokeDasharray: '6 4' },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#D97706' },
      })),
  ];

  const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    onSelect?.(node.id);
  }, [onSelect]);

  const onConnect = useCallback((connection: Connection) => {
    if (!onChange || !connection.source || !connection.target) return;
    if (connection.source === connection.target) return;
    if (team.edges.some((edge) => edge.source === connection.source && edge.target === connection.target)) return;
    const next: AgentTeam = {
      ...team,
      edges: [...team.edges, {
        id: `e-${connection.source}-${connection.target}`,
        source: connection.source,
        target: connection.target,
      }],
    };
    if (hasSuccessCycle(next)) {
      message.error('这条依赖会成环');
      return;
    }
    onChange(next);
  }, [onChange, team]);

  const onDragOver = useCallback((event: DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback((event: DragEvent) => {
    event.preventDefault();
    if (!onChange) return;
    const raw = event.dataTransfer.getData('application/x-team-node');
    if (!raw) return;
    const payload = JSON.parse(raw) as { kind: 'agent' | 'gate'; agentNum?: string; agentName?: string };
    const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    const id = `n${Math.random().toString(36).slice(2, 7)}`;
    const created: FlowNode = {
      id,
      kind: payload.kind,
      name: payload.kind === 'gate' ? '人工确认' : payload.agentName || 'Agent',
      x: position.x,
      y: position.y,
      agentNum: payload.agentNum || '',
      agentName: payload.agentName || '',
      version: '',
      promptTemplate: '{{run.input}}',
      maxAttempts: 1,
      rejectTo: '',
      maxReworks: 1,
      gatePrompt: payload.kind === 'gate' ? '请确认上游结果。' : '',
    };
    onChange({ ...team, nodes: [...team.nodes, created] });
    onSelect?.(id);
  }, [onChange, onSelect, screenToFlowPosition, team]);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodeClick={onNodeClick}
      onNodeDragStop={(_event, node) => {
        if (!onChange) return;
        onChange({
          ...team,
          nodes: team.nodes.map((item) => (
            item.id === node.id ? { ...item, x: node.position.x, y: node.position.y } : item
          )),
        });
      }}
      onConnect={readonly ? undefined : onConnect}
      onDragOver={readonly ? undefined : onDragOver}
      onDrop={readonly ? undefined : onDrop}
      nodesDraggable={!readonly}
      nodesConnectable={!readonly}
      elementsSelectable
      deleteKeyCode={null}
      className="team-canvas"
      forceColorMode="light"
      proOptions={{ hideAttribution: true }}
      style={{ background: '#F8FAFC' }}
    >
      <Background variant={BackgroundVariant.Dots} gap={18} color="#CBD5E1" bgColor="#F8FAFC" />
      <Controls showInteractive={false} />
    </ReactFlow>
  );
}

export default function TeamCanvas(props: {
  team: AgentTeam;
  runtime?: Record<string, NodeRunStatus>;
  readonly?: boolean;
  onChange?: (team: AgentTeam) => void;
  onSelect?: (nodeId: string) => void;
}) {
  return (
    <ReactFlowProvider>
      <CanvasBody {...props} />
    </ReactFlowProvider>
  );
}
