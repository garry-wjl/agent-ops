/**
 * 新建 Agent 方式选择器
 *
 * v2.6 引入：「新建 Agent」入口先弹出此 Modal，让用户在两种创建方式之间二选一：
 *   - CONFIG 模式：本平台从零搭建（5 步表单）
 *   - A2A 模式：接入已注册到 Nacos 的远端 Agent（A2A 接入表单）
 *
 * 选中后回调 onPick('CONFIG' | 'A2A')，由父组件决定后续 Drawer / Modal 行为。
 */
import { Modal, Typography } from 'antd';
import {
  ApiOutlined,
  AppstoreAddOutlined,
} from '@ant-design/icons';

const { Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  borderHover: '#2563EB',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  bgConfig: '#EFF6FF',
  bgA2a: '#F0FDF4',
  iconConfig: '#2563EB',
  iconA2a: '#16A34A',
} as const;

export interface CreateMethodPickerModalProps {
  open: boolean;
  onClose: () => void;
  onPick: (mode: 'CONFIG' | 'A2A') => void;
}

export default function CreateMethodPickerModal({
  open,
  onClose,
  onPick,
}: CreateMethodPickerModalProps) {
  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="选择创建方式"
      footer={null}
      width={680}
      destroyOnHidden
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 16,
          marginTop: 8,
        }}
      >
        <PickerCard
          title="配置模式"
          subtitle="在平台从零搭建 Agent，配置模型、Skills、MCP 与高级参数"
          icon={
            <AppstoreAddOutlined
              style={{ fontSize: 28, color: COLOR.iconConfig }}
            />
          }
          iconBg={COLOR.bgConfig}
          recommendedTag="平台标准模式"
          onClick={() => onPick('CONFIG')}
        />
        <PickerCard
          title="A2A 模式"
          subtitle="接入已注册到 Nacos 的远端 Agent，平台仅做订阅与同步"
          icon={
            <ApiOutlined style={{ fontSize: 28, color: COLOR.iconA2a }} />
          }
          iconBg={COLOR.bgA2a}
          recommendedTag="远端接入"
          onClick={() => onPick('A2A')}
        />
      </div>
      <div style={{ marginTop: 16 }}>
        <Text style={{ fontSize: 12, color: COLOR.textMuted }}>
          提示：A2A 模式接入完成后，AgentCard / Skills / MCP 由远端管理，
          平台仅展示与转发；如需在平台内编辑请选择"配置模式"。
        </Text>
      </div>
    </Modal>
  );
}

interface PickerCardProps {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  iconBg: string;
  recommendedTag?: string;
  onClick: () => void;
}

function PickerCard({
  title,
  subtitle,
  icon,
  iconBg,
  recommendedTag,
  onClick,
}: PickerCardProps) {
  return (
    <div
      onClick={onClick}
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 12,
        padding: 20,
        cursor: 'pointer',
        transition: 'all 0.15s',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        background: '#fff',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = COLOR.borderHover;
        e.currentTarget.style.boxShadow = '0 2px 8px rgba(37, 99, 235, 0.08)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = COLOR.border;
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      <div
        style={{
          width: 48,
          height: 48,
          borderRadius: 10,
          background: iconBg,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {icon}
      </div>
      <div>
        <div
          style={{
            fontSize: 16,
            fontWeight: 600,
            color: COLOR.textPrimary,
            marginBottom: 4,
          }}
        >
          {title}
          {recommendedTag ? (
            <span
              style={{
                marginLeft: 8,
                fontSize: 11,
                fontWeight: 500,
                color: COLOR.textMuted,
                background: '#F1F5F9',
                padding: '1px 6px',
                borderRadius: 4,
              }}
            >
              {recommendedTag}
            </span>
          ) : null}
        </div>
        <div
          style={{
            fontSize: 13,
            color: COLOR.textSecondary,
            lineHeight: '20px',
          }}
        >
          {subtitle}
        </div>
      </div>
    </div>
  );
}
