/**
 * 评测详情 — `/skill/evaluation/detail/:num`
 * 像素级还原 Figma 节点 116:24（AgentSphere · Eval - Result）
 * 视觉：面包屑 / 标题行 / KPI 卡（总通过率 + 通过/失败/未判定）/ 筛选胶囊 / case 明细表
 * ⚠️ Figma 没画雷达图；保留 detail.radarData 的兼容渲染（如有则在 case 表下方显示）
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Empty, Tooltip, Typography, message } from 'antd';
import {
  CloudDownloadOutlined,
  CopyOutlined,
  RightOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  MinusOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { EvalApi } from '@/services';
import type { EvalCaseVO, EvalDimension, EvaluationDetailVO } from '@/types';
import { copyToClipboard, formatTime, prettyJson } from '@/utils/format';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';

const { Text } = Typography;

const COLOR = {
  border: '#E5E7EB',
  borderHard: '#E2E8F0',
  textPrimary: '#0F172B',
  textBody: '#1D293D',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  primary: '#3B82F6',
  primaryDeep: '#2563EB',
  bgInfo: '#EFF6FF',
  textSuccess: '#10B981',
  bgSuccess: '#ECFDF5',
  textError: '#EF4444',
  bgError: '#FEF2F2',
  textPending: '#D97706',
  bgPending: '#FEF3C7',
} as const;

const DIM_LABEL: Record<EvalDimension, string> = {
  ACCURACY: '准确性',
  RELEVANCE: '相关性',
  COMPLETENESS: '完整性',
  STYLE: '风格',
  COMPLIANCE: '合规',
};

type Filter = 'ALL' | 'PASS' | 'FAIL' | 'PENDING';

export default function EvaluationDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const num = params.num!;
  const [detail, setDetail] = useState<EvaluationDetailVO | null>(null);
  const [cases, setCases] = useState<EvalCaseVO[]>([]);
  const [filter, setFilter] = useState<Filter>('ALL');
  useBreadcrumbName(detail?.num);

  useEffect(() => {
    EvalApi.detail(num).then(setDetail);
    EvalApi.cases(num).then(setCases);
  }, [num]);

  const counts = useMemo(() => {
    const out = { ALL: cases.length, PASS: 0, FAIL: 0, PENDING: 0 };
    cases.forEach((c) => {
      if (c.passed === true) out.PASS++;
      else if (c.passed === false) out.FAIL++;
      else out.PENDING++;
    });
    return out;
  }, [cases]);

  const filtered = useMemo(() => {
    if (filter === 'ALL') return cases;
    if (filter === 'PASS') return cases.filter((c) => c.passed === true);
    if (filter === 'FAIL') return cases.filter((c) => c.passed === false);
    return cases.filter((c) => c.passed === undefined || c.passed === null);
  }, [cases, filter]);

  if (!detail) return <div style={{ padding: 32 }}>加载中...</div>;

  const passPct =
    typeof detail.passRate === 'number'
      ? Math.round(detail.passRate * 100)
      : null;

  const radarOption = detail.radarData?.length
    ? {
        radar: {
          indicator: detail.radarData.map((d) => ({
            name: DIM_LABEL[d.dimension],
            max: 100,
          })),
          radius: 90,
        },
        series: [
          {
            type: 'radar',
            data: [
              {
                value: detail.radarData.map((d) => d.score),
                name: '本次评测',
                areaStyle: { color: 'rgba(59, 130, 246, 0.2)' },
                lineStyle: { color: COLOR.primary },
              },
            ],
          },
        ],
      }
    : null;

  const filterChips: { key: Filter; label: string }[] = [
    { key: 'ALL', label: `全部 ${counts.ALL}` },
    { key: 'PASS', label: `通过 ${counts.PASS}` },
    { key: 'FAIL', label: `失败 ${counts.FAIL}` },
    { key: 'PENDING', label: `未判定 ${counts.PENDING}` },
  ];

  return (
    <div
      style={{
        padding: 32,
        background: '#fff',
        minHeight: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
      }}
    >
      {/* 1. 面包屑 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 12,
          color: COLOR.textMuted,
        }}
      >
        <a
          onClick={() => navigate('/skill/evaluation')}
          style={{ color: COLOR.textMuted }}
        >
          Skill 评测
        </a>
        <RightOutlined style={{ fontSize: 9 }} />
        <span style={{ color: COLOR.textBody }}>{detail.num}</span>
      </div>

      {/* 2. 标题行 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 16,
        }}
      >
        <h1
          style={{
            margin: 0,
            fontSize: 24,
            fontWeight: 700,
            color: COLOR.textPrimary,
          }}
        >
          {detail.num}
        </h1>
        <button
          onClick={() => message.info('M2 上线 CSV 导出')}
          style={{
            background: 'none',
            border: 'none',
            color: COLOR.primaryDeep,
            fontSize: 13,
            fontWeight: 500,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <CloudDownloadOutlined />
          导出 CSV
        </button>
      </div>

      {/* 3. meta 行 */}
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        <Meta label="target" value={`${detail.targetType.toLowerCase()}/${detail.targetNum}`} mono />
        <Meta label="version" value={detail.targetVersionNum ?? '-'} />
        <Meta label="method" value={detail.mode === 'AUTO' ? '自动' : '人工'} />
        <Meta label="case" value={String(detail.caseCount ?? cases.length ?? '-')} />
        <Meta label="created" value={formatTime(detail.gmtCreate) ?? '-'} />
      </div>

      {/* 4. KPI 卡 */}
      <div style={{ display: 'flex', gap: 16 }}>
        <KpiCard
          label="总通过率"
          value={passPct !== null ? `${passPct}%` : '-'}
          big
          color={
            passPct !== null && passPct >= 90
              ? COLOR.textSuccess
              : passPct !== null && passPct >= 60
                ? COLOR.textPending
                : COLOR.textError
          }
        />
        <KpiCard label="通过" value={String(counts.PASS)} color={COLOR.textSuccess} />
        <KpiCard label="失败" value={String(counts.FAIL)} color={COLOR.textError} />
        <KpiCard label="未判定" value={String(counts.PENDING)} color={COLOR.textMuted} />
      </div>

      {/* 5. 筛选胶囊 */}
      <div style={{ display: 'flex', gap: 8 }}>
        {filterChips.map((c) => {
          const active = c.key === filter;
          return (
            <button
              key={c.key}
              onClick={() => setFilter(c.key)}
              style={{
                padding: '6px 14px',
                background: active ? COLOR.bgInfo : '#fff',
                border: `1px solid ${active ? COLOR.primaryDeep : COLOR.borderHard}`,
                color: active ? COLOR.primaryDeep : COLOR.textSecondary,
                borderRadius: 999,
                fontSize: 13,
                fontWeight: active ? 500 : 400,
                cursor: 'pointer',
              }}
            >
              {c.label}
            </button>
          );
        })}
      </div>

      {/* 6. case 表 */}
      {filtered.length === 0 ? (
        <Empty description="无 case 数据" />
      ) : (
        <div
          style={{
            border: `1px solid ${COLOR.borderHard}`,
            borderRadius: 8,
            overflow: 'hidden',
          }}
        >
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#F8FAFC' }}>
                <Th width={50}>#</Th>
                <Th>INPUT</Th>
                <Th>ACTUAL OUTPUT</Th>
                <Th>EXPECTED</Th>
                <Th width={120}>JUDGEMENT</Th>
                <Th width={80}>耗时</Th>
                <Th width={120}>操作</Th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr
                  key={c.seq}
                  style={{ borderTop: `1px solid ${COLOR.borderHard}` }}
                >
                  <Td>
                    <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>
                      {String(c.seq).padStart(3, '0')}
                    </Text>
                  </Td>
                  <Td>
                    <CodeBlock value={c.input} />
                  </Td>
                  <Td>
                    <CodeBlock value={c.actual} />
                  </Td>
                  <Td>
                    {c.expected !== undefined ? (
                      <CodeBlock value={c.expected} />
                    ) : (
                      <span style={{ color: COLOR.textMuted }}>-</span>
                    )}
                  </Td>
                  <Td>
                    <JudgeChip passed={c.passed} />
                  </Td>
                  <Td>
                    <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>
                      {c.latencyMs ? `${c.latencyMs}ms` : '-'}
                    </Text>
                  </Td>
                  <Td>
                    <CaseActions caseItem={c} />
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 7. 雷达图（兼容旧数据，Figma 没画） */}
      {radarOption ? (
        <div
          style={{
            border: `1px solid ${COLOR.borderHard}`,
            borderRadius: 8,
            padding: 16,
          }}
        >
          <div
            style={{
              fontSize: 13,
              fontWeight: 500,
              color: COLOR.textPrimary,
              marginBottom: 8,
            }}
          >
            评分雷达图（auto 评测维度分）
          </div>
          <ReactECharts option={radarOption} style={{ height: 260 }} />
        </div>
      ) : null}
    </div>
  );
}

