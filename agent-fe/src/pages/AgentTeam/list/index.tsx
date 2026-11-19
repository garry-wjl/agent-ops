import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Col, Dropdown, Empty, Input, Modal, Row, Segmented, Space, Spin, Tag, Typography, message } from 'antd';
import { ApartmentOutlined, EllipsisOutlined, PlusOutlined, SearchOutlined, TeamOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useWorkspaceStore } from '@/stores/workspace';
import { deleteTeam, duplicateTeam, fetchBucket, saveTeam, startTeamRun } from '../api';
import type { AgentTeam, TeamMode, TeamRun } from '../types';
import { RunStatusTag, TeamStatusTag } from '../ui';
import '../team.css';

const { Title, Text, Paragraph } = Typography;

function ModeTag({ mode }: { mode: TeamMode }) {
  return mode === 'leader'
    ? <Tag icon={<TeamOutlined />} color="blue" style={{ margin: 0 }}>Leader + Worker</Tag>
    : <Tag icon={<ApartmentOutlined />} style={{ margin: 0 }}>编排</Tag>;
}

export default function TeamListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const ws = useWorkspaceStore((state) => state.currentWorkspaceNum) || '';
  const bucketQuery = useQuery({
    queryKey: ['agent-team', ws],
    queryFn: fetchBucket,
    enabled: Boolean(ws),
  });
  const bucket = bucketQuery.data ?? { teams: [], runs: [] };
  const [keyword, setKeyword] = useState('');
  const [tab, setTab] = useState<'teams' | 'runs'>('teams');
  const [runTeam, setRunTeam] = useState<AgentTeam | null>(null);
  const [runInput, setRunInput] = useState('');
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['agent-team', ws] });

  const teams = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    return bucket.teams.filter((team) => !text || `${team.name} ${team.description}`.toLowerCase().includes(text));
  }, [bucket.teams, keyword]);

  const openRun = (team: AgentTeam) => {
    setRunTeam(team);
    setRunInput('');
  };

  const confirmRun = () => {
    if (!runTeam) return;
    if (!runInput.trim()) {
      message.warning('请填写这次要完成的任务');
      return;
    }
    startTeamRun(runTeam.num, runInput.trim())
      .then((run) => {
        setRunTeam(null);
        void refresh();
        navigate(`/agent/team/runs/${run.num}`);
      })
      .catch(() => undefined);
  };

  const create = (mode: TeamMode) => {
    const team: AgentTeam = {
      num: '',
      name: mode === 'leader' ? '未命名 Leader 团队' : '未命名编排',
      description: '',
      mode,
      status: 'DRAFT',
      updatedAt: '',
      nodes: [],
      edges: [],
      leader: { agentNum: '', agentName: '', instruction: '把任务拆给 Worker，汇总他们的结果后再回复。' },
      workers: mode === 'leader' ? [{ id: `W${Date.now().toString(36)}`, agentNum: '', agentName: '', duty: '' }] : [],
    };
    saveTeam(team)
      .then((saved) => {
        void refresh();
        navigate(`/agent/team/editor/${saved.num}`);
      })
      .catch(() => undefined);
  };

  const remove = (team: AgentTeam) => {
    Modal.confirm({
      title: `删除「${team.name}」？`,
      content: '删除后不能再从这个 Team 开新的运行。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteTeam(team.num).then(() => refresh()),
    });
  };

  if (bucketQuery.isLoading) {
    return <div className="team-page"><Spin /></div>;
  }

  return (
    <div className="team-page">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', marginBottom: 20 }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <Title level={3} style={{ margin: 0, color: '#0F172B' }}>Agent Team</Title>
          <Text style={{ color: '#45556C', display: 'block', marginTop: 6 }}>
            把已发布的 Agent 编成流程，或组成 Leader 和 Worker。每次运行共用一块 OSS 工作目录。
          </Text>
        </div>
        <Dropdown
          menu={{
            items: [
              {
                key: 'orchestration',
                icon: <ApartmentOutlined />,
                label: (
                  <div>
                    <div>编排模式</div>
                    <Text type="secondary" style={{ fontSize: 12 }}>按依赖把多个 Agent 串成流程</Text>
                  </div>
                ),
              },
              {
                key: 'leader',
                icon: <TeamOutlined />,
                label: (
                  <div>
                    <div>Leader + Worker</div>
                    <Text type="secondary" style={{ fontSize: 12 }}>由 Leader 拆任务，Worker 分别完成</Text>
                  </div>
                ),
              },
            ],
            onClick: ({ key }) => create(key as TeamMode),
          }}
        >
          <Button type="primary" icon={<PlusOutlined />} style={{ flex: 'none' }}>新建 Team</Button>
        </Dropdown>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 16 }}>
        <Segmented
          value={tab}
          onChange={(value) => setTab(value as 'teams' | 'runs')}
          options={[
            { label: `团队 ${bucket.teams.length}`, value: 'teams' },
            { label: `运行 ${bucket.runs.length}`, value: 'runs' },
          ]}
        />
        {tab === 'teams' && (
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: '#90A1B9' }} />}
            placeholder="搜索团队名称"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            style={{ width: 260 }}
          />
        )}
      </div>
      {tab === 'teams' ? (
        teams.length === 0 ? (
          <Empty description={keyword ? '没有匹配的团队' : '还没有 Team'} style={{ padding: 48 }}>
            {!keyword && <Text type="secondary">从右上角新建一个编排，或一个 Leader + Worker。</Text>}
          </Empty>
        ) : (
          <Row gutter={[16, 16]}>
            {teams.map((team) => (
              <Col key={team.num} xs={24} sm={12} lg={8} xl={6}>
                <Card
                  hoverable
                  style={{ height: '100%' }}
                  styles={{ body: { padding: 16, height: '100%', display: 'flex', flexDirection: 'column' } }}
                  onClick={() => navigate(`/agent/team/editor/${team.num}`)}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                    <ModeTag mode={team.mode} />
                    <Space size={4} onClick={(event) => event.stopPropagation()}>
                      <TeamStatusTag status={team.status} />
                      <Dropdown
                        trigger={['click']}
                        menu={{
                          items: [
                            { key: 'edit', label: '编辑', onClick: () => navigate(`/agent/team/editor/${team.num}`) },
                            { key: 'copy', label: '复制', onClick: () => { void duplicateTeam(team.num).then(() => refresh()); } },
                            { key: 'delete', label: '删除', danger: true, onClick: () => remove(team) },
                          ],
                        }}
                      >
                        <Button type="text" size="small" icon={<EllipsisOutlined />} />
                      </Dropdown>
                    </Space>
                  </div>
                  <Title level={5} ellipsis style={{ margin: '12px 0 4px' }}>{team.name}</Title>
                  <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ minHeight: 44, marginBottom: 12, fontSize: 13 }}>
                    {team.description || '还没有说明'}
                  </Paragraph>
                  <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>{dayjs(team.updatedAt).format('YYYY-MM-DD HH:mm')}</Text>
                    <Button
                      size="small"
                      onClick={(event) => {
                        event.stopPropagation();
                        openRun(team);
                      }}
                    >
                      运行
                    </Button>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )
      ) : bucket.runs.length === 0 ? (
        <Empty description="还没有运行" style={{ padding: 48 }} />
      ) : (
        <Row gutter={[16, 16]}>
          {bucket.runs.map((run: TeamRun) => (
            <Col key={run.num} xs={24} sm={12} lg={8} xl={6}>
              <Card
                hoverable
                style={{ height: '100%' }}
                styles={{ body: { padding: 16, height: '100%', display: 'flex', flexDirection: 'column' } }}
                onClick={() => navigate(`/agent/team/runs/${run.num}`)}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                  <ModeTag mode={run.mode} />
                  <RunStatusTag status={run.status} />
                </div>
                <Title level={5} ellipsis style={{ margin: '12px 0 4px' }}>{run.teamName}</Title>
                <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ minHeight: 44, marginBottom: 12, fontSize: 13 }}>
                  {run.input || '没有任务说明'}
                </Paragraph>
                <Text type="secondary" style={{ marginTop: 'auto', fontSize: 12 }}>
                  {dayjs(run.createdAt).format('YYYY-MM-DD HH:mm')}
                </Text>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      <Modal
        title={runTeam ? `运行「${runTeam.name}」` : '运行'}
        open={Boolean(runTeam)}
        okText="开始"
        cancelText="取消"
        onOk={confirmRun}
        onCancel={() => setRunTeam(null)}
      >
        <Text style={{ color: '#45556C' }}>这次要完成的任务。服务端会分配一块共享目录，后续每个 Agent 都挂同一块。</Text>
        <Input.TextArea
          style={{ marginTop: 12 }}
          rows={4}
          value={runInput}
          onChange={(event) => setRunInput(event.target.value)}
          placeholder="例如：整理一版发布说明"
        />
      </Modal>
    </div>
  );
}
