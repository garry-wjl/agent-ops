/**
 * 评测任务详情（轮询状态）— `/agent/evaluation/tasks/:num`
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Descriptions,
  Empty,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
  Modal,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  ExperimentOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  SaveOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import type { EvalTaskDetailVO, EvalTaskItemVO } from '@/types';
import { prettyJson } from '@/types';
import JsonEditor from '@/components/JsonEditor';
import PermissionGate from '@/components/PermissionGate';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import {
  COLOR,
  EVAL_BASE,
  TABLE_STYLE,
  passRateText,
  TASK_STATUS_LABEL,
  TASK_ITEM_STATUS_LABEL,
  enumLabel,
} from '../constants';
import DistillGraderModal from './DistillGraderModal';

const { Title, Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  FINISHED: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
};

const COLUMN_TIPS = {
  row: '评测集中该用例的行号（发布版本内的顺序）。',
  status:
    '本行执行状态：PENDING 待跑 / RUNNING 执行中 / PASSED 综合通过 / FAILED 评估未全部通过 / ERROR 调用或系统异常 / CANCELLED 已取消。',
  overallPass:
    '本行挂载的全部评估器均 Pass 时为综合通过（Pass）；任一评估器 Fail 则为 Fail。与任务运行状态无关。',
  input:
    '本行写入评测任务时的输入快照（一般为评测集行的 dataJson）。点击可查看完整 JSON，含 input / reference / context 等字段。',
  actualOutput:
    '绑定 Agent 时为该行 invoke 后的实际回复；未绑定 Agent（NONE）时为行内已有 output 字段。悬停可看全文。',
  scores:
    '本行各评估器的得分与 Pass/Fail。分数与阈值由评估器配置决定；全部 Pass 才会使「综合结果」为 Pass。悬停单条可看说明。',
  latency: '本行从开始执行到产出结果（含调用 Agent 与打分）的耗时，单位毫秒。',
  label:
    '人工标注 JSON，用于补充主观维度或沉淀样本。填写并「保存标注」后，可用顶部「蒸馏 LLM 评估器」生成可复用评估器。',
  error:
    '本行失败原因：调用/系统异常时为错误信息；评估未全部通过时多为「评估未全部通过」。通过的行为空。具体谁 Fail 请看「评估器得分」。',
} as const;

function ColumnTitle({
  label,
  tip,
}: {
  label: string;
  tip: string;
}) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      {label}
      <Tooltip title={tip}>
        <QuestionCircleOutlined
          style={{ color: COLOR.textMuted, fontSize: 12, cursor: 'help' }}
        />
      </Tooltip>
    </span>
  );
}

const DEFAULT_LABEL_JSON = `{
  "quality": "好",
  "passed": true,
  "note": "说明为什么好/不好，蒸馏时会作为样本依据"
}`;

const POLL_MS = 3000;

function formatScore(score?: number | string | null): string {
  if (score == null || score === '') return '—';
  const n = typeof score === 'number' ? score : Number(score);
  if (Number.isNaN(n)) return String(score);
  return n.toFixed(2);
}

export default function TaskDetailPage() {
  const { num = '' } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<EvalTaskDetailVO | null>(null);
  const [items, setItems] = useState<EvalTaskItemVO[]>([]);
  const [labelDrafts, setLabelDrafts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [rerunning, setRerunning] = useState(false);
  const [savingLabels, setSavingLabels] = useState(false);
  const [jsonView, setJsonView] = useState<{ title: string; value: string } | null>(
    null,
  );
  const [labelEdit, setLabelEdit] = useState<{
    itemNum: string;
    value: string;
  } | null>(null);
  const [distillOpen, setDistillOpen] = useState(false);

  useBreadcrumbName(detail?.name);

  const load = useCallback(async (opts?: { silent?: boolean }) => {
    if (!num) return;
    if (!opts?.silent) {
      setLoading(true);
    }
    try {
      const [d, its] = await Promise.all([
        evalApi.taskDetail(num),
        evalApi.taskItems(num),
      ]);
      setDetail(d);
      setItems(its ?? []);
      const drafts: Record<string, string> = {};
      for (const it of its ?? []) {
        drafts[it.num] = it.labelJson
          ? prettyJson(it.labelJson, '{}')
          : '';
      }
      setLabelDrafts(drafts);
    } finally {
      if (!opts?.silent) {
        setLoading(false);
      }
    }
  }, [num]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!detail) return;
    const running =
      detail.status === 'PENDING' || detail.status === 'RUNNING';
    if (!running) return;
    const id = window.setInterval(() => {
      void load({ silent: true });
    }, POLL_MS);
    return () => window.clearInterval(id);
  }, [detail?.status, load, detail]);

  const handleCancel = () => {
    Modal.confirm({
      title: '取消任务',
      content: '确认取消当前运行中的任务？',
      okText: '取消任务',
      cancelText: '返回',
      onOk: async () => {
        await evalApi.cancelTask(num);
        message.success('已取消');
        void load();
      },
    });
  };

  const failedCount =
    detail?.failedCount ??
    items.filter((it) => it.status === 'FAILED').length;

  const handleRerunFailed = () => {
    Modal.confirm({
      title: '重跑失败项',
      content: `将重跑 ${failedCount} 条失败用例，确认继续？`,
      okText: '重跑',
      cancelText: '返回',
      onOk: async () => {
        setRerunning(true);
        try {
          await evalApi.rerunFailedTask(num);
          message.success('已提交重跑');
          void load();
        } finally {
          setRerunning(false);
        }
      },
    });
  };

  const handleSaveLabels = async () => {
    setSavingLabels(true);
    try {
      await evalApi.saveTaskLabels({
        taskNum: num,
        labelConfigJson: detail?.labelConfigJson,
        items: items.map((it) => ({
          itemNum: it.num,
          labelJson: labelDrafts[it.num]?.trim() || undefined,
        })),
      });
      message.success('标注已保存');
      void load();
    } finally {
      setSavingLabels(false);
    }
  };

  const updateLabel = (itemNum: string, value: string) => {
    setLabelDrafts((prev) => ({ ...prev, [itemNum]: value }));
  };

  const labeledCount = useMemo(() => {
    return items.filter((it) => Boolean(it.labelJson?.trim())).length;
  }, [items]);

  const unsavedLabelEdits = useMemo(() => {
    for (const it of items) {
      const draft = (labelDrafts[it.num] ?? '').trim();
      const saved = it.labelJson?.trim()
        ? prettyJson(it.labelJson, '{}').trim()
        : '';
      if (draft !== saved) {
        return true;
      }
    }
    return false;
  }, [items, labelDrafts]);

  const columns: TableColumnsType<EvalTaskItemVO> = [
    {
      title: <ColumnTitle label="行" tip={COLUMN_TIPS.row} />,
      dataIndex: 'rowIndex',
      width: 70,
    },
    {
      title: <ColumnTitle label="状态" tip={COLUMN_TIPS.status} />,
      dataIndex: 'status',
      width: 100,
      render: (s: string) => (
        <Tag color={STATUS_COLOR[s] ?? 'default'}>
          {enumLabel(TASK_ITEM_STATUS_LABEL, s)}
        </Tag>
      ),
    },
    {
      title: <ColumnTitle label="综合结果" tip={COLUMN_TIPS.overallPass} />,
      dataIndex: 'overallPass',
      width: 110,
      render: (p?: boolean) =>
        p == null ? '—' : p ? (
          <Tag color="success">Pass</Tag>
        ) : (
          <Tag color="error">Fail</Tag>
        ),
    },
    {
      title: <ColumnTitle label="输入" tip={COLUMN_TIPS.input} />,
      dataIndex: 'inputJson',
      width: 200,
      render: (j?: string) => (
        <Button
          type="link"
          size="small"
          style={{ padding: 0, fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
          onClick={() =>
            setJsonView({
              title: '输入 JSON',
              value: prettyJson(j, '{}'),
            })
          }
        >
          {j && j.length > 60 ? `${j.slice(0, 60)}…` : j || '—'}
        </Button>
      ),
    },
    {
      title: <ColumnTitle label="实际输出" tip={COLUMN_TIPS.actualOutput} />,
      dataIndex: 'actualOutput',
      width: 220,
      render: (t?: string) => {
        const text = t || '—';
        return (
          <Tooltip
            title={
              <div
                style={{
                  maxHeight: 280,
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {text}
              </div>
            }
            mouseEnterDelay={0.35}
            styles={{ root: { maxWidth: 480 } }}
          >
            <div
              style={{
                fontSize: 12,
                lineHeight: 1.5,
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                wordBreak: 'break-word',
                cursor: 'default',
              }}
            >
              {text}
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: <ColumnTitle label="评估器得分" tip={COLUMN_TIPS.scores} />,
      dataIndex: 'scores',
      width: 320,
      render: (scores: EvalTaskItemVO['scores']) => {
        if (!scores?.length) {
          return <Text type="secondary">—</Text>;
        }
        const nameByNum = new Map(
          (detail?.graders ?? []).map((g) => [
            g.graderNum,
            g.name || g.graderNum,
          ]),
        );
        return (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              minWidth: 280,
            }}
          >
            {scores.map((s) => {
              const name =
                s.graderName ||
                nameByNum.get(s.graderNum) ||
                s.graderNum;
              const tip = [
                name,
                `编号：${s.graderNum}`,
                `得分：${formatScore(s.score)}`,
                `结果：${s.passed == null ? '—' : s.passed ? 'Pass' : 'Fail'}`,
                s.explanation ? `说明：${s.explanation}` : '',
              ]
                .filter(Boolean)
                .join('\n');
              return (
                <Tooltip
                  key={s.graderNum}
                  title={
                    <div style={{ whiteSpace: 'pre-wrap' }}>{tip}</div>
                  }
                  mouseEnterDelay={0.35}
                >
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'minmax(0, 1fr) 52px auto',
                      alignItems: 'center',
                      columnGap: 8,
                      fontSize: 12,
                      lineHeight: 1.4,
                    }}
                  >
                    <Text
                      ellipsis
                      style={{ margin: 0, fontSize: 12 }}
                      title={name}
                    >
                      {name}
                    </Text>
                    <Text
                      style={{
                        margin: 0,
                        fontSize: 13,
                        fontWeight: 600,
                        fontVariantNumeric: 'tabular-nums',
                        textAlign: 'right',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {formatScore(s.score)}
                    </Text>
                    {s.passed == null ? (
                      <Tag style={{ margin: 0 }}>—</Tag>
                    ) : s.passed ? (
                      <Tag color="success" style={{ margin: 0 }}>
                        Pass
                      </Tag>
                    ) : (
                      <Tag color="error" style={{ margin: 0 }}>
                        Fail
                      </Tag>
                    )}
                  </div>
                </Tooltip>
              );
            })}
          </div>
        );
      },
    },
    {
      title: <ColumnTitle label="时延" tip={COLUMN_TIPS.latency} />,
      dataIndex: 'latencyMs',
      width: 90,
      render: (ms?: number) => (ms != null ? `${ms}ms` : '—'),
    },
    {
      title: <ColumnTitle label="人工标注" tip={COLUMN_TIPS.label} />,
      key: 'labelJson',
      width: 140,
      render: (_, r) => {
        const draft = labelDrafts[r.num] ?? '';
        return (
          <Button
            type="link"
            size="small"
            onClick={() =>
              setLabelEdit({
                itemNum: r.num,
                value: draft ? prettyJson(draft, '{}') : DEFAULT_LABEL_JSON,
              })
            }
          >
            {draft ? '编辑 JSON' : '填写'}
          </Button>
        );
      },
    },
    {
      title: <ColumnTitle label="错误" tip={COLUMN_TIPS.error} />,
      dataIndex: 'errorMessage',
      width: 160,
      render: (e?: string) => (
        <Text type="danger" style={{ fontSize: 12 }} ellipsis>
          {e || ''}
        </Text>
      ),
    },
  ];

  if (!detail) {
    return (
      <div style={{ padding: 32 }}>
        <Text type="secondary">加载中…</Text>
      </div>
    );
  }

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/tasks`}
        moduleName="Agent 评测"
        current={detail.name}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 24,
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <Space align="center" style={{ marginBottom: 4 }}>
            <Title
              level={3}
              style={{ margin: 0, color: COLOR.textPrimary, fontWeight: 700 }}
            >
              {detail.name}
            </Title>
            <Tag color={STATUS_COLOR[detail.status] ?? 'default'}>
              {enumLabel(TASK_STATUS_LABEL, detail.status)}
            </Tag>
          </Space>
          <Text
            style={{
              fontFamily: 'ui-monospace, monospace',
              fontSize: 13,
              color: COLOR.textMuted,
              display: 'block',
            }}
          >
            {detail.num}
          </Text>
          {detail.description ? (
            <Text
              style={{
                color: COLOR.textSecondary,
                display: 'block',
                marginTop: 8,
                maxWidth: 640,
              }}
            >
              {detail.description}
            </Text>
          ) : null}
        </div>
        <Space wrap>
          {failedCount > 0 &&
            detail.status !== 'RUNNING' &&
            detail.status !== 'PENDING' && (
              <PermissionGate anyOf={['evaluation:task:execute']}>
                <Button
                  icon={<ReloadOutlined />}
                  loading={rerunning}
                  onClick={handleRerunFailed}
                >
                  重跑失败项 ({failedCount})
                </Button>
              </PermissionGate>
            )}
          <PermissionGate anyOf={['evaluation:task:execute']}>
            <Button
              icon={<SaveOutlined />}
              loading={savingLabels}
              onClick={() => void handleSaveLabels()}
            >
              保存标注
            </Button>
          </PermissionGate>
          <PermissionGate anyOf={['evaluation:grader:create']}>
            <Tooltip
              title={
                labeledCount > 0
                  ? unsavedLabelEdits
                    ? `已保存 ${labeledCount} 条标注；另有未保存修改，蒸馏只用已落库样本，建议先「保存标注」`
                    : `用本任务已保存的 ${labeledCount} 条人工标注蒸馏 LLM 评估器`
                  : unsavedLabelEdits
                    ? '请先点击「保存标注」，蒸馏只使用已落库的标注'
                    : '请先为 Case 填写并保存人工标注'
              }
            >
              <Button
                icon={<ExperimentOutlined />}
                disabled={labeledCount <= 0}
                onClick={() => setDistillOpen(true)}
              >
                蒸馏 LLM 评估器
              </Button>
            </Tooltip>
          </PermissionGate>
          {(detail.status === 'RUNNING' || detail.status === 'PENDING') && (
            <PermissionGate anyOf={['evaluation:task:execute']}>
              <Button danger icon={<StopOutlined />} onClick={handleCancel}>
                取消任务
              </Button>
            </PermissionGate>
          )}
        </Space>
      </div>

      <div
        style={{
          marginBottom: 20,
          padding: '16px 20px',
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          background: COLOR.headerBg,
        }}
      >
        <Text
          strong
          style={{
            display: 'block',
            marginBottom: 12,
            color: COLOR.textPrimary,
            fontSize: 13,
          }}
        >
          基本信息
        </Text>
        <Descriptions
          size="small"
          column={{ xs: 1, sm: 2, md: 3 }}
          styles={{
            label: {
              color: COLOR.textMuted,
              whiteSpace: 'nowrap',
              width: 108,
            },
          }}
        >
          <Descriptions.Item label="任务编号">
            <Text copyable style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>
              {detail.num}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={STATUS_COLOR[detail.status] ?? 'default'}>
              {enumLabel(TASK_STATUS_LABEL, detail.status)}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="绑定方式">
            {detail.bindMode === 'AGENT' ? 'AGENT（调用 Agent）' : 'NONE（不调用 Agent）'}
          </Descriptions.Item>
          <Descriptions.Item label="评测集">
            <a
              onClick={() =>
                navigate(`${EVAL_BASE}/datasets/${detail.datasetNum}`)
              }
            >
              {detail.datasetNum}
            </a>
            {detail.datasetVersion != null ? ` @v${detail.datasetVersion}` : ''}
          </Descriptions.Item>
          <Descriptions.Item label="Agent">
            {detail.bindMode === 'AGENT' ? (
              <Text style={{ fontSize: 12 }}>
                {detail.agentNum || '—'}
                {detail.agentVersionNum ? ` / ${detail.agentVersionNum}` : ''}
              </Text>
            ) : (
              <Text type="secondary">—</Text>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="通过率">
            {passRateText(detail.passedCount, detail.totalCount)}
          </Descriptions.Item>
          <Descriptions.Item label="用例统计">
            共 {detail.totalCount ?? 0} · 通过 {detail.passedCount ?? 0} · 未通过{' '}
            {detail.failedCount ?? 0}
          </Descriptions.Item>
          <Descriptions.Item label="创建人">
            {detail.creatorUserId || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {detail.createTime || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {detail.updateTime || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="评估器" span={3}>
            {(detail.graders ?? []).length === 0 ? (
              <Text type="secondary">—</Text>
            ) : (
              <Space size={[6, 6]} wrap>
                {(detail.graders ?? []).map((g) => (
                  <Tag
                    key={g.graderNum}
                    style={{ cursor: 'pointer', marginInlineEnd: 0 }}
                    onClick={() =>
                      navigate(`${EVAL_BASE}/graders/${g.graderNum}`)
                    }
                  >
                    {g.name || g.graderNum}
                    {g.kind ? ` · ${g.kind}` : ''}
                    {g.graderVersion != null ? ` @v${g.graderVersion}` : ''}
                  </Tag>
                ))}
              </Space>
            )}
          </Descriptions.Item>
          {detail.description ? (
            <Descriptions.Item label="描述" span={3}>
              {detail.description}
            </Descriptions.Item>
          ) : null}
        </Descriptions>
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table<EvalTaskItemVO>
          rowKey="num"
          columns={columns}
          dataSource={items}
          loading={loading}
          size="middle"
          scroll={{ x: 1400 }}
          rowClassName={() => 'eval-list-row'}
          locale={{
            emptyText: (
              <Empty description="暂无用例明细" style={{ padding: 32 }} />
            ),
          }}
          pagination={{ pageSize: 50, showTotal: (t) => `共 ${t} 条` }}
        />
      </div>

      <Modal
        title={jsonView?.title || 'JSON'}
        open={!!jsonView}
        onCancel={() => setJsonView(null)}
        footer={
          <Button type="primary" onClick={() => setJsonView(null)}>
            关闭
          </Button>
        }
        width={720}
        destroyOnHidden
      >
        {jsonView && (
          <JsonEditor value={jsonView.value} readOnly height={360} />
        )}
      </Modal>

      <Modal
        title="编辑人工标注"
        open={!!labelEdit}
        onCancel={() => setLabelEdit(null)}
        onOk={() => {
          if (!labelEdit) return;
          const raw = labelEdit.value.trim();
          if (raw) {
            try {
              JSON.parse(raw);
            } catch {
              message.error('标注内容须为合法 JSON');
              return;
            }
          }
          updateLabel(labelEdit.itemNum, raw);
          setLabelEdit(null);
        }}
        okText="确定"
        width={720}
        destroyOnHidden
      >
        {labelEdit && (
          <>
            <Text
              type="secondary"
              style={{ display: 'block', marginBottom: 12, fontSize: 13 }}
            >
              无固定 Schema，写成任意合法 JSON 即可。建议包含你对「实际输出」的判断（如
              quality / passed / note）。保存后可用于回看，以及「蒸馏 LLM
              评估器」当 few-shot 样本。点确定后还需点顶部「保存标注」才落库。
            </Text>
            <JsonEditor
              value={labelEdit.value}
              onChange={(v) =>
                setLabelEdit((prev) => (prev ? { ...prev, value: v } : prev))
              }
              height={320}
            />
          </>
        )}
      </Modal>

      <DistillGraderModal
        open={distillOpen}
        taskNum={detail.num}
        taskName={detail.name}
        labeledCount={labeledCount}
        onClose={() => setDistillOpen(false)}
      />

      <style>{TABLE_STYLE}</style>
    </div>
  );
}
