/**
 * A2A 模式同步历史 Tab — v2.6 新增
 *
 * 列：版本号 (remoteVersion) / 同步事件类型 / 触发源 / 同步时间
 * 行操作：
 *   - [查看 AgentCard JSON]：弹 Modal + SyntaxHighlighter 显示 agentCardJson
 *
 * 数据源：GET /api/v1/agent/version/a2a-history?agentNum=...
 */
import { useState } from 'react';
import { Button, Empty, Modal, Space, Table, Tag, Tooltip } from 'antd';
import type { TableColumnsType } from 'antd';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { useA2aSyncHistoryQuery } from '@/services/agent';
import type { A2aSyncHistoryVO } from '@/types';
import { formatTime, prettyJson, safeJsonParse } from '@/utils/format';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

export interface A2aVersionHistoryTabProps {
  agentNum: string;
}

export default function A2aVersionHistoryTab({
  agentNum,
}: A2aVersionHistoryTabProps) {
  const { data, isLoading } = useA2aSyncHistoryQuery(agentNum);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState<A2aSyncHistoryVO | null>(null);

  const handleView = (r: A2aSyncHistoryVO) => {
    setActive(r);
    setOpen(true);
  };

  const columns: TableColumnsType<A2aSyncHistoryVO> = [
    {
      title: '版本号',
      dataIndex: 'remoteVersion',
      key: 'remoteVersion',
      width: 160,
      render: (v?: string) => (
        <span
          style={{
            fontFamily:
              'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
            fontSize: 13,
            color: COLOR.textPrimary,
          }}
        >
          {v || '-'}
        </span>
      ),
    },
    {
      title: '同步事件类型',
      dataIndex: 'syncEventType',
      key: 'syncEventType',
      width: 200,
      render: (t: string) =>
        t ? (
          <Tooltip title={t}>
            <Tag color="geekblue">{t}</Tag>
          </Tooltip>
        ) : (
          <span style={{ color: COLOR.textMuted }}>-</span>
        ),
    },
    {
      title: '触发源',
      dataIndex: 'triggeredBy',
      key: 'triggeredBy',
      width: 110,
      render: (t: A2aSyncHistoryVO['triggeredBy']) =>
        t === 'AUTO' ? (
          <Tag color="default">AUTO</Tag>
        ) : (
          <Tag color="purple">MANUAL</Tag>
        ),
    },
    {
      title: '同步时间',
      dataIndex: 'syncedAt',
      key: 'syncedAt',
      width: 200,
      render: (t?: string) => (
        <span style={{ color: COLOR.textSecondary, fontSize: 13 }}>
          {formatTime(t)}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, r) => (
        <Space>
          <a onClick={() => handleView(r)}>查看 AgentCard JSON</a>
        </Space>
      ),
    },
  ];

  if (!isLoading && (!data || data.length === 0)) {
    return <Empty description="暂无同步记录" />;
  }

  return (
    <div
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <Table<A2aSyncHistoryVO>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data ?? []}
        pagination={false}
        size="middle"
      />
      <Modal
        open={open}
        title={
          active
            ? `AgentCard JSON · ${active.remoteVersion ?? '-'}`
            : 'AgentCard JSON'
        }
        width={760}
        footer={<Button onClick={() => setOpen(false)}>关闭</Button>}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        {active?.agentCardJson ? (
          <SyntaxHighlighter
            language="json"
            customStyle={{
              margin: 0,
              padding: 16,
              background: '#F8FAFC',
              fontSize: 12,
              maxHeight: '60vh',
              overflow: 'auto',
              borderRadius: 6,
            }}
          >
            {(() => {
              const parsed = safeJsonParse(active.agentCardJson);
              return parsed ? prettyJson(parsed) : active.agentCardJson;
            })()}
          </SyntaxHighlighter>
        ) : (
          <Empty description="该次同步未保留 AgentCard 原文" />
        )}
        <div style={{ marginTop: 12, fontSize: 12, color: COLOR.textMuted }}>
          {active ? (
            <>
              触发源：{active.triggeredBy} · 同步时间：
              {formatTime(active.syncedAt)}
            </>
          ) : null}
        </div>
      </Modal>
    </div>
  );
}
