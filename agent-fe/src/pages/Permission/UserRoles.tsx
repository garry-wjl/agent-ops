/**
 * 一级 · 权限管理 → 用户角色。
 * <p>仅 platform_admin 可见；列出已绑定平台角色的工号，支持「添加用户角色」与「编辑」覆盖式保存。</p>
 *
 * 交互（按用户决策）：
 * - 点「添加用户角色」：先搜员工 → 选中 → 自动回填该工号已绑定的平台角色 → 多选角色 → 保存
 * - 表格行「编辑」：直接以 empNo 为初值打开同一弹窗，可改可清空
 * - 表格行「解除全部」：直接 saveUserPlatformRoles 传空集合
 * - 一个工号在平台只有"一套"角色（后端覆盖式 bindUser/unbindUser）
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Modal,
  Popconfirm,
  Select,
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
  usePlatformAdminsQuery,
  usePlatformRolesQuery,
  useSaveUserPlatformRolesMutation,
} from '@/services/authz';
import { ROLE_PLATFORM_ADMIN } from '@/types';
import MemberSelect from '@/pages/Workspaces/list/MemberSelect';

const { Title, Text, Paragraph } = Typography;

interface AdminRow {
  empNo: string;
  roleNums: string[];
}

interface EditorState {
  open: boolean;
  /** 编辑模式时为已选中工号；新增模式为 undefined */
  empNo?: string;
  /** 当前选中的角色 num 列表（受控） */
  roleNums: string[];
}

const EMPTY_EDITOR: EditorState = { open: false, empNo: undefined, roleNums: [] };

export default function PermissionUserRolesPage() {
  const { data: adminsMap = {}, isLoading } = usePlatformAdminsQuery();
  const { data: platformRoles = [] } = usePlatformRolesQuery();
  const saveMut = useSaveUserPlatformRolesMutation();

  const rows: AdminRow[] = useMemo(
    () =>
      Object.entries(adminsMap).map(([empNo, roles]) => ({
        empNo,
        roleNums: (roles ?? []).map((r) => r.roleNum),
      })),
    [adminsMap],
  );

  const [editor, setEditor] = useState<EditorState>(EMPTY_EDITOR);

  // 选中工号变化时，从已有平台管理员列表里回填该工号现有角色
  useEffect(() => {
    if (!editor.open || !editor.empNo) return;
    const current = adminsMap[editor.empNo];
    if (current && editor.roleNums.length === 0) {
      setEditor((prev) => ({ ...prev, roleNums: current.map((r) => r.roleNum) }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor.open, editor.empNo, adminsMap]);

  const openAdd = () => setEditor({ open: true, empNo: undefined, roleNums: [] });
  const openEdit = (row: AdminRow) =>
    setEditor({ open: true, empNo: row.empNo, roleNums: row.roleNums });
  const close = () => setEditor(EMPTY_EDITOR);

  const onEmpNoChange = (selected: string[]) => {
    // MemberSelect 是多选；这里限定单选语义：只保留最后选中的工号
    const next = selected.length === 0 ? undefined : selected[selected.length - 1];
    if (!next) {
      setEditor({ open: true, empNo: undefined, roleNums: [] });
      return;
    }
    // 切换工号 → 重置角色集合，由 useEffect 回填新的现有角色
    setEditor({ open: true, empNo: next, roleNums: [] });
  };

  const onSubmit = async () => {
    if (!editor.empNo) {
      message.error('请先选择用户');
      return;
    }
    try {
      await saveMut.mutateAsync({
        empNo: editor.empNo,
        roleNums: editor.roleNums,
      });
      message.success('已保存');
      close();
    } catch {
      /* 拦截器统一 toast */
    }
  };

  const onRemoveAll = async (empNo: string) => {
    try {
      await saveMut.mutateAsync({ empNo, roleNums: [] });
      message.success('已解除');
    } catch {
      /* 拦截器统一 toast */
    }
  };

  const roleOptions = platformRoles.map((r) => ({
    label: r.builtin ? `${r.name}（内置）` : r.name,
    value: r.roleNum,
  }));

  const columns: NonNullable<TableProps<AdminRow>['columns']> = [
    {
      title: '工号',
      dataIndex: 'empNo',
      width: 200,
      render: (v: string) => <Text strong>{v}</Text>,
    },
    {
      title: '平台角色',
      dataIndex: 'roleNums',
      render: (codes: string[]) =>
        codes.length === 0 ? (
          <Text type="secondary">—</Text>
        ) : (
          <Space wrap>
            {codes.map((c) => {
              const meta = platformRoles.find((r) => r.roleNum === c);
              const label = meta?.name ?? (c === ROLE_PLATFORM_ADMIN ? '平台管理员' : c);
              return (
                <Tag color="gold" key={c}>
                  {label}
                </Tag>
              );
            })}
          </Space>
        ),
    },
    {
      title: '操作',
      width: 220,
      align: 'right',
      render: (_v: unknown, row: AdminRow) => (
        <Space>
          <Button type="link" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title="确认解除该工号的所有平台角色？"
            onConfirm={() => onRemoveAll(row.empNo)}
            okText="解除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger>
              解除全部
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const isEdit = Boolean(editor.empNo) && rows.some((r) => r.empNo === editor.empNo);

  return (
    <Card style={{ margin: 16 }}>
      <Space
        style={{
          marginBottom: 16,
          justifyContent: 'space-between',
          width: '100%',
        }}
      >
        <Title level={5} style={{ margin: 0 }}>
          用户角色
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
          添加用户角色
        </Button>
      </Space>
      <Paragraph type="secondary">
        平台角色拥有跨空间生效的能力。每个用户在平台仅持有一套角色（保存即整体覆盖）。
        空间内的成员角色由各空间管理员在「编辑空间」抽屉里维护，不在此页。
      </Paragraph>
      <Table<AdminRow>
        rowKey="empNo"
        loading={isLoading}
        dataSource={rows}
        columns={columns}
        pagination={false}
      />

      <Modal
        title={isEdit ? `编辑用户角色 · ${editor.empNo}` : '添加用户角色'}
        open={editor.open}
        onCancel={close}
        onOk={onSubmit}
        confirmLoading={saveMut.isPending}
        okText="保存"
        cancelText="取消"
        destroyOnClose
        width={560}
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>
            用户 <Text type="danger">*</Text>
          </Text>
          <div style={{ marginTop: 6 }}>
            {isEdit ? (
              <Text>{editor.empNo}</Text>
            ) : (
              <MemberSelect
                value={editor.empNo ? [editor.empNo] : []}
                onChange={onEmpNoChange}
                placeholder="按工号 / 姓名搜索用户"
              />
            )}
            {!isEdit && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                选中后将自动回填该用户当前已有的平台角色。
              </Text>
            )}
          </div>
        </div>

        <div>
          <Text strong>平台角色</Text>
          <div style={{ marginTop: 6 }}>
            {editor.empNo === undefined ? (
              <Text type="secondary">请先选择用户</Text>
            ) : platformRoles.length === 0 ? (
              <Spin />
            ) : (
              <Select
                mode="multiple"
                style={{ width: '100%' }}
                placeholder="可选多个平台角色（清空则保存为：解除所有平台角色）"
                value={editor.roleNums}
                onChange={(next) => setEditor((prev) => ({ ...prev, roleNums: next }))}
                options={roleOptions}
                allowClear
              />
            )}
          </div>
        </div>
      </Modal>
    </Card>
  );
}
