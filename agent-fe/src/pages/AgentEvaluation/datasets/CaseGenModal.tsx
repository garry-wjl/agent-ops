/**
 * 自动生成评测 Case — 弹窗（选择生成器 / 条数 / 清空策略 / 说明）+ 进度 + 历史重试
 */
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { HistoryOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { agentApi } from '@/services/agent';
import { evalApi } from '@/services/evaluation';
import type {
  AgentDebugVersionVO,
  AgentVO,
  CaseGenJobVO,
} from '@/types';
import {
  caseGenDebugVersionValue,
  caseGenStatusColor,
  caseGenStatusLabel,
} from './caseGenUtils';

const { TextArea } = Input;
const { Text: TypoText } = Typography;

function statusColor(status?: string): string {
  return caseGenStatusColor(status);
}

function statusLabel(status?: string): string {
  return caseGenStatusLabel(status);
}

export interface CaseGenModalProps {
  datasetNum: string;
  open: boolean;
  onClose: () => void;
  /** 任务完成（FINISHED）后回调，用于刷新行列表 */
  onFinished?: () => void;
}

export default function CaseGenModal({
  datasetNum,
  open,
  onClose,
  onFinished,
}: CaseGenModalProps) {
  const [form] = Form.useForm();
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [versions, setVersions] = useState<AgentDebugVersionVO[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [activeJob, setActiveJob] = useState<CaseGenJobVO | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [history, setHistory] = useState<CaseGenJobVO[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const generatorAgentNum = Form.useWatch('generatorAgentNum', form) as
    | string
    | undefined;

  useEffect(() => {
    if (!open) return;
    void agentApi
      .pageList({ pageNo: 1, pageSize: 100 })
      .then((ag) => setAgents(ag?.list ?? []))
      .catch(() => undefined);
  }, [open]);

  useEffect(() => {
    if (!open || !generatorAgentNum) {
      setVersions([]);
      return;
    }
    void agentApi
      .debugVersions(generatorAgentNum)
      .then((list) => {
        setVersions(list ?? []);
        const online = (list ?? []).find((v) => v.current && v.versionNum);
        if (online?.versionNum) {
          form.setFieldValue('generatorAgentVersionNum', online.versionNum);
        } else {
          const draft = (list ?? []).find((v) => v.status === 'DRAFT');
          form.setFieldValue(
            'generatorAgentVersionNum',
            draft ? 'DRAFT' : undefined,
          );
        }
      })
      .catch(() => setVersions([]));
  }, [open, generatorAgentNum, form]);

  const pollJob = useCallback(
    async (jobNum: string) => {
      const job = await evalApi.caseGenJobDetail(jobNum);
      setActiveJob(job);
      if (job.status === 'FINISHED') {
        message.success(
          `生成完成：写入 ${job.writtenCount ?? 0} 条，跳过 ${job.skippedCount ?? 0} 条`,
        );
        onFinished?.();
        return true;
      }
      if (job.status === 'FAILED') {
        message.error(job.errorMessage || '自动生成失败');
        return true;
      }
      return false;
    },
    [onFinished],
  );

  useEffect(() => {
    if (!activeJob?.num) return;
    if (activeJob.status === 'FINISHED' || activeJob.status === 'FAILED') {
      return;
    }
    const timer = window.setInterval(() => {
      void pollJob(activeJob.num).catch(() => undefined);
    }, 2000);
    return () => window.clearInterval(timer);
  }, [activeJob?.num, activeJob?.status, pollJob]);

  const handleStart = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await evalApi.startCaseGen({
        datasetNum,
        generatorAgentNum: values.generatorAgentNum,
        generatorAgentVersionNum: values.generatorAgentVersionNum,
        targetCount: values.targetCount ?? null,
        clearDraft: values.clearDraft === true,
        instructionMode: values.instructionMode || 'APPEND',
        userInstruction: values.userInstruction,
      });
      message.success('已启动自动生成');
      await pollJob(res.jobNum);
    } finally {
      setSubmitting(false);
    }
  };

  const loadHistory = async () => {
    setHistoryLoading(true);
    try {
      const page = await evalApi.pageCaseGenJobs({
        datasetNum,
        pageNo: 1,
        pageSize: 50,
      });
      setHistory(page?.list ?? []);
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleRetry = async (job: CaseGenJobVO) => {
    const res = await evalApi.retryCaseGen({ jobNum: job.num });
    message.success('已重新排队');
    setHistoryOpen(false);
    await pollJob(res.jobNum);
  };

  const columns: TableColumnsType<CaseGenJobVO> = [
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: string) => <Tag color={statusColor(s)}>{statusLabel(s)}</Tag>,
    },
    {
      title: '写入/跳过',
      width: 100,
      render: (_: unknown, r) =>
        `${r.writtenCount ?? 0} / ${r.skippedCount ?? 0}`,
    },
    {
      title: '操作',
      width: 80,
      render: (_: unknown, r) =>
        r.status === 'FAILED' ? (
          <Button type="link" size="small" onClick={() => void handleRetry(r)}>
            重试
          </Button>
        ) : null,
    },
  ];

  return (
    <>
      <Modal
        title="自动生成评测 Case"
        open={open}
        onCancel={onClose}
        width={640}
        destroyOnHidden
        footer={
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Button
              icon={<HistoryOutlined />}
              onClick={() => {
                setHistoryOpen(true);
                void loadHistory();
              }}
            >
              生成历史
            </Button>
            <Space>
              <Button onClick={onClose}>关闭</Button>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={submitting}
                disabled={
                  !!activeJob &&
                  (activeJob.status === 'PENDING' ||
                    activeJob.status === 'RUNNING')
                }
                onClick={() => void handleStart()}
              >
                开始生成
              </Button>
            </Space>
          </Space>
        }
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            clearDraft: false,
            instructionMode: 'APPEND',
          }}
        >
          <Form.Item
            name="generatorAgentNum"
            label="生成器 Agent"
            rules={[{ required: true, message: '请选择生成器 Agent' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择已发布/可调试的 Agent"
              options={agents.map((a) => ({
                value: a.num,
                label: `${a.name || a.num}（${a.num}）`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="generatorAgentVersionNum"
            label="Agent 版本"
            rules={[{ required: true, message: '请选择版本' }]}
            extra="默认当前在线已发布版，也可选手动版本或草稿"
          >
            <Select
              placeholder="选择版本"
              options={versions.map((v) => ({
                value: caseGenDebugVersionValue(v),
                label:
                  v.status === 'DRAFT'
                    ? '草稿（未发布）'
                    : `${v.versionNum}${v.current ? ' · 在线' : ''}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="targetCount"
            label="生成条数"
            extra="可不填：由模型自行决定合理数量（平台硬上限 50）"
          >
            <InputNumber min={1} max={50} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="clearDraft"
            label="写入方式"
            rules={[{ required: true }]}
          >
            <Radio.Group>
              <Radio value={false}>追加到现有草稿</Radio>
              <Radio value={true}>先清空草稿再写入</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="instructionMode" label="自定义说明模式">
            <Radio.Group>
              <Radio value="APPEND">追加到默认提示词</Radio>
              <Radio value="OVERRIDE">覆盖默认说明（仍保留用例字段定义/格式约束）</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="userInstruction" label="自定义说明（可选）">
            <TextArea rows={4} placeholder="补充生成要求，例如覆盖场景、难度等" />
          </Form.Item>
        </Form>

        {activeJob && (
          <div
            style={{
              marginTop: 8,
              padding: 12,
              background: '#fafafa',
              borderRadius: 8,
            }}
          >
            <Space direction="vertical" style={{ width: '100%' }} size={4}>
              <Space>
                <TypoText strong>任务进度</TypoText>
                <Tag color={statusColor(activeJob.status)}>
                  {statusLabel(activeJob.status)}
                </Tag>
                <TypoText type="secondary" style={{ fontSize: 12 }}>
                  {activeJob.num}
                </TypoText>
              </Space>
              <Progress
                percent={activeJob.progressPct ?? 0}
                status={
                  activeJob.status === 'FAILED'
                    ? 'exception'
                    : activeJob.status === 'FINISHED'
                      ? 'success'
                      : 'active'
                }
              />
              <TypoText type="secondary" style={{ fontSize: 12 }}>
                {activeJob.progressMessage ||
                  (activeJob.errorMessage
                    ? activeJob.errorMessage
                    : '等待更新…')}
              </TypoText>
              {(activeJob.writtenCount != null ||
                activeJob.skippedCount != null) &&
                (activeJob.status === 'FINISHED' ||
                  activeJob.status === 'FAILED') && (
                  <TypoText style={{ fontSize: 12 }}>
                    解析 {activeJob.parsedCount ?? 0} · 写入{' '}
                    {activeJob.writtenCount ?? 0} · 跳过{' '}
                    {activeJob.skippedCount ?? 0}
                  </TypoText>
                )}
            </Space>
          </div>
        )}
      </Modal>

      <Drawer
        title="自动生成历史"
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        width={560}
      >
        <Table<CaseGenJobVO>
          rowKey="num"
          size="small"
          loading={historyLoading}
          columns={columns}
          dataSource={history}
          pagination={false}
        />
      </Drawer>
    </>
  );
}
