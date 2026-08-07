/**
 * 工作空间管理 — 卡片列表页 `/workspace/manage`（PRD §7.2 / §8.1）
 * - 卡片网格展示"我创建 + 我加入"的空间，不分页
 * - 顶部搜索框（前端按 名称 / 描述 过滤）
 * - 新建空间（Modal）/ 编辑（Drawer）/ 删除（确认 Modal）
 * - 角色胶囊（管理员 / 成员）；当前活动空间边框高亮
 * - 仅管理员卡片显示 编辑 / 删除 菜单
 */
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  Dropdown,
  Empty,
  Input,
  Row,
  Spin,
  Tag,
  Typography,
} from 'antd';
import {
  EllipsisOutlined,
  PlusOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useWorkspaceListQuery } from '@/services/workspace';
import { useWorkspaceStore } from '@/stores/workspace';
import type { WorkspaceVO } from '@/types';
import CreateWorkspaceModal from './CreateWorkspaceModal';
import EditWorkspaceDrawer from './EditWorkspaceDrawer';
import DeleteWorkspaceModal from './DeleteWorkspaceModal';

const { Title, Text, Paragraph } = Typography;

export default function WorkspaceListPage() {
  const navigate = useNavigate();
  const { data: list = [], isLoading } = useWorkspaceListQuery();
  const currentWorkspaceNum = useWorkspaceStore(s => s.currentWorkspaceNum);
  const setCurrentWorkspace = useWorkspaceStore(s => s.setCurrentWorkspace);

  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editNum, setEditNum] = useState<string | undefined>();
  const [deleteTarget, setDeleteTarget] = useState<
    { num: string; name: string } | undefined
  >();

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      w =>
        w.name.toLowerCase().includes(kw) ||
        (w.description ?? '').toLowerCase().includes(kw),
    );
  }, [list, keyword]);

  const openEdit = (num: string) => setEditNum(num);
  const closeEdit = () => setEditNum(undefined);

  /** 点击卡片：设为当前空间并进入二级工作区（URL 带空间编号） */
  const enterWorkspace = (num: string) => {
    setCurrentWorkspace(num);
    navigate(`/agent/manage?ws=${encodeURIComponent(num)}`);
  };

  return (
    <div style={{ padding: 24 }}>
      {/* 头部 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0 }}>
            空间管理
          </Title>
          <Text type="secondary">你创建或加入的所有工作空间</Text>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateOpen(true)}
        >
          新建空间
        </Button>
      </div>

      {/* 搜索 */}
      <Input
        allowClear
        prefix={<SearchOutlined />}
        placeholder="搜索空间名称 / 描述…"
        value={keyword}
        onChange={e => setKeyword(e.target.value)}
        style={{ maxWidth: 360, marginBottom: 16 }}
      />

      {/* 列表 */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : filtered.length === 0 ? (
        <Empty
          description={keyword ? '没有匹配的空间' : '还没有工作空间'}
          style={{ padding: 48 }}
        >
          {!keyword && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateOpen(true)}
            >
              新建空间
            </Button>
          )}
        </Empty>
      ) : (
        <Row gutter={[16, 16]}>
          {filtered.map(ws => (
            <Col key={ws.num} xs={24} sm={12} md={8} xl={6}>
              <WorkspaceCard
                ws={ws}
                onEnter={() => enterWorkspace(ws.num)}
                onEdit={() => openEdit(ws.num)}
                onDelete={() =>
                  setDeleteTarget({ num: ws.num, name: ws.name })
                }
              />
            </Col>
          ))}
        </Row>
      )}

      {/* 弹层 */}
      <CreateWorkspaceModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={num => setCurrentWorkspace(num)}
      />
      <EditWorkspaceDrawer
        num={editNum}
        open={!!editNum}
        onClose={closeEdit}
      />
      <DeleteWorkspaceModal
        open={!!deleteTarget}
        workspaceNum={deleteTarget?.num}
        workspaceName={deleteTarget?.name}
        onClose={() => setDeleteTarget(undefined)}
        onDeleted={() => {
          // 删除的是当前活动空间 → 清空，等用户重新选择
          if (deleteTarget?.num === currentWorkspaceNum) {
            setCurrentWorkspace(undefined);
          }
          setDeleteTarget(undefined);
        }}
      />
    </div>
  );
}

function WorkspaceCard({
  ws,
  onEnter,
  onEdit,
  onDelete,
}: {
  ws: WorkspaceVO;
  onEnter: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const isAdmin = ws.myRole === 'ADMIN';

  return (
    <Card
      hoverable
      onClick={onEnter}
      className="ws-card"
      style={{ height: '100%' }}
      styles={{ body: { padding: 16 } }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 8,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <Text
            strong
            ellipsis
            style={{ fontSize: 15 }}
            title={ws.name}
          >
            {ws.name}
          </Text>
        </div>
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 6 }}
          onClick={e => e.stopPropagation()}
        >
          <Tag color={isAdmin ? 'blue' : 'default'} style={{ margin: 0 }}>
            {isAdmin ? '管理员' : '成员'}
          </Tag>
          {isAdmin && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'edit', label: '编辑', onClick: onEdit },
                  {
                    key: 'delete',
                    label: '删除',
                    danger: true,
                    onClick: onDelete,
                  },
                ],
              }}
            >
              <Button type="text" size="small" icon={<EllipsisOutlined />} />
            </Dropdown>
          )}
        </div>
      </div>

      {!ws.isCreator && (
        <Tag color="purple" style={{ marginBottom: 8 }}>
          被邀请加入
        </Tag>
      )}

      <Paragraph
        type="secondary"
        ellipsis={{ rows: 2 }}
        style={{ minHeight: 40, marginBottom: 12, fontSize: 13 }}
      >
        {ws.description || '暂无描述'}
      </Paragraph>

      <Text type="secondary" style={{ fontSize: 12 }}>
        <TeamOutlined /> 成员 {ws.memberCount} · 管理员 {ws.adminCount}
      </Text>
    </Card>
  );
}