/* =================== sub-components =================== */

function Meta(props: { label: string; value: string; mono?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ fontSize: 13, color: COLOR.textMuted }}>{props.label}</span>
      <span
        style={{
          fontSize: 13,
          fontWeight: 500,
          color: '#314158',
          fontFamily: props.mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
        }}
      >
        {props.value}
      </span>
    </div>
  );
}

function KpiCard(props: {
  label: string;
  value: string;
  big?: boolean;
  color?: string;
  trend?: { dir: 'up' | 'down' | 'flat'; text: string };
}) {
  return (
    <div
      style={{
        flex: props.big ? 1.5 : 1,
        border: `1px solid ${COLOR.borderHard}`,
        borderRadius: 8,
        padding: '14px 16px',
        background: '#fff',
      }}
    >
      <div style={{ fontSize: 12, color: COLOR.textMuted, marginBottom: 6 }}>
        {props.label}
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: 8,
        }}
      >
        <span
          style={{
            fontSize: props.big ? 28 : 22,
            fontWeight: 700,
            color: props.color ?? COLOR.textPrimary,
            lineHeight: 1,
          }}
        >
          {props.value}
        </span>
        {props.trend ? (
          <span
            style={{
              fontSize: 12,
              color:
                props.trend.dir === 'up'
                  ? COLOR.textSuccess
                  : props.trend.dir === 'down'
                    ? COLOR.textError
                    : COLOR.textMuted,
            }}
          >
            {props.trend.dir === 'up' ? <ArrowUpOutlined /> : null}
            {props.trend.dir === 'down' ? <ArrowDownOutlined /> : null}
            {props.trend.dir === 'flat' ? <MinusOutlined /> : null}
            {props.trend.text}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function JudgeChip({ passed }: { passed?: boolean }) {
  if (passed === true) {
    return (
      <span
        style={{
          background: COLOR.bgSuccess,
          color: COLOR.textSuccess,
          fontSize: 11,
          padding: '2px 8px',
          borderRadius: 4,
          fontWeight: 500,
        }}
      >
        ✓ 通过
      </span>
    );
  }
  if (passed === false) {
    return (
      <span
        style={{
          background: COLOR.bgError,
          color: COLOR.textError,
          fontSize: 11,
          padding: '2px 8px',
          borderRadius: 4,
          fontWeight: 500,
        }}
      >
        ✕ 失败
      </span>
    );
  }
  return (
    <span
      style={{
        background: COLOR.bgPending,
        color: COLOR.textPending,
        fontSize: 11,
        padding: '2px 8px',
        borderRadius: 4,
        fontWeight: 500,
      }}
    >
      ● 未判定
    </span>
  );
}

function CodeBlock({ value }: { value: unknown }) {
  if (value === undefined || value === null)
    return <span style={{ color: COLOR.textMuted }}>-</span>;
  return (
    <SyntaxHighlighter
      language="json"
      customStyle={{
        margin: 0,
        padding: 6,
        fontSize: 11,
        background: '#F8FAFC',
        maxHeight: 80,
        overflow: 'auto',
        borderRadius: 4,
      }}
    >
      {prettyJson(value)}
    </SyntaxHighlighter>
  );
}

function CaseActions({ caseItem }: { caseItem: EvalCaseVO }) {
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      {caseItem.judgeExplanation ? (
        <Tooltip title={caseItem.judgeExplanation}>
          <a style={{ fontSize: 12 }}>详情</a>
        </Tooltip>
      ) : (
        <a style={{ fontSize: 12, color: COLOR.textMuted }}>详情</a>
      )}
      {caseItem.traceId ? (
        <Tooltip title="复制 trace_id">
          <a
            onClick={async () => {
              const ok = await copyToClipboard(caseItem.traceId!);
              message[ok ? 'success' : 'error'](ok ? '已复制' : '复制失败');
            }}
            style={{ fontSize: 12 }}
          >
            <CopyOutlined /> trace
          </a>
        </Tooltip>
      ) : null}
    </div>
  );
}

function Th({ children, width }: { children: React.ReactNode; width?: number }) {
  return (
    <th
      style={{
        padding: '10px 16px',
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.06em',
        textTransform: 'uppercase',
        color: COLOR.textMuted,
        textAlign: 'left',
        width,
        borderBottom: `1px solid ${COLOR.borderHard}`,
      }}
    >
      {children}
    </th>
  );
}

function Td({ children }: { children: React.ReactNode }) {
  return (
    <td
      style={{
        padding: '14px 16px',
        fontSize: 13,
        color: COLOR.textBody,
        verticalAlign: 'top',
      }}
    >
      {children}
    </td>
  );
}
