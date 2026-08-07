/**
 * 一级 · 权限管理 → 角色管理（仅平台角色）。
 * - 列出 scope=PLATFORM 的全部角色（内置 + 自定义）
 * - 「新建角色」：创建平台自定义角色
 * - 「编辑」：自定义角色才可编辑；内置只读「查看」
 * - 「删除」：自定义角色，被绑定时拒绝
 */
import { useState } from 'react';
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  useCreatePlatformRoleMutation,
  useDeletePlatformRoleMutation,
  usePlatformRolesQuery,
  useRoleDetailQuery,
  useUpdatePlatformRoleMutation,
} from '@/services/authz';
import PermissionCheckPanel from '@/pages/Roles/list/PermissionCheckPanel';
import type { RoleSummaryVO } from '@/types';

const { Title, Text, Paragraph } = Typography;

type DrawerMode = 'create' | 'edit' | 'view';

interface FormValues {
  name: string;
  description?: string;
  permissionCodes: string[];
}

export default function PermissionRolesPage() {
  const { data: rows = [], isLoading } = usePlatformRolesQuery();
  const createMut = useCreatePlatformRoleMutation();
  const updateMut = useUpdatePlatformRoleMutation();
  const deleteMut = useDeletePlatformRoleMutation();

  const [drawer, setDrawer] = useState<{ mode: DrawerMode; roleNum?: string } | null>(null);
  const { data: detail, isLoading: detailLoading } = useRoleDetailQuery(drawer?.roleNum);
  const [form] = Form.useForm<FormValues>();

  const openCreate = () => {
    form.resetFields();
    setDrawer({ mode: 'create' });
  };
  const openView = (row: RoleSummaryVO) => setDrawer({ mode: 'view', roleNum: row.roleNum });
  const openEdit = (row: RoleSummaryVO) => setDrawer({ mode: 'edit', roleNum: row.roleNum });
  const close = () => setDrawer(null);

  // detail 加载完毕后回填表单（仅 edit/view）
  if (drawer && (drawer.mode === 'edit' || drawer.mode === 'view') && detail && form.getFieldValue('name') !== detail.name) {
    form.setFieldsValue({
      name: detail.name,
      description: detail.description,
      permissionCodes: detail.permissionGroups
        .flatMap((g) => g.permissions)
        .filter((p) => p.selected)
        .map((p) => p.code),
    });
  }

  const onSubmit = async () => {
    const values = await form.validateFields();
    if (!values.permissionCodes?.length) {
      message.error('至少勾选 1 个权限');
      return;
    }
    try {
      if (drawer?.mode === 'create') {
        await createMut.mutateAsync({
          name: values.name,
          description: values.description,
          permissionCodes: values.permissionCodes,
        });
        message.success('已创建');
      } else if (drawer?.mode === 'edit' && drawer.roleNum) {
        await updateMut.mutateAsync({
          roleNum: drawer.roleNum,
          name: values.name,
          description: values.description,
          permissionCodes: values.permissionCodes,
        });
        message.success('已保存');
      }
      close();
    } catch {
      /* 拦截器统一 toast */
    }
  };

  const onDelete = (row: RoleSummaryVO) => {
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
          /* 拦截器统一 toast */
        }
      },
    });
  };

  const columns: NonNullable<TableProps<RoleSummaryVO>['columns']> = [
    {
      title: '角色',
      dataIndex: 'name',
      width: 220,
      render: (name: string, row: RoleSummaryVO) => (
        <Space size={4}>
          <Text strong>{name}</Text>
          {row.builtin ? <Tag color="blue">内置</Tag> : <Tag>自定义</Tag>}
        </Space>
      ),
    },
    {
      title: '角色编号',
      dataIndex: 'roleNum',
      width: 220,
      render: (v: string) => <Text type="secondary">{v}</Text>,
    },
    {
      title: '权限数',
      dataIndex: 'permissionCount',
      width: 100,
      align: 'center',
    },
    {
      title: '绑定用户数',
      dataIndex: 'assignedUserCount',
      width: 120,
      align: 'center',
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (d?: string) => d || <Text type="secondary">—</Text>,
    },
    {
      title: '操作',
      width: 180,
      align: 'right',
      render: (_v: unknown, row: RoleSummaryVO) => (
        <Space>
          {row.builtin ? (
            <Button type="link" onClick={() => openView(row)}>
              查看
            </Button>
          ) : (
            <>
              <Button type="link" onClick={() => openEdit(row)}>
                编辑
              </Button>
              <Button type="link" danger onClick={() => onDelete(row)}>
                删除
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  const isEdit = drawer?.mode === 'edit';
  const isView = drawer?.mode === 'view';
  const isCreate = drawer?.mode === 'create';

  return (
    <Card style={{ margin: 16 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={5} style={{ margin: 0 }}>
          平台角色管理
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建平台角色
        </Button>
      </Space>
      <Paragraph type="secondary">
        本页用于管理平台级角色（跨所有空间生效）。内置 platform_admin 不可修改 / 删除。
        空间级角色请进入对应空间的「空间角色」页面维护。
      </Paragraph>
      <Table<RoleSummaryVO>
        rowKey="roleNum"
        loading={isLoading}
        dataSource={rows}
        columns={columns}
        pagination={false}
      />

      <Drawer
        title={
          isCreate
            ? '新建平台角色'
            : isEdit
              ? `编辑 · ${detail?.name ?? ''}`
              : `查看 · ${detail?.name ?? ''}`
        }
        open={Boolean(drawer)}
        onClose={close}
        width={720}
        destroyOnClose
        afterOpenChange={(o) => {
          if (!o) form.resetFields();
        }}
        extra={
          !isView && (
            <Space>
              <Button onClick={close}>取消</Button>
              <Button
                type="primary"
                loading={createMut.isPending || updateMut.isPending}
                onClick={onSubmit}
              >
                保存
              </Button>
            </Space>
          )
        }
      >
        {(isEdit || isView) && detailLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin />
          </div>
        ) : (
          <Form layout="vertical" form={form} disabled={isView}>
            <Form.Item
              name="name"
              label="角色名"
              rules={[
                { required: true, message: '角色名必填' },
                { max: 64, message: '不超过 64 字符' },
              ]}
            >
              <Input placeholder="例：审计员" />
            </Form.Item>
            <Form.Item
              name="description"
              label="描述"
              rules={[{ max: 200, message: '不超过 200 字符' }]}
            >
              <Input.TextArea rows={3} showCount maxLength={200} />
            </Form.Item>
            <Form.Item
              name="permissionCodes"
              label="权限勾选"
              rules={[{ required: true, message: '至少勾选 1 个权限' }]}
              valuePropName="value"
            >
              <PermissionCheckPanel readOnly={isView} scope="PLATFORM" />
            </Form.Item>
          </Form>
        )}
      </Drawer>
    </Card>
  );
}
