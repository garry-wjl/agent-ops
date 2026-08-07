/**
 * 新建 / 编辑角色 Drawer。
 * - 字段：name * / description / permissionCodes *
 * - 权限勾选面板按资源域分组（PermissionCheckPanel）
 */
import { useEffect } from 'react';
import { Drawer, Form, Input, Space, Button, message } from 'antd';
import {
  useCreateRoleMutation,
  useUpdateRoleMutation,
  useRoleDetailQuery,
} from '@/services/authz';
import PermissionCheckPanel from './PermissionCheckPanel';

interface Props {
  open: boolean;
  onClose: () => void;
  /** 编辑场景传入；新建场景为 undefined */
  roleNum?: string;
  /** 当前空间编号（用于 invalidate query） */
  workspaceNum?: string;
  onSaved?: () => void;
}

export default function RoleFormDrawer({
  open,
  onClose,
  roleNum,
  workspaceNum,
  onSaved,
}: Props) {
  const [form] = Form.useForm<{
    name: string;
    description?: string;
    permissionCodes: string[];
  }>();
  const isEdit = Boolean(roleNum);
  const { data: detail } = useRoleDetailQuery(roleNum);
  const createMut = useCreateRoleMutation(workspaceNum);
  const updateMut = useUpdateRoleMutation(workspaceNum);

  useEffect(() => {
    if (!open) return;
    if (isEdit && detail) {
      form.setFieldsValue({
        name: detail.name,
        description: detail.description,
        permissionCodes: detail.permissionGroups
          .flatMap((g) => g.permissions)
          .filter((p) => p.selected)
          .map((p) => p.code),
      });
    } else if (!isEdit) {
      form.resetFields();
    }
  }, [open, isEdit, detail, form]);

  const submit = async () => {
    const values = await form.validateFields();
    if (!values.permissionCodes?.length) {
      message.error('至少勾选 1 个权限');
      return;
    }
    try {
      if (isEdit && roleNum) {
        await updateMut.mutateAsync({
          roleNum,
          name: values.name,
          description: values.description,
          permissionCodes: values.permissionCodes,
        });
        message.success('角色已更新');
      } else {
        await createMut.mutateAsync({
          name: values.name,
          description: values.description,
          permissionCodes: values.permissionCodes,
        });
        message.success('角色已创建');
      }
      onSaved?.();
      onClose();
    } catch {
      /* axios 拦截器统一 toast */
    }
  };

  const builtin = Boolean(detail?.builtin);
  return (
    <Drawer
      title={isEdit ? '编辑角色' : '新建角色'}
      open={open}
      onClose={onClose}
      width={720}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={createMut.isPending || updateMut.isPending}
            onClick={submit}
            disabled={builtin}
          >
            保存
          </Button>
        </Space>
      }
    >
      <Form layout="vertical" form={form} disabled={builtin}>
        <Form.Item
          name="name"
          label="角色名"
          rules={[
            { required: true, message: '角色名必填' },
            { max: 64, message: '不超过 64 字符' },
          ]}
        >
          <Input placeholder="例：技能审核员" />
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
          <PermissionCheckPanel readOnly={builtin} scope="SPACE" enabled={open} />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
