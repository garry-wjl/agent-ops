import type { ReactNode } from 'react';
import { Button, Input, InputNumber, Select, Typography } from 'antd';
import { AuditOutlined, RobotOutlined } from '@ant-design/icons';
import type { AgentTeam, FlowNode } from '../types';
import { Field, SectionLabel } from '../ui';
import { upstreamIds } from '../validate';
import { AgentSelect, VersionSelect } from './AgentSelect';
import TeamCanvas from './TeamCanvas';

const { Text, Title } = Typography;

function PaletteItem({
  title,
  hint,
  icon,
  tint,
  payload,
}: {
  title: string;
  hint: string;
  icon: ReactNode;
  tint: string;
  payload: string;
}) {
  return (
    <div
      className="team-palette-item"
      draggable
      onDragStart={(event) => {
        event.dataTransfer.setData('application/x-team-node', payload);
        event.dataTransfer.effectAllowed = 'move';
      }}
    >
      <span className="team-palette-icon" style={{ background: tint }}>{icon}</span>
      <span>
        <div style={{ color: '#0F172B', fontWeight: 600 }}>{title}</div>
        <Text style={{ color: '#90A1B9', fontSize: 12 }}>{hint}</Text>
      </span>
    </div>
  );
}

function Inspector({
  team,
  node,
  onChange,
  onDelete,
}: {
  team: AgentTeam;
  node: FlowNode;
  onChange: (node: FlowNode) => void;
  onDelete: () => void;
}) {
  const upstream = [...upstreamIds(team, node.id)]
    .map((id) => team.nodes.find((item) => item.id === id))
    .filter((item): item is FlowNode => Boolean(item));
  const others = team.nodes.filter((item) => item.id !== node.id);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div>
        <Text className="team-kicker">{node.kind === 'gate' ? '人工闸门' : 'AGENT'}</Text>
        <Title level={5} style={{ margin: '4px 0 0' }}>{node.name || '未命名节点'}</Title>
      </div>
      <Field label="名称">
        <Input value={node.name} onChange={(event) => onChange({ ...node, name: event.target.value })} />
      </Field>
      {node.kind === 'agent' ? (
        <>
          <SectionLabel>绑定</SectionLabel>
          <Field label="Agent">
            <AgentSelect
              value={node.agentNum}
              fallbackName={node.agentName}
              onChange={(agentNum, agentName) => onChange({ ...node, agentNum, agentName, version: '' })}
            />
          </Field>
          <Field label="版本">
            <VersionSelect
              agentNum={node.agentNum}
              value={node.version}
              onChange={(version) => onChange({ ...node, version })}
            />
          </Field>
          <SectionLabel>任务</SectionLabel>
          <Field label="任务说明">
            <Input.TextArea
              rows={5}
              value={node.promptTemplate}
              onChange={(event) => onChange({ ...node, promptTemplate: event.target.value })}
              placeholder="可用 {{run.input}} 和 {{nodes.节点id.output}}"
            />
          </Field>
          {upstream.length > 0 && (
            <div>
              <Text style={{ color: '#45556C', fontSize: 12 }}>插入上游输出</Text>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
                {upstream.map((item) => (
                  <Button
                    key={item.id}
                    size="small"
                    onClick={() => onChange({
                      ...node,
                      promptTemplate: `${node.promptTemplate}\n{{nodes.${item.id}.output}}`.trim(),
                    })}
                  >
                    {item.name}
                  </Button>
                ))}
              </div>
            </div>
          )}
        </>
      ) : (
        <Field label="需要确认什么">
          <Input.TextArea
            rows={4}
            value={node.gatePrompt}
            onChange={(event) => onChange({ ...node, gatePrompt: event.target.value })}
          />
        </Field>
      )}
      <SectionLabel>失败与打回</SectionLabel>
      {node.kind === 'agent' && (
        <Field label="失败重试次数">
          <InputNumber
            min={1}
            max={5}
            value={node.maxAttempts}
            onChange={(value) => onChange({ ...node, maxAttempts: value || 1 })}
            style={{ width: '100%' }}
          />
        </Field>
      )}
      <Field label="打回目标">
        <Select
          allowClear
          placeholder="不打回"
          value={node.rejectTo || undefined}
          options={others.map((item) => ({ value: item.id, label: item.name }))}
          onChange={(rejectTo) => onChange({ ...node, rejectTo: rejectTo || '' })}
          style={{ width: '100%' }}
        />
      </Field>
      <Field label="最多打回次数">
        <InputNumber
          min={0}
          max={5}
          value={node.maxReworks}
          onChange={(value) => onChange({ ...node, maxReworks: value ?? 0 })}
          style={{ width: '100%' }}
        />
      </Field>
      <Button danger type="text" style={{ alignSelf: 'flex-start', paddingInline: 0 }} onClick={onDelete}>删除节点</Button>
    </div>
  );
}

export default function OrchestrationEditor({
  team,
  selectedId,
  onSelect,
  onChange,
}: {
  team: AgentTeam;
  selectedId?: string;
  onSelect: (nodeId: string) => void;
  onChange: (team: AgentTeam) => void;
}) {
  const selected = team.nodes.find((node) => node.id === selectedId);
  const patchNode = (next: FlowNode) => {
    onChange({
      ...team,
      nodes: team.nodes.map((node) => (node.id === next.id ? next : node)),
    });
  };
  const removeNode = (id: string) => {
    onChange({
      ...team,
      nodes: team.nodes.filter((node) => node.id !== id).map((node) => (
        node.rejectTo === id ? { ...node, rejectTo: '' } : node
      )),
      edges: team.edges.filter((edge) => edge.source !== id && edge.target !== id),
    });
  };

  return (
    <div style={{ display: 'flex', minHeight: 0, flex: 1 }}>
      <aside className="team-palette">
        <div className="team-kicker">拖到画布</div>
        <PaletteItem
          title="Agent 节点"
          hint="调用一个已发布 Agent"
          icon={<RobotOutlined style={{ color: '#1677FF' }} />}
          tint="#EFF6FF"
          payload={JSON.stringify({ kind: 'agent' })}
        />
        <PaletteItem
          title="人工闸门"
          hint="暂停，等人确认或打回"
          icon={<AuditOutlined style={{ color: '#D97706' }} />}
          tint="#FFF7ED"
          payload={JSON.stringify({ kind: 'gate' })}
        />
        <div className="team-note">
          从节点右侧拖到下一个节点，连成功依赖。打回在右侧配置，画布上是橙色虚线。
        </div>
      </aside>
      <div style={{ flex: 1, minWidth: 0, background: '#F8FAFC', overflow: 'hidden' }}>
        <TeamCanvas team={team} onChange={onChange} onSelect={onSelect} />
      </div>
      <aside className="team-inspector">
        {selected ? (
          <Inspector
            team={team}
            node={selected}
            onChange={patchNode}
            onDelete={() => removeNode(selected.id)}
          />
        ) : (
          <Text style={{ color: '#90A1B9' }}>选中一个节点后编辑它。</Text>
        )}
      </aside>
    </div>
  );
}
