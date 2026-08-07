/**
 * MCP 代理与透传请求头编辑器（PRD §7.5）。
 *
 * - 代理 Switch（默认关）；仅启用代理后才显示透传 Header 配置区。
 * - Header 表格化录入：名 / 值 / 描述 + 增删行；上限 20 条。
 * - 值支持字面值或 `{变量名}` 占位；底部内置变量 Popover 一键插入到最近聚焦的「值」单元格。
 * - 失焦实时校验（名去重 + 占位符格式），错误红字提示。
 *
 * 受控组件：proxyEnabled / proxyHeaders 由父编辑器持有，经 onChange 上抛。
 */
import { useState } from "react";
import { Button, Input, Popover, Space, Switch, Table, Typography } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import type { ProxyHeader } from "@/types";
import {
  BUILTIN_VARIABLES,
  TOOL_LIMITS,
  validateProxyHeaders,
} from "../constants";

const { Text } = Typography;

interface ProxyHeadersEditorProps {
  proxyEnabled: boolean;
  proxyHeaders: ProxyHeader[];
  onChange: (next: {
    proxyEnabled: boolean;
    proxyHeaders: ProxyHeader[];
  }) => void;
}

export default function ProxyHeadersEditor({
  proxyEnabled,
  proxyHeaders,
  onChange,
}: ProxyHeadersEditorProps) {
  // 记录最近聚焦的「值」行索引，供内置变量一键插入
  const [activeRow, setActiveRow] = useState<number>(-1);

  const setHeaders = (rows: ProxyHeader[]) =>
    onChange({ proxyEnabled, proxyHeaders: rows });

  const updateRow = (idx: number, patch: Partial<ProxyHeader>) => {
    const rows = proxyHeaders.map((h, i) =>
      i === idx ? { ...h, ...patch } : h,
    );
    setHeaders(rows);
  };

  const addRow = () => {
    if (proxyHeaders.length >= TOOL_LIMITS.PROXY_HEADER_MAX) return;
    setHeaders([...proxyHeaders, { name: "", value: "", description: "" }]);
  };

  const removeRow = (idx: number) =>
    setHeaders(proxyHeaders.filter((_, i) => i !== idx));

  const insertVariable = (varName: string) => {
    const idx = activeRow >= 0 ? activeRow : proxyHeaders.length - 1;
    if (idx < 0) return;
    const cur = proxyHeaders[idx];
    updateRow(idx, { value: `${cur.value ?? ""}{${varName}}` });
  };

  const validation = validateProxyHeaders(proxyHeaders);

  const columns = [
    {
      title: "Header 名",
      dataIndex: "name",
      width: "28%",
      render: (_: unknown, _r: ProxyHeader, idx: number) => (
        <Input
          placeholder="如 Authorization"
          value={proxyHeaders[idx].name}
          onChange={(e) => updateRow(idx, { name: e.target.value })}
        />
      ),
    },
    {
      title: "值",
      dataIndex: "value",
      width: "34%",
      render: (_: unknown, _r: ProxyHeader, idx: number) => (
        <Input
          placeholder="字面值或 {userToken}"
          value={proxyHeaders[idx].value}
          onFocus={() => setActiveRow(idx)}
          onChange={(e) => updateRow(idx, { value: e.target.value })}
        />
      ),
    },
    {
      title: "描述",
      dataIndex: "description",
      render: (_: unknown, _r: ProxyHeader, idx: number) => (
        <Input
          placeholder="可选"
          value={proxyHeaders[idx].description}
          onChange={(e) => updateRow(idx, { description: e.target.value })}
        />
      ),
    },
    {
      title: "操作",
      dataIndex: "op",
      width: 60,
      render: (_: unknown, _r: ProxyHeader, idx: number) => (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={() => removeRow(idx)}
        />
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Text strong>MCP 代理</Text>
        <Switch
          checked={proxyEnabled}
          onChange={(checked) =>
            onChange({
              proxyEnabled: checked,
              // 关闭代理时清空透传 Header（PRD §7.5 / 技术方案代理约束 7006）
              proxyHeaders: checked ? proxyHeaders : [],
            })
          }
        />
        <Text type="secondary">
          {proxyEnabled ? "调用经平台中转，可透传请求头" : "关闭（直连调用）"}
        </Text>
      </Space>

      {/* 仅启用代理后才显示透传 Header 配置 */}
      {proxyEnabled && (
        <div>
          <Table<ProxyHeader>
            size="small"
            rowKey={(_, i) => String(i)}
            columns={columns}
            dataSource={proxyHeaders}
            pagination={false}
            locale={{ emptyText: "暂无透传 Header" }}
          />
          <Space style={{ marginTop: 8 }}>
            <Button
              size="small"
              icon={<PlusOutlined />}
              disabled={proxyHeaders.length >= TOOL_LIMITS.PROXY_HEADER_MAX}
              onClick={addRow}
            >
              添加 Header
            </Button>
            <Popover
              trigger="click"
              title="内置变量（点击插入到当前「值」）"
              content={
                <Space direction="vertical" size={4}>
                  {BUILTIN_VARIABLES.map((v) => (
                    <Button
                      key={v.name}
                      type="link"
                      size="small"
                      style={{ padding: 0, height: "auto" }}
                      onClick={() => insertVariable(v.name)}
                    >
                      {`{${v.name}}`} — {v.description}
                    </Button>
                  ))}
                </Space>
              }
            >
              <Button size="small" type="link">
                ℹ 内置变量
              </Button>
            </Popover>
          </Space>

          {!validation.ok && (
            <div style={{ color: "#DC2626", fontSize: 12, marginTop: 8 }}>
              ✗ {validation.error}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
