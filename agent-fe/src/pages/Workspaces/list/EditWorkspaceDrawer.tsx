/**
 * 编辑空间抽屉（PRD §7.4 / §8.3 + 权限管理 v1.0）
 * - 可改：名称 / 描述
 * - 按角色分组选人：列出当前空间所有角色（内置 + 自定义），每个角色一个多选
 * - 保存时自动推导 adminEmpNos / memberEmpNos + 整体覆盖 memberRoles
 * - 创建人必须始终是 space_admin（后端保护，前端兜底补充）
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  Typography,
  message,
} from 'antd';
import MemberSelect from './MemberSelect';
import {
  useWorkspaceDetailQuery,
  useWorkspaceUpdateMutation,
} from '@/services/workspace';
import {
  useMemberRolesQuery,
  useWorkspaceRolesQuery,
} from '@/services/authz';
import { ROLE_SPACE_ADMIN } from '@/types';

const { Text } = Typography;

interface EditWorkspaceDrawerProps {
  num?: string;
  open: boolean;
  onClose: () => void;
}

export default function EditWorkspaceDrawer({
  num,
  open,
  onClose,
}: EditWorkspaceDrawerProps) {
  const [form] = Form.useForm();
  const detailQuery = useWorkspaceDetailQuery(open ? num : undefined);
  const updateMut = useWorkspaceUpdateMutation();
  const rolesQuery = useWorkspaceRolesQuery(open ? num : undefined);
  const memberRolesQuery = useMemberRolesQuery(open ? num : undefined);

  /** roleNum → empNo[]：当前编辑中的角色-成员映射 */
  const [roleMembers, setRoleMembers] = useState<Record<string, string[]>>({});

  const detail = detailQuery.data;
  const allRoles = rolesQuery.data ?? [];

  // 成员 displayName 缓存（从详情 member list 解析）
  const labelMap = useMemo(() => {
    const lm: Record<string, string> = {};
    detail?.members.forEach(m => {
      lm[m.empNo] = m.displayName ?? m.empNo;
    });
    return lm;
  }, [detail]);

  // 详情加载后初始化表单
  useEffect(() => {
    if (!detail) return;
    form.setFieldsValue({ name: detail.name, description: detail.description });
  }, [detail, form]);

  // 后端成员-角色关系 + 角色列表加载后初始化 roleMembers
  useEffect(() => {
    if (!detail || !memberRolesQuery.data || !allRoles.length) return;
    const byRole: Record<string, string[]> = {};
    Object.entries(memberRolesQuery.data).forEach(([empNo, roles]) => {
      roles.forEach(r => {
        if (!byRole[r.roleNum]) byRole[r.roleNum] = [];
        byRole[r.roleNum].push(empNo);
      });
    });
    // 补全 roleMembers 中缺失的角色（角色列表中有但 memberRoles 中无成员的角色）
    allRoles.forEach(r => {
      if (!byRole[r.roleNum]) byRole[r.roleNum] = [];
    });
    setRoleMembers(byRole);
  }, [detail, memberRolesQuery.data, allRoles]);

  /** 角色成员变更处理：管理员角色至少保留 1 人。 */
  const handleRoleMembersChange = (roleNum: string, next: string[]) => {
    if (roleNum === ROLE_SPACE_ADMIN && next.length === 0) {
      message.warning('至少保留 1 名管理员');
      return;
    }
    setRoleMembers(prev => ({ ...prev, [roleNum]: next }));
  };

  const handleSave = async () => {
    const values = await form.validateFields();

    // 从 roleMembers 推导 adminEmpNos（有 RL-SPACE-ADMIN 的成员）
    const adminEmpNos = roleMembers[ROLE_SPACE_ADMIN] ?? [];
    // 创建人保护：如果创建人不在 space_admin 中，自动补入
    const creator = detail?.createNo;
    const finalAdminEmpNos = creator && !adminEmpNos.includes(creator)
      ? [...adminEmpNos, creator]
      : adminEmpNos;

    if (finalAdminEmpNos.length === 0) {
      message.warning('空间至少保留 1 名管理员');
      return;
    }

    // 全部成员去重
    const allEmpNos = Array.from(
      new Set(Object.values(roleMembers).flat())
    );
    const finalMemberEmpNos = allEmpNos.filter(
      empNo => !finalAdminEmpNos.includes(empNo)
    );

    // 推导 memberRoles：empNo → roleNum[]（逆转换 roleMembers）
    const memberRoles: Record<string, string[]> = {};
    Object.entries(roleMembers).forEach(([roleNum, empNos]) => {
      empNos.forEach(empNo => {
        if (!memberRoles[empNo]) memberRoles[empNo] = [];
        memberRoles[empNo].push(roleNum);
      });
    });
    // 确保创建人一定有 space_admin
    if (creator) {
      if (!memberRoles[creator]) memberRoles[creator] = [];
      if (!memberRoles[creator].includes(ROLE_SPACE_ADMIN)) {
        memberRoles[creator].push(ROLE_SPACE_ADMIN);
      }
    }

    try {
      await updateMut.mutateAsync({
        num: num as string,
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
        adminEmpNos: finalAdminEmpNos,
        memberEmpNos: finalMemberEmpNos,
        memberRoles,
      });
      message.success('已保存');
      onClose();
    } catch {
      /* 拦截器统一 toast */
    }
  };

  return (
    <Drawer
      title={detail ? `编辑空间 · ${detail.name}` : '编辑空间'}
      width={680}
      open={open}
      onClose={onClose}
      destroyOnClose
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={updateMut.isPending}
            onClick={handleSave}
          >
            保存
          </Button>
        </div>
      }
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
          <Input maxLength={64} showCount />
        </Form.Item>
        <Form.Item
          name="description"
          label="空间描述"
          rules={[{ max: 200, message: '描述不超过 200 字符' }]}
        >
          <Input.TextArea maxLength={200} showCount rows={3} />
        </Form.Item>
      </Form>

      <div style={{ marginTop: 8, marginBottom: 12 }}>
        <Text strong>分配角色成员</Text>
        <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
          每位成员可按其职责分配一个或多个角色
        </Text>
      </div>

      {allRoles.map(role => (
        <div key={role.roleNum} style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 4 }}>
            <Text strong>{role.name}</Text>
            {role.builtin && (
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 6 }}>
                （内置）
              </Text>
            )}
            {role.roleNum === ROLE_SPACE_ADMIN && detail?.createNo && (
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                创建人必须始终是空间管理员
              </Text>
            )}
          </div>
          <MemberSelect
            value={roleMembers[role.roleNum] ?? []}
            onChange={(next) => handleRoleMembersChange(role.roleNum, next)}
            labelMap={labelMap}
            placeholder={`选择「${role.name}」的成员`}
          />
        </div>
      ))}
    </Drawer>
  );
}