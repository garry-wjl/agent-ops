/**
 * 工作台（一级门户）—— 个人概览（mock）
 * - 顶部欢迎
 * - 统计卡：我的空间 / 我管理的空间 / 成员可见空间数（取自工作空间列表）
 * - 最近使用空间：点击进入工作区
 * - 快捷入口
 * 真实数据待后端「工作台」接口；当前从工作空间列表 + 静态 mock 拼装。
 */
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  Empty,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import {
  AppstoreOutlined,
  PlusOutlined,
  RightOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/providers/AuthProvider';
import { useWorkspaceListQuery } from '@/services/workspace';
import { useWorkspaceStore } from '@/stores/workspace';

const { Title, Text, Paragraph } = Typography;

export default function WorkbenchPage() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { data: list = [] } = useWorkspaceListQuery();
  const setCurrentWorkspace = useWorkspaceStore(s => s.setCurrentWorkspace);

  const stats = useMemo(() => {
    const adminCount = list.filter(w => w.myRole === 'ADMIN').length;
    const createdCount = list.filter(w => w.isCreator).length;
    return { total: list.length, adminCount, createdCount };
  }, [list]);

  // 最近使用空间（mock：取前 3 个）
  const recent = list.slice(0, 3);

  const enter = (num: string) => {
    setCurrentWorkspace(num);
    navigate(`/agent/manage?ws=${encodeURIComponent(num)}`);
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 20 }}>
        <Title level={4} style={{ margin: 0 }}>
          你好，{currentUser?.userName ?? '同学'} 👋
        </Title>
        <Text type="secondary">欢迎回到 AgentOps，从一个工作空间开始你的工作</Text>
      </div>

      {/* 统计 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="我可见的空间" value={stats.total} prefix={<AppstoreOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="我管理的空间" value={stats.adminCount} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="我创建的空间" value={stats.createdCount} />
          </Card>
        </Col>
      </Row>

      {/* 快捷入口 */}
      <Card style={{ marginBottom: 20 }} title="快捷入口">
        <Space wrap>
          <Button
            type="primary"
            icon={<AppstoreOutlined />}
            onClick={() => navigate('/spaces')}
          >
            进入工作空间
          </Button>
          <Button icon={<PlusOutlined />} onClick={() => navigate('/spaces')}>
            新建空间
          </Button>
        </Space>
      </Card>

      {/* 最近使用空间 */}
      <Card
        title="最近使用空间"
        extra={
          <Button type="link" onClick={() => navigate('/spaces')}>
            查看全部 <RightOutlined />
          </Button>
        }
      >
        {recent.length === 0 ? (
          <Empty description="还没有工作空间">
            <Button type="primary" onClick={() => navigate('/spaces')}>
              去创建
            </Button>
          </Empty>
        ) : (
          <Row gutter={[16, 16]}>
            {recent.map(ws => (
              <Col key={ws.num} xs={24} sm={12} md={8}>
                <Card hoverable size="small" onClick={() => enter(ws.num)}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: 6,
                    }}
                  >
                    <Text strong ellipsis title={ws.name}>
                      {ws.name}
                    </Text>
                    <Tag color={ws.myRole === 'ADMIN' ? 'blue' : 'default'} style={{ margin: 0 }}>
                      {ws.myRole === 'ADMIN' ? '管理员' : '成员'}
                    </Tag>
                  </div>
                  <Paragraph
                    type="secondary"
                    ellipsis={{ rows: 1 }}
                    style={{ margin: 0, fontSize: 12 }}
                  >
                    {ws.description || '暂无描述'}
                  </Paragraph>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    <TeamOutlined /> 成员 {ws.memberCount}
                  </Text>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>
    </div>
  );
}
