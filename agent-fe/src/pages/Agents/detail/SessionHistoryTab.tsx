/**
 * 会话历史 Tab — Agent 详情页「会话历史」Tab 页。
 *
 * 展示该 Agent 的全部历史会话（分页列表，按时间倒序），
 * 每行显示：会话ID、会话名称、来源标签（DEBUG_CONSOLE / API）、创建时间、最后更新时间。
 * 支持按关键字（会话ID/名称）搜索。
 * 点击某行 → 右侧抽屉展示会话完整消息历史（复用 SessionMessagesView 组件，只读）。
 */
import React, { useEffect, useState, useCallback } from 'react';
import {
  Drawer,
  Input,
  Table,
  Tag,
  message,
} from 'antd';
import {
  SearchOutlined,
  ApiOutlined,
  BugOutlined,
} from '@ant-design/icons';
import type { TableColumnsType, TablePaginationConfig } from 'antd';
import { SessionApi } from '@/services';
import SessionMessagesView, { toChatMessage } from '@/components/SessionMessagesView';
import type { SessionListVO } from '@/types';

const PAGE_SIZE = 20;

const COLOR = {
  textPrimary: '#0F172B',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  border: '#E5E7EB',
  bgTagDebug: '#EFF6FF',
  textTagDebug: '#2563EB',
  bgTagApi: '#F0FDF4',
  textTagApi: '#16A34A',
} as const;

interface SessionHistoryTabProps {
  agentNum: string;
}

const SessionHistoryTab: React.FC<SessionHistoryTabProps> = ({ agentNum }) => {
  const [sessions, setSessions] = useState<SessionListVO[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMessages, setDrawerMessages] = useState<any[]>([]);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [drawerTitle, setDrawerTitle] = useState('');

  const fetchSessions = useCallback(async (page: number, kw: string) => {
    setLoading(true);
    try {
      const res = await SessionApi.pageList({
        pageNo: page,
        pageSize: PAGE_SIZE,
        agentNum,
        keyword: kw || undefined,
      });
      setSessions(res.list);
      setTotal(res.total);
    } catch {
      message.error('加载会话列表失败');
    } finally {
      setLoading(false);
    }
  }, [agentNum]);

  useEffect(() => {
    if (agentNum) {
      setPageNo(1);
      fetchSessions(1, keyword);
    }
  }, [agentNum, fetchSessions]);

  const handleSearch = (value: string) => {
    const kw = value.trim();
    setKeyword(kw);
    setPageNo(1);
    fetchSessions(1, kw);
  };

  const handleTableChange = (pagination: TablePaginationConfig) => {
    const p = pagination.current || 1;
    setPageNo(p);
    fetchSessions(p, keyword);
  };

  const handleRowClick = async (record: SessionListVO) => {
    setDrawerTitle(record.title || '未命名会话');
    setDrawerOpen(true);
    setDrawerLoading(true);
    setDrawerMessages([]);
    try {
      const list = await SessionApi.listMessages(record.num);
      setDrawerMessages(list.map(toChatMessage));
    } catch {
      message.error('加载会话详情失败');
    } finally {
      setDrawerLoading(false);
    }
  };

  const columns: TableColumnsType<SessionListVO> = [
    {
      title: '会话 ID',
      dataIndex: 'num',
      key: 'num',
      width: 200,
      ellipsis: true,
    },
    {
      title: '会话名称',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
      render: (text: string) => text || '未命名会话',
    },
    {
      title: '来源',
      dataIndex: 'origin',
      key: 'origin',
      width: 140,
      render: (origin: string) => {
        if (origin === 'DEBUG_CONSOLE') {
          return (
            <Tag
              icon={<BugOutlined />}
              color="blue"
              style={{ borderRadius: 4, fontSize: 11 }}
            >
              调试台
            </Tag>
          );
        }
        if (origin === 'API') {
          return (
            <Tag
              icon={<ApiOutlined />}
              color="green"
              style={{ borderRadius: 4, fontSize: 11 }}
            >
              API
            </Tag>
          );
        }
        return <Tag style={{ borderRadius: 4, fontSize: 11 }}>未知</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString() : '-',
    },
    {
      title: '最后更新时间',
      dataIndex: 'lastMessageAt',
      key: 'lastMessageAt',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString() : '-',
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* 搜索栏 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
        }}
      >
        <Input.Search
          placeholder="搜索会话 ID / 名称"
          allowClear
          onSearch={handleSearch}
          style={{ width: 280 }}
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
        />
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table
          dataSource={sessions}
          columns={columns}
          rowKey="num"
          loading={loading}
          pagination={{
            current: pageNo,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            showTotal: (t) => `共 ${t} 条`,
          }}
          onChange={handleTableChange}
          onRow={(record) => ({
            onClick: () => handleRowClick(record),
            style: { cursor: 'pointer' },
          })}
          size="middle"
        />
      </div>

      <Drawer
        title={drawerTitle}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
        destroyOnClose
      >
        <SessionMessagesView
          messages={drawerMessages}
          loading={drawerLoading}
          emptyText="暂无消息记录"
        />
      </Drawer>
    </div>
  );
};

export default SessionHistoryTab;