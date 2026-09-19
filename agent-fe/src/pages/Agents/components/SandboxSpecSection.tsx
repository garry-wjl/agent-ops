/**
 * Agent 编辑页「沙箱」独立区块。
 * 放在系统提示词与「工具与上下文」之间，不进 Tab。默认开启。
 */
import { Empty, InputNumber, Space, Switch, Typography } from 'antd';
import type { ToolsContextValue } from './ToolsContextSection';

const COLOR = {
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

/** 新建 Agent 时的沙箱默认值（默认开启）。 */
export const DEFAULT_SANDBOX_SPEC = {
  sandboxEnabled: true,
  sandboxCpu: 1,
  sandboxMemoryMb: 2048,
  sandboxAliveMinutes: 10,
  sandboxMaxConcurrent: 8,
} as const;

export interface SandboxSpecSectionProps {
  value: ToolsContextValue;
  onChange: (next: ToolsContextValue) => void;
}

export default function SandboxSpecSection({
  value,
  onChange,
}: SandboxSpecSectionProps) {
  const patch = (p: Partial<ToolsContextValue>) => onChange({ ...value, ...p });
  const enabled = value.sandboxEnabled !== false;

  return (
    <div style={{ maxWidth: 880, display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Typography.Text type="secondary" style={{ fontSize: 13 }}>
        为本 Agent 独占配置沙箱规格（存元数据）。真正容器在创建会话时启动；可在沙箱管理只读查看。
      </Typography.Text>
      <Space align="center">
        <span style={{ color: COLOR.textSecondary }}>启用沙箱</span>
        <Switch
          checked={enabled}
          onChange={(checked) =>
            patch({
              sandboxEnabled: checked,
              sandboxCpu: value.sandboxCpu ?? DEFAULT_SANDBOX_SPEC.sandboxCpu,
              sandboxMemoryMb:
                value.sandboxMemoryMb ?? DEFAULT_SANDBOX_SPEC.sandboxMemoryMb,
              sandboxAliveMinutes:
                value.sandboxAliveMinutes ?? DEFAULT_SANDBOX_SPEC.sandboxAliveMinutes,
              sandboxMaxConcurrent:
                value.sandboxMaxConcurrent ?? DEFAULT_SANDBOX_SPEC.sandboxMaxConcurrent,
            })
          }
        />
        {value.sandboxRef ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            资产 {value.sandboxRef}
          </Typography.Text>
        ) : null}
      </Space>
      {enabled ? (
        <Space wrap size="middle">
          <span>
            CPU（核）{' '}
            <InputNumber
              min={0.5}
              max={16}
              step={0.5}
              value={value.sandboxCpu ?? DEFAULT_SANDBOX_SPEC.sandboxCpu}
              onChange={(v) => patch({ sandboxCpu: Number(v) || 1 })}
            />
          </span>
          <span>
            内存（MB）{' '}
            <InputNumber
              min={128}
              max={65536}
              step={128}
              value={value.sandboxMemoryMb ?? DEFAULT_SANDBOX_SPEC.sandboxMemoryMb}
              onChange={(v) => patch({ sandboxMemoryMb: Number(v) || 2048 })}
            />
          </span>
          <span>
            存活（分钟）{' '}
            <InputNumber
              min={1}
              max={1440}
              value={value.sandboxAliveMinutes ?? DEFAULT_SANDBOX_SPEC.sandboxAliveMinutes}
              onChange={(v) => patch({ sandboxAliveMinutes: Number(v) || 10 })}
            />
          </span>
          <span>
            最大并发{' '}
            <InputNumber
              min={1}
              max={64}
              value={value.sandboxMaxConcurrent ?? DEFAULT_SANDBOX_SPEC.sandboxMaxConcurrent}
              onChange={(v) => patch({ sandboxMaxConcurrent: Number(v) || 8 })}
            />
          </span>
        </Space>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <span style={{ color: COLOR.textMuted }}>
              未启用沙箱时，Agent 不挂载远程代码执行环境
            </span>
          }
        />
      )}
    </div>
  );
}
