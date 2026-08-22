/**
 * Agent 评测 Tab 壳 — `/agent/evaluation/{datasets|graders|tasks}`
 * 深页（create/detail/compare）不走本壳。
 *
 * Tab 列表为 lazy 路由：必须在 Outlet 外包一层 Suspense，避免挂起冒泡到整页
 * Routes Suspense 导致标题/Tabs 一并卸载（切 Tab 白屏闪一下）。
 */
import { Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Skeleton, Statistic, Tabs, Typography } from 'antd';
import { evalApi } from '@/services/evaluation';
import type { EvalTaskStatsVO } from '@/types';
import { COLOR, EVAL_BASE } from './constants';

const { Title, Text } = Typography;

const TAB_ITEMS = [
  { key: 'datasets', label: '评测集' },
  { key: 'graders', label: '评估器' },
  { key: 'tasks', label: '评测任务' },
] as const;

/** 与 router lazy 同源，挂载后预拉三个 Tab chunk，切 Tab 尽量不再 suspend */
function prefetchTabChunks() {
  void import('./datasets/List');
  void import('./graders/List');
  void import('./tasks/List');
}

function activeTab(pathname: string): string {
  if (pathname.includes('/datasets')) return 'datasets';
  if (pathname.includes('/graders')) return 'graders';
  return 'tasks';
}

function TabContentFallback() {
  return (
    <div style={{ paddingTop: 8, minHeight: 280 }}>
      <Skeleton active paragraph={{ rows: 8 }} />
    </div>
  );
}

export default function EvaluationShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const tab = activeTab(location.pathname);
  const [stats, setStats] = useState<EvalTaskStatsVO | null>(null);

  useEffect(() => {
    prefetchTabChunks();
  }, []);

  useEffect(() => {
    void evalApi
      .taskStats()
      .then(setStats)
      .catch(() => setStats(null));
  }, [location.pathname]);

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <div style={{ marginBottom: 20 }}>
        <Title
          level={2}
          style={{
            margin: 0,
            color: COLOR.textPrimary,
            fontSize: 24,
            fontWeight: 700,
          }}
        >
          Agent 评测
        </Title>
        <Text
          style={{
            color: COLOR.textSecondary,
            fontSize: 14,
            marginTop: 4,
            display: 'block',
          }}
        >
          评测集 × 评估器 × 评测任务——版本化回归与质量度量
        </Text>
      </div>

      {stats && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
            gap: 16,
            marginBottom: 20,
            padding: '16px 20px',
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            background: COLOR.headerBg,
          }}
        >
          <Statistic title="评测集" value={stats.datasetCount ?? 0} />
          <Statistic title="评估器" value={stats.graderCount ?? 0} />
          <Statistic title="任务总数" value={stats.taskCount ?? 0} />
          <Statistic title="运行中" value={stats.runningTaskCount ?? 0} />
          <Statistic title="已完成" value={stats.finishedTaskCount ?? 0} />
          <Statistic title="失败" value={stats.failedTaskCount ?? 0} />
          {stats.avgPassRate != null && (
            <Statistic
              title="平均通过率"
              value={stats.avgPassRate}
              suffix="%"
              precision={1}
            />
          )}
        </div>
      )}

      <Tabs
        activeKey={tab}
        items={TAB_ITEMS.map((t) => ({ key: t.key, label: t.label }))}
        onChange={(key) => navigate(`${EVAL_BASE}/${key}`)}
        style={{ marginBottom: 8 }}
      />

      <Suspense fallback={<TabContentFallback />}>
        <Outlet />
      </Suspense>
    </div>
  );
}
