/**
 * Agent 详情页 · 秘钥管理 Tab —— Agent 优化（PRD 更改 1）
 *
 * 仅 CONFIG 模式 Agent 可用：
 * - 列表展示秘钥（仅掩码 keyMasked，绝不含密文 / 明文）
 * - [+ 创建秘钥]：填备注 → 后端系统生成 key → 弹窗一次性回显明文 + [复制]（仅此次可见）
 * - 小眼睛：单条调 query/reveal 解密查看完整明文 + [复制]（登录态 + 后端审计）
 * - 删除：二次确认 → 逻辑删，认证立即失效
 * - 单 Agent ≤ 50（后端 application 层强制；前端在达上限时禁用创建按钮 + 提示）
 */
import { useState } from 'react';
import {
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  CopyOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  agentApi,
  useAgentApiKeyCreateMutation,
  useAgentApiKeyDeleteMutation,
  useAgentApiKeyListQuery,
} from '@/services/agent';
import type { AgentApiKeyVO } from '@/types';
import { formatTime } from '@/utils/format';

const { Text, Paragraph } = Typography;

/** 单 Agent 秘钥数量上限（与后端 §3.2.2 一致） */
const MAX_KEYS = 50;

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  } catch {
    const el = document.createElement('textarea');
    el.value = text;
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    document.body.removeChild(el);
    message.success('已复制');
  }
}

export interface ApiKeyTabProps {
  agentNum: string;
}

export default function ApiKeyTab({ agentNum }: ApiKeyTabProps) {
  const { data: keys, isLoading } = useAgentApiKeyListQuery(agentNum);
  const createMutation = useAgentApiKeyCreateMutation(agentNum);
  const deleteMutation = useAgentApiKeyDeleteMutation(agentNum);

  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{ remark: string }>();
  /** 创建成功 / 小眼睛解密后展示明文的弹窗 */
  const [plainKey, setPlainKey] = useState<{ title: string; key: string } | null>(null);
  /** 正在 reveal 的行 num（按钮 loading） */
  const [revealingNum, setRevealingNum] = useState<string | null>(null);

  const list = keys ?? [];
  const reachedLimit = list.length >= MAX_KEYS;

  const handleCreate = async () => {
    const v = await form.validateFields();
    const created = await createMutation.mutateAsync(v.remark);
    setCreateOpen(false);
    form.resetFields();
    // 明文仅此次回显
    setPlainKey({ title: '秘钥创建成功（请立即复制，仅此次可见）', key: created.key });
  };

  const handleReveal = async (row: AgentApiKeyVO) => {
    setRevealingNum(row.num);
    try {
      const plain = await agentApi.apiKeyReveal(agentNum, row.num);
      setPlainKey({ title: `秘钥明文 — ${row.num}`, key: plain.key });
    } catch {
      message.error('查看失败，请稍后重试');
    } finally {
      setRevealingNum(null);
    }
  };

  const handleDelete = async (row: AgentApiKeyVO) => {
    await deleteMutation.mutateAsync(row.num);
    message.success('已删除，该秘钥认证立即失效');
  };

  const columns: TableColumnsType<AgentApiKeyVO> = [
    { title: '编号', dataIndex: 'num', key: 'num', width: 180 },
    {
      title: '秘钥',
      dataIndex: 'keyMasked',
      key: 'keyMasked',
      render: (masked: string) => (
        <Text
          style={{
            fontFamily:
              'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
          }}
        >
          {masked}
        </Text>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (t?: string) => formatTime(t) || '-',
    },
    {
      title: '最近使用',
      dataIndex: 'lastUsedAt',
      key: 'lastUsedAt',
      width: 180,
      render: (t?: string) => formatTime(t) || '从未使用',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: unknown, row: AgentApiKeyVO) => (
        <Space size="small">
          <Tooltip title="查看明文">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              loading={revealingNum === row.num}
              onClick={() => handleReveal(row)}
            >
              查看
            </Button>
          </Tooltip>
          <Popconfirm
            title="删除该秘钥？"
            description="删除后使用该秘钥的对外调用将立即失效，且不可恢复。"
            okText="删除"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(row)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <Text type="secondary" style={{ fontSize: 12 }}>
          秘钥用于对外调用接口（API 信息 Tab）的 Bearer 认证；系统生成、加密存储、绝不明文落库，
          创建后请立即复制保存。单个 Agent 最多 {MAX_KEYS} 个（当前 {list.length}）。
        </Text>
        <Tooltip title={reachedLimit ? `已达上限 ${MAX_KEYS} 个` : undefined}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={reachedLimit}
            onClick={() => setCreateOpen(true)}
          >
            创建秘钥
          </Button>
        </Tooltip>
      </div>

      <Table<AgentApiKeyVO>
        rowKey="num"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={list}
        pagination={false}
        locale={{ emptyText: <Empty description="暂无秘钥，点击右上角创建" /> }}
      />

      {/* 创建秘钥 Modal */}
      <Modal
        open={createOpen}
        title="创建秘钥"
        okText="创建"
        confirmLoading={createMutation.isPending}
        onOk={handleCreate}
        onCancel={() => {
          setCreateOpen(false);
          form.resetFields();
        }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="备注"
            name="remark"
            rules={[
              { required: true, message: '请填写备注' },
              { max: 100, message: '备注不超过 100 字' },
            ]}
          >
            <Input placeholder="如：周报系统调用" maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 明文回显 Modal（创建成功 / 小眼睛 reveal 共用） */}
      <Modal
        open={!!plainKey}
        title={plainKey?.title}
        footer={[
          <Button
            key="copy"
            type="primary"
            icon={<CopyOutlined />}
            onClick={() => plainKey && copyText(plainKey.key)}
          >
            复制
          </Button>,
          <Button key="close" onClick={() => setPlainKey(null)}>
            关闭
          </Button>,
        ]}
        onCancel={() => setPlainKey(null)}
        destroyOnHidden
      >
        <Paragraph
          copyable={{ text: plainKey?.key }}
          style={{
            margin: 0,
            padding: '10px 12px',
            background: '#F8FAFC',
            border: '1px solid #DBEAFE',
            borderRadius: 6,
            fontFamily:
              'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
            wordBreak: 'break-all',
          }}
        >
          {plainKey?.key}
        </Paragraph>
      </Modal>
    </div>
  );
}
