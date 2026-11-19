import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Input, Space, Spin, Typography, message } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import TeamCanvas from '../editor/TeamCanvas';
import { approveGate, cancelRun, fetchRun, rejectGate } from '../api';
import type { NodeRunStatus, TeamRun } from '../types';
import { RunStatusTag } from '../ui';
import '../team.css';

const { Title, Text } = Typography;

export default function TeamRunPage() {
  const { runNum = '' } = useParams();
  const navigate = useNavigate();
  const [selectedId, setSelectedId] = useState<string>();
  const [comment, setComment] = useState('');
  const runQuery = useQuery({
    queryKey: ['agent-team-run', runNum],
    queryFn: () => fetchRun(runNum),
    enabled: Boolean(runNum),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'running' || status === 'waiting' ? 2000 : false;
    },
  });
  const run = runQuery.data ?? null;

  const apply = (next: TeamRun, notice?: string) => {
    runQuery.refetch();
    const current = next.graph?.nodes?.[selectedId ?? '']?.status;
    if (current !== 'running' && current !== 'waiting_approval') {
      const active = next.snapshot.nodes.find((node) => {
        const status = next.graph?.nodes?.[node.id]?.status;
        return status === 'running' || status === 'waiting_approval';
      });
      if (active) setSelectedId(active.id);
    }
    if (notice) message.success(notice);
  };

  if (runQuery.isLoading) {
    return <div style={{ padding: 32 }}><Spin /></div>;
  }
  if (!run) {
    return <div style={{ padding: 32 }}><Text>找不到这次运行。</Text></div>;
  }

  const graph = run.graph;
  const selected = run.snapshot.nodes.find((node) => node.id === selectedId);
  const selectedRuntime = selected && graph?.nodes ? graph.nodes[selected.id] : undefined;

  const runtime: Record<string, NodeRunStatus> = {};
  if (graph) {
    for (const node of run.snapshot.nodes) runtime[node.id] = graph.nodes?.[node.id]?.status ?? 'pending';
  }

  const approve = () => {
    if (!selected) return;
    approveGate(run.num, selected.id, comment || '已通过')
      .then((next) => {
        setComment('');
        apply(next, '已通过');
      })
      .catch(() => undefined);
  };

  const reject = () => {
    if (!selected) return;
    if (!selected.rejectTo) {
      message.warning('这个闸门没有设置打回目标');
      return;
    }
    rejectGate(run.num, selected.id, comment || '请返工')
      .then((next) => {
        setComment('');
        apply(next, '已打回');
      })
      .catch(() => undefined);
  };

  return (
    <div style={{ height: 'calc(100vh - 56px)', display: 'flex', flexDirection: 'column', background: '#fff', overflow: 'hidden' }}>
      <div className="team-topbar" style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start' }}>
        <div style={{ minWidth: 0 }}>
          <Space size={8}>
            <Text className="team-kicker">{run.num}</Text>
            <RunStatusTag status={run.status} />
            <Text type="secondary" style={{ fontSize: 12 }}>{run.ossSubPath || '共享目录准备中'}</Text>
          </Space>
          <Title level={4} style={{ margin: '6px 0 2px' }}>{run.teamName}</Title>
          <Text style={{ color: '#45556C' }}>{run.input}</Text>
        </div>
        <Space style={{ flex: 'none' }}>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/agent/team')}>返回</Button>
          {run.status !== 'cancelled' && run.status !== 'succeeded' && run.status !== 'failed' && (
            <Button onClick={() => { void cancelRun(run.num).then((next) => apply(next, '已取消')); }}>取消</Button>
          )}
        </Space>
      </div>
      {run.mode === 'orchestration' && graph ? (
        <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
          <div style={{ flex: 1, minWidth: 0, background: '#F8FAFC', overflow: 'hidden' }}>
            <TeamCanvas team={run.snapshot} runtime={runtime} readonly onSelect={setSelectedId} />
          </div>
          <aside className="team-inspector">
            {selected && selectedRuntime ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div>
                  <Text className="team-kicker">这一步</Text>
                  <Title level={5} style={{ margin: '4px 0 8px' }}>{selected.name}</Title>
                  <RunStatusTag status={selectedRuntime.status} />
                </div>
                {selectedRuntime.reworkCount > 0 && <Text type="secondary">已打回 {selectedRuntime.reworkCount} 次</Text>}
                {selectedRuntime.error && <Text type="danger">{selectedRuntime.error}</Text>}
                <div className="team-output">{selectedRuntime.output || '还没有输出'}</div>
                {selectedRuntime.status === 'running' && (
                  <Text type="secondary">Agent 正在共享目录里执行，完成后这里会显示输出。</Text>
                )}
                {selectedRuntime.status === 'waiting_approval' && (
                  <>
                    <Input.TextArea rows={3} value={comment} placeholder="审批意见" onChange={(event) => setComment(event.target.value)} />
                    <Space>
                      <Button type="primary" onClick={approve}>通过</Button>
                      <Button onClick={reject}>打回</Button>
                    </Space>
                  </>
                )}
              </div>
            ) : (
              <Text style={{ color: '#90A1B9' }}>点一个节点查看这一步。</Text>
            )}
          </aside>
        </div>
      ) : run.leader && (
        <div style={{ padding: '28px 32px', background: '#F8FAFC', flex: 1, overflow: 'auto' }}>
          <div className="team-org">
            <div className="team-org-leader">
              <article className="team-card team-card-leader">
                <div className="team-card-head">
                  <Text className="team-kicker">LEADER</Text>
                  <RunStatusTag status={run.leader.phase} />
                </div>
                <Title level={5} style={{ marginTop: 0 }}>{run.snapshot.leader.agentName || '未绑定 Leader'}</Title>
                <div className="team-output">{run.leader.summary || run.snapshot.leader.instruction}</div>
              </article>
            </div>
            <div className="team-org-stem" />
            <div className="team-org-row">
              {run.snapshot.workers.map((worker, index) => {
                const state = run.leader?.workers[worker.id];
                return (
                  <article key={worker.id} className="team-card team-card-worker">
                    <div className="team-card-head">
                      <Text className="team-kicker">WORKER {index + 1}</Text>
                      <RunStatusTag status={state?.status || 'pending'} />
                    </div>
                    <Title level={5} style={{ marginTop: 0 }}>{worker.agentName || '未绑定'}</Title>
                    <Text style={{ display: 'block', color: '#45556C' }}>{worker.duty}</Text>
                    {state?.task && <div className="team-output">{state.task}</div>}
                    {state?.output && <div className="team-output">{state.output}</div>}
                  </article>
                );
              })}
            </div>
            {(run.leader.phase === 'assigning' || run.leader.phase === 'working') && (
              <Text type="secondary" style={{ display: 'block', marginTop: 20 }}>
                Leader 和 Worker 正在同一块共享目录里执行。
              </Text>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
