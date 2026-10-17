/**
 * Agent 编辑页「沙箱」独立区块。
 * 放在系统提示词与「工具与上下文」之间，不进 Tab。默认开启。
 * 启用后可配置规格、容器环境变量，以及 Invoke 请求头→环境变量映射。
 */
import { Button, Empty, Input, InputNumber, Space, Switch, Typography } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { ToolsContextValue } from './ToolsContextSection';

const COLOR = {
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  border: '#E2E8F0',
} as const;

/** 新建 Agent 时的沙箱默认值（默认开启）。 */
export const DEFAULT_SANDBOX_SPEC = {
  sandboxEnabled: true,
  sandboxCpu: 0.5,
  sandboxMemoryMb: 1024,
  sandboxAliveMinutes: 10,
  sandboxMaxConcurrent: 8,
  sandboxEnv: [] as SandboxEnvRow[],
  headerEnvInjectEnabled: false,
  headerEnvMappings: [] as HeaderEnvMappingRow[],
} as const;

/** 环境变量编辑行（前端表单态；提交时压成 Record）。 */
export interface SandboxEnvRow {
  key: string;
  value: string;
}

/** 请求头 → 环境变量映射编辑行。 */
export interface HeaderEnvMappingRow {
  headerName: string;
  envName: string;
}

/** 将映射行压成提交用数组；跳过空 header / env。 */
export function headerEnvMappingsToParam(
  rows?: HeaderEnvMappingRow[] | null,
): HeaderEnvMappingRow[] | undefined {
  if (!rows?.length) return undefined;
  const out = rows
    .map((r) => ({
      headerName: r.headerName?.trim() ?? '',
      envName: r.envName?.trim() ?? '',
    }))
    .filter((r) => r.headerName && r.envName);
  return out.length ? out : undefined;
}

export interface SandboxSpecSectionProps {
  value: ToolsContextValue;
  onChange: (next: ToolsContextValue) => void;
}

/** 将 env 对象转为可编辑行；空对象 → 空数组。 */
export function envRecordToRows(
  env?: Record<string, string> | null,
): SandboxEnvRow[] {
  if (!env) return [];
  return Object.entries(env).map(([key, value]) => ({
    key,
    value: value ?? '',
  }));
}

/** 将编辑行压成提交用 Record；跳过空 key。 */
export function envRowsToRecord(
  rows?: SandboxEnvRow[] | null,
): Record<string, string> | undefined {
  if (!rows?.length) return undefined;
  const out: Record<string, string> = {};
  for (const row of rows) {
    const k = row.key?.trim();
    if (!k) continue;
    out[k] = row.value ?? '';
  }
  return Object.keys(out).length ? out : undefined;
}

