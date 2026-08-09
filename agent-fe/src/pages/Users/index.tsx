/**
 * 一级 · 用户管理：平台用户 CRUD / 启停 / 重置密码 / 平台角色。
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  useCreateUserMutation,
  useDisableUserMutation,
  useEnableUserMutation,
  useResetPasswordMutation,
  useSaveUserPlatformRolesMutation,
  useUpdateUserMutation,
  useUserDetailQuery,
  useUsersPageQuery,
  type UserVO,
} from '@/services/user';
import { usePlatformRolesQuery } from '@/services/authz';
import { usePermissions } from '@/providers/AuthProvider';

const { Title, Text } = Typography;

export default function UsersPage() {
  const { hasAny } = usePermissions();
  const canCreate = hasAny('user_manage:create');
  const canUpdate = hasAny('user_manage:update');
  const canEnable = hasAny('user_manage:enable');
  const canDisable = hasAny('user_manage:disable');
  const canAssign = hasAny('user_manage:assign_role');

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const query = useMemo(
    () => ({ keyword: keyword || undefined, status, pageNo, pageSize }),
    [keyword, status, pageNo, pageSize],
  );
  const { data, isLoading, refetch } = useUsersPageQuery(query);
  const createMut = useCreateUserMutation();
  const updateMut = useUpdateUserMutation();
  const enableMut = useEnableUserMutation();
  const disableMut = useDisableUserMutation();
  const resetMut = useResetPasswordMutation();
  const rolesMut = useSaveUserPlatformRolesMutation();
  const { data: platformRoles = [] } = usePlatformRolesQuery();

  const [createOpen, setCreateOpen] = useState(false);
  const [editNum, setEditNum] = useState<string>();
  const [resetNum, setResetNum] = useState<string>();
  const [rolesNum, setRolesNum] = useState<string>();
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [resetForm] = Form.useForm();
  const [rolesForm] = Form.useForm();

  const detailQ = useUserDetailQuery(rolesNum);

  useEffect(() => {
    if (rolesNum && detailQ.data) {
      rolesForm.setFieldsValue({
        roleNums: detailQ.data.platformRoleNums ?? [],
      });
    }
  }, [rolesNum, detailQ.data, rolesForm]);

  const openEdit = (row: UserVO) => {
    setEditNum(row.num);
    editForm.setFieldsValue({
      username: row.username,
      email: row.email,
      remark: row.remark,
    });
  };

  const openRoles = (row: UserVO) => {
    setRolesNum(row.num);
  };

  const columns: NonNullable<TableProps<UserVO>['columns']> = [
    {
      title: '用户名',
      dataIndex: 'username',
      width: 160,
      render: (v: string) => <Text strong>{v}</Text>,
    },
    { title: '邮箱', dataIndex: 'email', width: 220 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) =>
        s === 'ENABLED' ? (
          <Tag color="success">启用</Tag>
        ) : (
          <Tag>禁用</Tag>
        ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (v?: string) => v || <Text type="secondary">—</Text>,
    },
    {
      title: '操作',
      width: 320,
      fixed: 'right',
      render: (_, row) => (
        <Space wrap size="small">
          {canUpdate ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : null}
          {canUpdate ? (
            <Button type="link" size="small" onClick={() => setResetNum(row.num)}>
              重置密码
            </Button>
          ) : null}
          {canAssign ? (
            <Button type="link" size="small" onClick={() => openRoles(row)}>
              平台角色
            </Button>
          ) : null}
          {row.status === 'ENABLED' && canDisable ? (
            <Popconfirm
              title="确认禁用该用户？"
              onConfirm={async () => {
                await disableMut.mutateAsync(row.num);
                message.success('已禁用');
              }}
            >
              <Button type="link" size="small" danger>
                禁用
              </Button>
            </Popconfirm>
          ) : null}
          {row.status === 'DISABLED' && canEnable ? (
            <Button
              type="link"
              size="small"
              onClick={async () => {
                await enableMut.mutateAsync(row.num);
                message.success('已启用');
              }}
            >
              启用
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            用户管理
          </Title>
          <Text type="secondary">管理平台登录账号、启停与平台角色</Text>
        </div>
        {canCreate ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新建用户
          </Button>
        ) : null}
      </Space>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          allowClear
          placeholder="搜索用户名 / 邮箱"
          style={{ width: 260 }}
          onSearch={(v) => {
            setKeyword(v.trim());
            setPageNo(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          options={[
            { value: 'ENABLED', label: '启用' },
            { value: 'DISABLED', label: '禁用' },
          ]}
          onChange={(v) => {
            setStatus(v);
            setPageNo(1);
          }}
        />
        <Button onClick={() => refetch()}>刷新</Button>
      </Space>

      <Table<UserVO>
        rowKey="num"
        loading={isLoading}
        columns={columns}
        dataSource={data?.list ?? []}
        scroll={{ x: 960 }}
        pagination={{
          current: pageNo,
          pageSize,
          total: data?.total ?? 0,
          showSizeChanger: true,
          onChange: (p, ps) => {
            setPageNo(p);
            setPageSize(ps);
          },
        }}
      />

      <Modal
        title="新建用户"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false);
          createForm.resetFields();
        }}
        onOk={async () => {
          const values = await createForm.validateFields();
          await createMut.mutateAsync(values);
          message.success('已创建');
          setCreateOpen(false);
          createForm.resetFields();
        }}
        confirmLoading={createMut.isPending}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { pattern: /^[A-Za-z0-9._-]+$/, message: '仅允许字母数字及 ._-' },
            ]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: 8, message: '至少 8 位' },
            ]}
          >
            <Input.Password maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea maxLength={512} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑用户"
        open={Boolean(editNum)}
        onCancel={() => setEditNum(undefined)}
        onOk={async () => {
          const values = await editForm.validateFields();
          await updateMut.mutateAsync({ num: editNum!, ...values });
          message.success('已保存');
          setEditNum(undefined);
        }}
        confirmLoading={updateMut.isPending}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true },
              { pattern: /^[A-Za-z0-9._-]+$/, message: '仅允许字母数字及 ._-' },
            ]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ required: true }, { type: 'email' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea maxLength={512} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重置密码"
        open={Boolean(resetNum)}
        onCancel={() => {
          setResetNum(undefined);
          resetForm.resetFields();
        }}
        onOk={async () => {
          const values = await resetForm.validateFields();
          await resetMut.mutateAsync({ num: resetNum!, password: values.password });
          message.success('密码已重置');
          setResetNum(undefined);
          resetForm.resetFields();
        }}
        confirmLoading={resetMut.isPending}
        destroyOnClose
      >
        <Form form={resetForm} layout="vertical">
          <Form.Item
            name="password"
            label="新密码"
            rules={[{ required: true }, { min: 8, message: '至少 8 位' }]}
          >
            <Input.Password maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="平台角色"
        open={Boolean(rolesNum)}
        onCancel={() => {
          setRolesNum(undefined);
          rolesForm.resetFields();
        }}
        onOk={async () => {
          const values = await rolesForm.validateFields();
          await rolesMut.mutateAsync({
            num: rolesNum!,
            roleNums: values.roleNums ?? [],
          });
          message.success('已保存平台角色');
          setRolesNum(undefined);
        }}
        confirmLoading={rolesMut.isPending || detailQ.isLoading}
        destroyOnClose
      >
        <Form form={rolesForm} layout="vertical">
          <Form.Item name="roleNums" label="角色">
            <Select
              mode="multiple"
              options={platformRoles.map((r) => ({
                value: r.roleNum,
                label: r.builtin ? `${r.name}（内置）` : r.name,
              }))}
              placeholder="选择平台角色"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
