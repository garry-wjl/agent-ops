import { useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Spin, Tag, Typography, message } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useWorkspaceStore } from '@/stores/workspace';
import { fetchTeam, publishTeam, saveTeam, startTeamRun } from '../api';
import type { AgentTeam } from '../types';
import { validateTeam } from '../validate';
import { TeamStatusTag } from '../ui';
import LeaderEditor from './LeaderEditor';
import OrchestrationEditor from './OrchestrationEditor';
import '../team.css';

const { Text } = Typography;

export default function TeamEditorPage() {
  const { num = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const ws = useWorkspaceStore((state) => state.currentWorkspaceNum) || '';
  const [team, setTeam] = useState<AgentTeam | null>(null);
  const [missing, setMissing] = useState(false);
  const [selectedId, setSelectedId] = useState<string>();
  const [errors, setErrors] = useState<string[]>([]);

  useEffect(() => {
    let alive = true;
    setTeam(null);
    setMissing(false);
    fetchTeam(num)
      .then((found) => {
        if (!alive) return;
        setTeam(found);
        setSelectedId(found.nodes[0]?.id);
        setErrors([]);
      })
      .catch(() => {
        if (alive) setMissing(true);
      });
    return () => {
      alive = false;
    };
  }, [num]);

  if (!team) {
    return (
      <div style={{ padding: 32 }}>
        {missing ? <Text>找不到这个 Team。</Text> : <Spin />}
      </div>
    );
  }

  const persist = (next: AgentTeam, notice: string) => {
    saveTeam(next)
      .then((saved) => {
        setTeam(saved);
        void queryClient.invalidateQueries({ queryKey: ['agent-team', ws] });
        message.success(notice);
      })
      .catch(() => undefined);
  };

  const publish = () => {
    const found = validateTeam(team);
    setErrors(found);
    if (found.length) return;
    saveTeam(team)
      .then((saved) => publishTeam(saved.num))
      .then((saved) => {
        setTeam(saved);
        void queryClient.invalidateQueries({ queryKey: ['agent-team', ws] });
        message.success('已发布');
      })
      .catch((error: Error) => setErrors([error.message || '发布失败']));
  };

  const preview = () => {
    saveTeam(team)
      .then((saved) => {
        setTeam(saved);
        return startTeamRun(saved.num, '请根据这个任务走一遍。');
      })
      .then((run) => navigate(`/agent/team/runs/${run.num}`))
      .catch(() => undefined);
  };

  return (
    <div style={{ height: 'calc(100vh - 56px)', display: 'flex', flexDirection: 'column', background: '#fff', overflow: 'hidden' }}>
      <div className="team-topbar">
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/agent/team')}>返回</Button>
          <div style={{ width: 1, alignSelf: 'stretch', background: '#E2E8F0' }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <Space size={8} wrap>
              <Tag>{team.mode === 'leader' ? 'Leader + Worker' : '编排'}</Tag>
              <TeamStatusTag status={team.status} />
              <Text type="secondary" style={{ fontSize: 12 }}>运行时共用一块 OSS 目录</Text>
            </Space>
            <Input
              variant="borderless"
              value={team.name}
              onChange={(event) => setTeam({ ...team, name: event.target.value })}
              style={{ fontSize: 20, fontWeight: 600, padding: '2px 0 0', color: '#0F172B' }}
            />
            <Input
              variant="borderless"
              value={team.description}
              placeholder="补充这个 Team 用来做什么"
              onChange={(event) => setTeam({ ...team, description: event.target.value })}
              style={{ padding: 0, color: '#45556C' }}
            />
          </div>
          <Space style={{ flex: 'none' }}>
            <Button onClick={preview}>运行</Button>
            <Button onClick={() => persist(team, '草稿已保存')}>保存草稿</Button>
            <Button type="primary" onClick={publish}>发布</Button>
          </Space>
        </div>
        {errors.length > 0 && (
          <Alert
            style={{ marginTop: 12 }}
            type="error"
            showIcon
            message="还不能发布"
            description={errors.join('；')}
          />
        )}
      </div>
      {team.mode === 'leader' ? (
        <LeaderEditor team={team} onChange={setTeam} />
      ) : (
        <OrchestrationEditor
          team={team}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onChange={setTeam}
        />
      )}
    </div>
  );
}
