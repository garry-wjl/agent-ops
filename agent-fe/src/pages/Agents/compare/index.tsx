/**
 * Agent 版本对比 — `/agent/manage/compare/:num`
 *
 * v2.4：后端尚未实现 `/api/v1/agents/version/compare` 接口（M3 后规划），
 * 本页暂为占位；保留路由以便后续接入。版本历史列表仍可展示，差异列待后端就绪。
 */
import { PageContainer } from '@ant-design/pro-components';
import { Card, Empty, Table, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { agentApi } from '@/services/agent';
import type { AgentVersionVO } from '@/types';

export default function AgentComparePage() {
  const navigate = useNavigate();
  const params = useParams();
  const num = params.num!;
  const [versions, setVersions] = useState<AgentVersionVO[]>([]);

  useEffect(() => {
    agentApi.versionList(num).then(setVersions);
  }, [num]);

  return (
    <PageContainer
      header={{ title: '版本历史' }}
      onBack={() => navigate(`/agent/manage/detail/${num}`)}
    >
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Typography.Text type="secondary">
          版本对比接口尚未在后端实现（M3 规划）；当前仅展示版本列表。
        </Typography.Text>
      </Card>

      {versions.length === 0 ? (
        <Empty description="暂无版本记录" />
      ) : (
        <Table
          rowKey="version"
          dataSource={versions}
          pagination={false}
          columns={[
            { title: '版本', dataIndex: 'version', width: 120 },
            { title: '变更等级', dataIndex: 'changeLevel', width: 120 },
            { title: '备注', dataIndex: 'remark' },
            { title: '发布人', dataIndex: 'publishedBy', width: 120 },
            { title: '发布时间', dataIndex: 'publishedAt', width: 200 },
          ]}
        />
      )}
    </PageContainer>
  );
}
