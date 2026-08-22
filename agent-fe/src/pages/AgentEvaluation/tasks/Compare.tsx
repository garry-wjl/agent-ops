/**
 * 评测任务对比 — `/agent/evaluation/compare`
 */
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Empty,
  Form,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { evalApi } from '@/services/evaluation';
import type { EvalTaskVO, TaskCompareRowVO, TaskCompareVO } from '@/types';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import { COLOR, EVAL_BASE, TABLE_STYLE } from '../constants';

const { Title, Text } = Typography;

const VERDICT_COLOR: Record<string, string> = {
  uplift: 'success',
  regress: 'error',
  same: 'default',
  missing: 'warning',
};

export default function TaskComparePage() {
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();
  const [tasks, setTasks] = useState<EvalTaskVO[]>([]);
  const [result, setResult] = useState<TaskCompareVO | null>(null);
  const [loading, setLoading] = useState(false);
  useBreadcrumbName('任务对比');

  useEffect(() => {
    void evalApi
      .pageTasks({ pageNo: 1, pageSize: 100, status: 'FINISHED' })
      .then((p) => setTasks(p?.list ?? []));
    const left = searchParams.get('left');
    const right = searchParams.get('right');
    if (left || right) {
      form.setFieldsValue({
        leftTaskNum: left || undefined,
        rightTaskNum: right || undefined,
      });
    }
  }, [form, searchParams]);

  const onCompare = async (values: {
    leftTaskNum: string;
    rightTaskNum: string;
  }) => {
    if (values.leftTaskNum === values.rightTaskNum) {
      message.warning('请选择两个不同的任务');
      return;
    }
    setLoading(true);
    try {
      const res = await evalApi.compareTasks(values);
      setResult(res);
    } finally {
      setLoading(false);
    }
  };

  const columns: TableColumnsType<TaskCompareRowVO> = [
    { title: '行号', dataIndex: 'rowIndex', width: 80 },
    {
      title: '左侧 Pass',
      dataIndex: 'leftPass',
      width: 100,
      render: (p?: boolean) =>
        p == null ? '—' : p ? (
          <Tag color="success">Pass</Tag>
        ) : (
          <Tag color="error">Fail</Tag>
        ),
    },
    {
      title: '右侧 Pass',
      dataIndex: 'rightPass',
      width: 100,
      render: (p?: boolean) =>
        p == null ? '—' : p ? (
          <Tag color="success">Pass</Tag>
        ) : (
          <Tag color="error">Fail</Tag>
        ),
    },
    {
      title: '判定',
      dataIndex: 'verdict',
      width: 120,
      render: (v: string) => (
        <Tag color={VERDICT_COLOR[v] ?? 'default'}>{v}</Tag>
      ),
    },
  ];

  const taskOptions = tasks.map((t) => ({
    value: t.num,
    label: `${t.name} (${t.num}) · ${t.datasetNum}@v${t.datasetVersion}`,
  }));

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/tasks`}
        moduleName="Agent 评测"
        current="任务对比"
      />
      <Title
        level={3}
        style={{ margin: '0 0 4px', color: COLOR.textPrimary, fontWeight: 700 }}
      >
        任务对比
      </Title>
      <Text style={{ color: COLOR.textSecondary, display: 'block', marginBottom: 24 }}>
        建议对比同一评测集版本下的两次 FINISHED 任务
      </Text>

      <Form
        form={form}
        layout="inline"
        onFinish={onCompare}
        style={{ marginBottom: 24, rowGap: 12 }}
      >
        <Form.Item
          name="leftTaskNum"
          rules={[{ required: true, message: '选择左侧任务' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="左侧任务"
            style={{ width: 320 }}
            options={taskOptions}
          />
        </Form.Item>
        <Form.Item
          name="rightTaskNum"
          rules={[{ required: true, message: '选择右侧任务' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="右侧任务"
            style={{ width: 320 }}
            options={taskOptions}
          />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>
            开始对比
          </Button>
        </Form.Item>
      </Form>

      {result && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space size={24} wrap>
            <Text>
              左侧通过率：
              <Text strong>
                {result.leftPassRate != null
                  ? `${(result.leftPassRate * 100).toFixed(1)}%`
                  : '—'}
              </Text>
            </Text>
            <Text>
              右侧通过率：
              <Text strong>
                {result.rightPassRate != null
                  ? `${(result.rightPassRate * 100).toFixed(1)}%`
                  : '—'}
              </Text>
            </Text>
            <Text>
              差值：
              <Text strong>
                {result.passRateDiff != null
                  ? `${(result.passRateDiff * 100).toFixed(1)}pp`
                  : '—'}
              </Text>
            </Text>
          </Space>

          <div
            style={{
              border: `1px solid ${COLOR.border}`,
              borderRadius: 8,
              overflow: 'hidden',
            }}
          >
            <Table<TaskCompareRowVO>
              rowKey="rowIndex"
              columns={columns}
              dataSource={result.rows ?? []}
              size="middle"
              rowClassName={() => 'eval-list-row'}
              locale={{
                emptyText: (
                  <Empty description="无对比行" style={{ padding: 32 }} />
                ),
              }}
              pagination={{ pageSize: 50 }}
            />
          </div>
        </Space>
      )}
      <style>{TABLE_STYLE}</style>
    </div>
  );
}
