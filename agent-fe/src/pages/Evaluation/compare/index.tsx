/**
 * 评测对比 — `/skill/evaluation/compare`
 * 像素级还原 Figma 节点 116:35（AgentSphere · Eval - Compare）
 * 视觉：面包屑 + 标题 + 输入区（评测 A / B）+ KPI 卡（v3 / v2 / 总差）+ 维度差异表
 * ⚠️ Figma 双列 case 对比表本期不实现（需要后端补 cases 对比 API），加 D-tech-debt
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Empty, Form, Input, Typography } from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  MinusOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { EvalApi } from '@/services';
import type { EvalCompareVO, EvalDimension } from '@/types';

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
} as const;

const DIM_LABEL: Record<EvalDimension, string> = {
  ACCURACY: '准确性',
  RELEVANCE: '相关性',
  COMPLETENESS: '完整性',
  STYLE: '风格',
  COMPLIANCE: '合规',
};

export default function EvaluationComparePage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [data, setData] = useState<EvalCompareVO | null>(null);

  const handleCompare = async () => {
    const v = await form.validateFields();
    const res = await EvalApi.compare(v.evalNumA, v.evalNumB);
    setData(res);
  };

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
        <span style={{ color: COLOR.textBody }}>跨版本对比</span>
      </div>

      {/* 2. 标题 */}
      <h1
        style={{
          margin: 0,
          fontSize: 24,
          fontWeight: 700,
          color: COLOR.textPrimary,
        }}
      >
        跨版本评测对比
      </h1>

      {/* 3. 选择条 */}
      <div
        style={{
          padding: '14px 16px',
          border: `1px solid ${COLOR.borderHard}`,
          borderRadius: 8,
          background: '#F8FAFC',
        }}
      >
        <Form form={form} layout="inline" onFinish={handleCompare}>
          <Form.Item
            label={<Text style={{ color: COLOR.textMuted, fontSize: 12 }}>Base (A)</Text>}
            name="evalNumA"
            rules={[{ required: true }]}
          >
            <Input placeholder="EVL..." style={{ width: 240 }} />
          </Form.Item>
          <RightOutlined style={{ fontSize: 12, color: COLOR.textMuted, alignSelf: 'center' }} />
          <Form.Item
            label={<Text style={{ color: COLOR.textMuted, fontSize: 12 }}>Target (B)</Text>}
            name="evalNumB"
            rules={[{ required: true }]}
            style={{ marginLeft: 12 }}
          >
            <Input placeholder="EVL..." style={{ width: 240 }} />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            style={{ background: COLOR.primary }}
          >
            对比
          </Button>
        </Form>
      </div>

      {!data ? (
        <Empty style={{ marginTop: 48 }} description="输入两个评测编号查看差异" />
      ) : (
        <>
          {/* 4. KPI 卡 */}
          <div style={{ display: 'flex', gap: 16 }}>
            <KpiCard
              label={`Base (${data.vA})`}
              value="—"
              subLabel={`${data.targetType.toLowerCase()}/${data.targetNum}`}
            />
            <KpiCard
              label={`Target (${data.vB})`}
              value="—"
              subLabel={`${data.targetType.toLowerCase()}/${data.targetNum}`}
            />
            <KpiCard
              label="总分差 (B - A)"
              value={
                data.totalScoreDiff > 0
                  ? `+${data.totalScoreDiff.toFixed(1)}`
                  : data.totalScoreDiff.toFixed(1)
              }
              valueColor={
                data.totalScoreDiff > 0
                  ? COLOR.textSuccess
                  : data.totalScoreDiff < 0
                    ? COLOR.textError
                    : COLOR.textMuted
              }
              big
            />
            <KpiCard
              label="通过率差"
              value={`${data.passRateDiff > 0 ? '+' : ''}${(data.passRateDiff * 100).toFixed(1)}%`}
              valueColor={
                data.passRateDiff > 0
                  ? COLOR.textSuccess
                  : data.passRateDiff < 0
                    ? COLOR.textError
                    : COLOR.textMuted
              }
            />
            <KpiCard
              label="时延差"
              value={`${data.latencyDiff > 0 ? '+' : ''}${data.latencyDiff} ms`}
              valueColor={
                data.latencyDiff < 0
                  ? COLOR.textSuccess
                  : data.latencyDiff > 0
                    ? COLOR.textError
                    : COLOR.textMuted
              }
            />
          </div>

          {/* 5. 维度差异表 */}
          <div>
            <h2
              style={{
                fontSize: 16,
                fontWeight: 500,
                color: COLOR.textPrimary,
                margin: '0 0 12px',
              }}
            >
              维度差异
            </h2>
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
                    <Th>维度</Th>
                    <Th width={140}>差异 (B - A)</Th>
                    <Th width={120}>趋势</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.dimensionDiffs.map((d) => (
                    <tr
                      key={d.dimension}
                      style={{ borderTop: `1px solid ${COLOR.borderHard}` }}
                    >
                      <Td>
                        <Text style={{ fontSize: 13, color: COLOR.textBody }}>
                          {DIM_LABEL[d.dimension] ?? d.dimension}
                        </Text>
                      </Td>
                      <Td>
                        <DiffChip diff={d.diff} />
                      </Td>
                      <Td>
                        {d.diff > 0 ? (
                          <span style={{ color: COLOR.textSuccess, fontSize: 12 }}>
                            <ArrowUpOutlined /> 提升
                          </span>
                        ) : d.diff < 0 ? (
                          <span style={{ color: COLOR.textError, fontSize: 12 }}>
                            <ArrowDownOutlined /> 回退
                          </span>
                        ) : (
                          <span style={{ color: COLOR.textMuted, fontSize: 12 }}>
                            <MinusOutlined /> 持平
                          </span>
                        )}
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function KpiCard(props: {
  label: string;
  value: string;
  subLabel?: string;
  valueColor?: string;
  big?: boolean;
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
          fontSize: props.big ? 24 : 20,
          fontWeight: 700,
          color: props.valueColor ?? COLOR.textPrimary,
          lineHeight: 1.2,
        }}
      >
        {props.value}
      </div>
      {props.subLabel ? (
        <div
          style={{
            fontSize: 11,
            color: COLOR.textMuted,
            marginTop: 4,
            fontFamily:
              'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
          }}
        >
          {props.subLabel}
        </div>
      ) : null}
    </div>
  );
}

function DiffChip({ diff }: { diff: number }) {
  const sign = diff > 0 ? '+' : '';
  const color =
    diff > 0 ? COLOR.textSuccess : diff < 0 ? COLOR.textError : COLOR.textMuted;
  const bg =
    diff > 0 ? COLOR.bgSuccess : diff < 0 ? COLOR.bgError : '#F1F5F9';
  return (
    <span
      style={{
        background: bg,
        color,
        padding: '2px 10px',
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 500,
      }}
    >
      {sign}
      {diff.toFixed(2)}
    </span>
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
      }}
    >
      {children}
    </td>
  );
}
