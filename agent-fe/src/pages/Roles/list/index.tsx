/**
 * 角色管理列表页 `/role/manage`。
 * - 顶部「新建角色」按钮（需 role_manage:create 权限）
 * - 查看/编辑角色详情（需 role_manage:edit 权限）
 * - 删除自定义角色（需 role_manage:delete 权限）
 * - 表格：角色名 / 描述 / 类型（内置/自定义）/ 权限数 / 绑定用户数 / 操作
 * - 内置角色（builtin=true）只读，"详情"打开抽屉；自定义角色可编辑 / 删除
 */
import { useState } from 'react';
import {
  Button,
  Card,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  useDeleteRoleMutation,
  useWorkspaceRolesQuery,
} from '@/services/authz';
import { usePermissions } from '@/providers/AuthProvider';
import { useWorkspaceStore } from '@/stores/workspace';
import type { RoleVO } from '@/types';
import PermissionGate from '@/components/PermissionGate';
import RoleFormDrawer from './RoleFormDrawer';

const { Title, Text, Paragraph } = Typography;

export default function RoleListPage() {
  const workspaceNum = useWorkspaceStore((s) => s.currentWorkspaceNum);
  const { data: roles = [], isLoading } = useWorkspaceRolesQuery(workspaceNum);
  const deleteMut = useDeleteRoleMutation(workspaceNum);
  const { hasAny } = usePermissions();
  const canManage = hasAny('role_manage:create', 'role_manage:edit', 'role_manage:delete');

  const [drawer, setDrawer] = useState<{
    open: boolean;
    roleNum?: string;
  }>({ open: false });

  const onDelete = (row: RoleVO) => {
    if ((row.assignedUserCount ?? 0) > 0) {
      message.error('该角色已被用户绑定，请先解除绑定');
      return;
    }
    Modal.confirm({
      title: `确认删除角色「${row.name}」？`,
      content: '删除后立即生效，且不可恢复。',
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteMut.mutateAsync(row.roleNum);
          message.success('已删除');
        } catch {
          /* axios 拦截器统一 toast */
        }
      },
    });
  };

  const columns: NonNullable<TableProps<RoleVO>['columns']> = [
    {
      title: '角色名',
      dataIndex: 'name',
      width: 200,
      render: (name: string, row: RoleVO) => (
        <Space size={4}>
          <Text strong>{name}</Text>
          {row.builtin ? <Tag color="blue">内置</Tag> : <Tag>自定义</Tag>}
        </Space>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (desc?: string) => desc || <Text type="secondary">—</Text>,
    },
    {
      title: '绑定用户数',
      dataIndex: 'assignedUserCount',
      width: 120,
      align: 'center',
    },
    {
      title: '操作',
      width: 200,
      align: 'right',
      render: (_v: unknown, row: RoleVO) => (
        <Space>
          <Button
            type="link"
            onClick={() =>
              setDrawer({ open: true, roleNum: row.roleNum })
            }
          >
            {row.builtin ? '查看' : '编辑'}
          </Button>
          {!row.builtin && (
<PermissionGate anyOf={['role_manage:delete']}>
              <Button type="link" danger onClick={() => onDelete(row)}>
                删除
              </Button>
            </PermissionGate>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card style={{ margin: 16 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={5} style={{ margin: 0 }}>
          角色管理
        </Title>
        <PermissionGate anyOf={['role_manage:create']}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setDrawer({ open: true })}
          >
            新建角色
          </Button>
        </PermissionGate>
      </Space>
      {!canManage && (
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          仅展示当前空间内可见角色；管理（新建 / 编辑 / 删除）需要角色管理权限。
        </Paragraph>
      )}
      <Table<RoleVO>
        rowKey="roleNum"
        loading={isLoading}
        dataSource={roles}
        columns={columns}
        pagination={false}
      />
      <RoleFormDrawer
        open={drawer.open}
        onClose={() => setDrawer({ open: false })}
        roleNum={drawer.roleNum}
        workspaceNum={workspaceNum}
        onSaved={() => setDrawer({ open: false })}
      />
    </Card>
  );
}
