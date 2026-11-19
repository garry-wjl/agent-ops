import { Button, Input, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { createId } from '../storage';
import type { AgentTeam, TeamWorker } from '../types';
import { Field } from '../ui';
import { AgentSelect } from './AgentSelect';
import '../team.css';

const { Text, Title } = Typography;

export default function LeaderEditor({
  team,
  onChange,
}: {
  team: AgentTeam;
  onChange: (team: AgentTeam) => void;
}) {
  const patchWorker = (worker: TeamWorker) => {
    onChange({
      ...team,
      workers: team.workers.map((item) => (item.id === worker.id ? worker : item)),
    });
  };

  return (
    <div style={{ padding: '28px 32px', overflow: 'auto', background: '#F8FAFC', flex: 1 }}>
      <div className="team-org">
        <div className="team-org-leader">
          <article className="team-card team-card-leader">
            <div className="team-card-head">
              <Text className="team-kicker">LEADER</Text>
            </div>
            <Title level={5} style={{ marginTop: 0 }}>{team.leader.agentName || '未绑定 Leader'}</Title>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <Field label="Agent">
                <AgentSelect
                  value={team.leader.agentNum}
                  fallbackName={team.leader.agentName}
                  onChange={(agentNum, agentName) => onChange({
                    ...team,
                    leader: { ...team.leader, agentNum, agentName },
                  })}
                />
              </Field>
              <Field label="分工说明">
                <Input.TextArea
                  rows={4}
                  value={team.leader.instruction}
                  placeholder="Leader 如何拆分任务、如何汇总 Worker 的结果"
                  onChange={(event) => onChange({
                    ...team,
                    leader: { ...team.leader, instruction: event.target.value },
                  })}
                />
              </Field>
            </div>
          </article>
        </div>
        <div className="team-org-stem" />
        <div className="team-org-row">
          {team.workers.map((worker, index) => (
            <article key={worker.id} className="team-card team-card-worker">
              <div className="team-card-head">
                <Text className="team-kicker">WORKER {index + 1}</Text>
                <Button
                  type="link"
                  danger
                  size="small"
                  disabled={team.workers.length <= 1}
                  style={{ padding: 0 }}
                  onClick={() => onChange({
                    ...team,
                    workers: team.workers.filter((item) => item.id !== worker.id),
                  })}
                >
                  移除
                </Button>
              </div>
              <Title level={5} style={{ marginTop: 0 }}>{worker.agentName || `Worker ${index + 1}`}</Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <Field label="Agent">
                  <AgentSelect
                    value={worker.agentNum}
                    fallbackName={worker.agentName}
                    onChange={(agentNum, agentName) => patchWorker({ ...worker, agentNum, agentName })}
                  />
                </Field>
                <Field label="职责">
                  <Input.TextArea
                    rows={3}
                    value={worker.duty}
                    placeholder="这个 Worker 负责什么"
                    onChange={(event) => patchWorker({ ...worker, duty: event.target.value })}
                  />
                </Field>
              </div>
            </article>
          ))}
          <button
            type="button"
            className="team-card team-card-add"
            onClick={() => onChange({
              ...team,
              workers: [...team.workers, { id: createId('W'), agentNum: '', agentName: '', duty: '' }],
            })}
          >
            <PlusOutlined />
            添加 Worker
          </button>
        </div>
      </div>
    </div>
  );
}
