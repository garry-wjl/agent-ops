/**
 * 新建工作空间 Modal（PRD §7.3 / §8.2 + 权限管理 v1.0）
 * - 字段：空间名称 * / 描述（≤200，字符计数）
 * - 按角色分组选人：从当前空间获取角色列表，每个角色一个多选
 * - 创建人默认勾入「空间管理员」
 * - 无当前空间时，展示内置 2 角色（空间管理员 + 空间成员）作为 fallback
 */
import { useEffect, useMemo, useState } from 'react';
import { Divider, Form, Input, Modal, Typography, message } from 'antd';
import MemberSelect from './MemberSelect';
import { useWorkspaceCreateMutation } from '@/services/workspace';
import { useWorkspaceRolesQuery } from '@/services/authz';
import { useWorkspaceStore } from '@/stores/workspace';
import { useAuth } from '@/providers/AuthProvider';
import { ROLE_SPACE_ADMIN, ROLE_SPACE_MEMBER } from '@/types';
import type { WorkspaceCreateParam } from '@/types';

const { Text } = Typography;

const FALLBACK_ROLES = [
  { roleNum: ROLE_SPACE_ADMIN, name: '空间管理员', builtin: true },
  { roleNum: ROLE_SPACE_MEMBER, name: '空间成员', builtin: true },
];

interface CreateWorkspaceModalProps {
  open: boolean;
  onClose: () => void;
  onCreated?: (num: string) => void;
}

export default function CreateWorkspaceModal({
  open,
  onClose,
  onCreated,
}: CreateWorkspaceModalProps) {
  const [form] = Form.useForm();
  const createMut = useWorkspaceCreateMutation();
  const currentWorkspaceNum = useWorkspaceStore(s => s.currentWorkspaceNum);
  const hasCurrentWorkspace = !!currentWorkspaceNum;
  const rolesQuery = useWorkspaceRolesQuery(open && hasCurrentWorkspace ? currentWorkspaceNum : undefined);
  const { currentUser } = useAuth();

  const allRoles = useMemo(() => {
    if (!open) return [];
    if (hasCurrentWorkspace) return rolesQuery.data ?? [];
    return FALLBACK_ROLES;
  }, [open, hasCurrentWorkspace, rolesQuery.data]);

  const [roleMembers, setRoleMembers] = useState<Record<string, string[]>>({});

  useEffect(() => {
    if (!open || !currentUser?.userId) return;
    if (Object.keys(roleMembers).length > 0) return;
    if (allRoles.length === 0) return;
    const hasAdmin = allRoles.some(r => r.roleNum === ROLE_SPACE_ADMIN);
    if (hasAdmin) {
      setRoleMembers({ [ROLE_SPACE_ADMIN]: [currentUser.userId] });
    }
  }, [open, allRoles, currentUser?.userId, roleMembers]);

  useEffect(() => {
    if (open) {
      form.resetFields();
      setRoleMembers({});
    }
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const param: WorkspaceCreateParam = {
      name: values.name.trim(),
      description: values.description?.trim() || undefined,
      memberRoles: roleMembers,
    };
    const vo = await createMut.mutateAsync(param);
    message.success('空间创建成功');
    onClose();
    onCreated?.(vo.num);
  };

  const isLoading = !hasCurrentWorkspace ? false : rolesQuery.isLoading && allRoles.length === 0;

  /** 默认管理员回显用户名（value 仍为用户编号） */
  const creatorLabelMap = useMemo(() => {
    if (!currentUser?.userId) return {};
    return { [currentUser.userId]: currentUser.userName || currentUser.userId };
  }, [currentUser?.userId, currentUser?.userName]);

  return (
    <Modal
      title="新建工作空间"
      open={open}
      onCancel={onClose}
      onOk={handleOk}
      okText="创建空间"
      cancelText="取消"
      confirmLoading={createMut.isPending}
      destroyOnClose
      maskClosable={false}
    >
      <Form form={form} layout="vertical" requiredMark>
        <Form.Item
          name="name"
          label="空间名称"
          rules={[
            { required: true, message: '请输入空间名称' },
            { max: 64, message: '空间名称不超过 64 字符' },
          ]}
        >
          <Input placeholder="如：AICoding 组" maxLength={64} showCount />
        </Form.Item>

        <Form.Item
          name="description"
          label="空间描述"
          rules={[{ max: 200, message: '描述不超过 200 字符' }]}
        >
          <Input.TextArea
            placeholder="一句话描述这个空间收拢的资源"
            maxLength={200}
            showCount
            rows={3}
          />
        </Form.Item>
      </Form>

      <Divider plain style={{ fontSize: 13, margin: '12px 0 16px' }}>
        分配角色成员
      </Divider>

      {allRoles.map(role => (
        <div key={role.roleNum} style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 4 }}>
            <Text strong>{role.name}</Text>
            {role.builtin && (
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 6 }}>
                （内置）
              </Text>
            )}
            {role.roleNum === ROLE_SPACE_ADMIN && (
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                你将自动成为空间管理员
              </Text>
            )}
          </div>
          <MemberSelect
            value={roleMembers[role.roleNum] ?? []}
            onChange={(next) =>
              setRoleMembers(prev => ({ ...prev, [role.roleNum]: next }))
            }
            labelMap={creatorLabelMap}
            placeholder={`选择「${role.name}」的成员`}
          />
        </div>
      ))}

      {isLoading && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          正在加载角色列表...
        </Text>
      )}
    </Modal>
  );
}