export default function SandboxSpecSection({
  value,
  onChange,
}: SandboxSpecSectionProps) {
  const patch = (p: Partial<ToolsContextValue>) => onChange({ ...value, ...p });
  const enabled = value.sandboxEnabled !== false;
  const envRows = value.sandboxEnv ?? [];
  const headerInject = Boolean(value.headerEnvInjectEnabled);
  const headerRows = value.headerEnvMappings ?? [];

  const patchEnvRow = (index: number, next: Partial<SandboxEnvRow>) => {
    const rows = envRows.map((r, i) => (i === index ? { ...r, ...next } : r));
    patch({ sandboxEnv: rows });
  };

  const addEnvRow = () => {
    patch({ sandboxEnv: [...envRows, { key: '', value: '' }] });
  };

  const removeEnvRow = (index: number) => {
    patch({ sandboxEnv: envRows.filter((_, i) => i !== index) });
  };

  const patchHeaderRow = (index: number, next: Partial<HeaderEnvMappingRow>) => {
    const rows = headerRows.map((r, i) => (i === index ? { ...r, ...next } : r));
    patch({ headerEnvMappings: rows });
  };

  const addHeaderRow = () => {
    patch({
      headerEnvMappings: [...headerRows, { headerName: '', envName: '' }],
    });
  };

  const removeHeaderRow = (index: number) => {
    patch({ headerEnvMappings: headerRows.filter((_, i) => i !== index) });
  };

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
              sandboxEnv: value.sandboxEnv ?? [],
              headerEnvInjectEnabled: value.headerEnvInjectEnabled ?? false,
              headerEnvMappings: value.headerEnvMappings ?? [],
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
        <>
          <Space wrap size="middle">
            <span>
              CPU（核）{' '}
              <InputNumber
                min={0.5}
                max={16}
                step={0.5}
                value={value.sandboxCpu ?? DEFAULT_SANDBOX_SPEC.sandboxCpu}
                onChange={(v) => patch({ sandboxCpu: Number(v) || 0.5 })}
              />
            </span>
            <span>
              内存（MB）{' '}
              <InputNumber
                min={128}
                max={65536}
                step={128}
                value={value.sandboxMemoryMb ?? DEFAULT_SANDBOX_SPEC.sandboxMemoryMb}
                onChange={(v) => patch({ sandboxMemoryMb: Number(v) || 1024 })}
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
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Typography.Text style={{ color: COLOR.textSecondary, fontSize: 13 }}>
              环境变量
              <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                创建沙箱时注入到容器；敏感值展示时已打码，可点开查看/编辑
              </Typography.Text>
            </Typography.Text>
            {envRows.map((row, index) => (
              <Space key={index} align="start" style={{ width: '100%' }} wrap>
                <Input
                  placeholder="KEY"
                  value={row.key}
                  style={{ width: 200 }}
                  onChange={(e) => patchEnvRow(index, { key: e.target.value })}
                />
                <Input.Password
                  placeholder="VALUE"
                  value={row.value}
                  style={{ width: 320 }}
                  visibilityToggle
                  onChange={(e) => patchEnvRow(index, { value: e.target.value })}
                />
                <Button
                  type="text"
                  danger
                  icon={<MinusCircleOutlined />}
                  onClick={() => removeEnvRow(index)}
                  aria-label="删除环境变量"
                />
              </Space>
            ))}
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              onClick={addEnvRow}
              style={{ width: 200, borderColor: COLOR.border }}
            >
              添加环境变量
            </Button>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Space align="center">
              <span style={{ color: COLOR.textSecondary }}>
                是否将接口请求头转换为环境变量？
              </span>
              <Switch
                checked={headerInject}
                onChange={(checked) =>
                  patch({
                    headerEnvInjectEnabled: checked,
                    headerEnvMappings: value.headerEnvMappings ?? [],
                  })
                }
              />
            </Space>
            {headerInject ? (
              <>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Invoke / 调试调用时，按下列映射把入站请求头写入沙箱环境变量（仅本
                  Agent 生效）
                </Typography.Text>
                {headerRows.map((row, index) => (
                  <Space key={index} align="start" style={{ width: '100%' }} wrap>
                    <Input
                      placeholder="请求头名，如 X-User-Id"
                      value={row.headerName}
                      style={{ width: 240 }}
                      onChange={(e) =>
                        patchHeaderRow(index, { headerName: e.target.value })
                      }
                    />
                    <Input
                      placeholder="环境变量名，如 USER_ID"
                      value={row.envName}
                      style={{ width: 240 }}
                      onChange={(e) =>
                        patchHeaderRow(index, { envName: e.target.value })
                      }
                    />
                    <Button
                      type="text"
                      danger
                      icon={<MinusCircleOutlined />}
                      onClick={() => removeHeaderRow(index)}
                      aria-label="删除请求头映射"
                    />
                  </Space>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={addHeaderRow}
                  style={{ width: 240, borderColor: COLOR.border }}
                >
                  添加映射
                </Button>
              </>
            ) : null}
          </div>
        </>
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
