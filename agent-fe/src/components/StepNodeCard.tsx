/**
 * StepNodeCard — StepChainView 单步可视化（圆点 + 标题 + 可展开 input/output）
 *
 * 从 StepChainView 抽出，供 AssistantSegmentList 的 ToolUseCard 复用。
 * StepChainView 保留外壳（卡片背景 / 标题 / 折叠 / 链路汇总），单步交给本组件。
 */
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { useState } from 'react';
import type { StepNodeVO } from '@/types';
import { prettyJson } from '@/utils/format';

export type StepKind = 'thinking' | 'tool_use' | 'tool_result' | 'text';

export const STEP_KIND_COLOR: Record<StepKind, string> = {
  thinking: '#7C3AED',
  tool_use: '#059669',
  tool_result: '#D97706',
  text: '#2563EB',
};

export const STEP_KIND_LABEL: Record<StepKind, string> = {
  thinking: 'thinking',
  tool_use: 'tool_use',
  tool_result: 'tool_result',
  text: 'text',
};

/** 从历史持久化的 StepNodeVO 推断 kind（名字关键词 + index 兜底） */
export function inferKindFromStep(step: StepNodeVO, index: number): StepKind {
  const name = (step.skillName ?? '').toLowerCase();
  if (name.includes('think') || name.includes('plan')) return 'thinking';
  if (name.includes('result') || name.includes('output')) return 'tool_result';
  if (name.includes('text') || name.includes('compose')) return 'text';
  if (name.includes('tool') || name.includes('call') || name.includes('skill'))
    return 'tool_use';
  const cycle: StepKind[] = ['thinking', 'tool_use', 'tool_result', 'text'];
  return cycle[index % cycle.length] as StepKind;
}

function summarize(step: StepNodeVO): string {
  const name = step.skillName ?? 'step';
  const ms = step.latencyMs;
  const t =
    typeof ms === 'number'
      ? ` +${(ms / 1000).toFixed(ms < 100 ? 2 : 1)}s`
      : '';
  return `${name}${t}`;
}

export interface StepNodeCardProps {
  step: StepNodeVO;
  kind: StepKind;
  /** 默认展开 input/output。若传 false，则点击才展开 */
  defaultOpen?: boolean;
}

export default function StepNodeCard({ step, kind, defaultOpen = false }: StepNodeCardProps) {
  const hasDetail = step.input !== undefined || step.output !== undefined;
  const [open, setOpen] = useState(defaultOpen);
  const color = STEP_KIND_COLOR[kind];

  return (
    <div>
      <div
        onClick={() => hasDetail && setOpen((v) => !v)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 11,
          color: '#0F172B',
          cursor: hasDetail ? 'pointer' : 'default',
        }}
      >
        <span
          style={{
            display: 'inline-block',
            width: 6,
            height: 6,
            borderRadius: '50%',
            background: step.status === 'error' ? '#EF4444' : color,
            flexShrink: 0,
          }}
        />
        <span style={{ flex: 1 }}>{summarize(step)}</span>
        {step.status === 'error' && step.error ? (
          <span style={{ color: '#EF4444' }}>{step.error}</span>
        ) : null}
      </div>

      {hasDetail && open ? (
        <div
          style={{
            marginLeft: 14,
            marginTop: 6,
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr)',
            gap: 6,
          }}
        >
          {step.input !== undefined ? <Block label="input" value={step.input} /> : null}
          {step.output !== undefined ? <Block label="output" value={step.output} /> : null}
        </div>
      ) : null}
    </div>
  );
}

function Block({ label, value }: { label: string; value: unknown }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div
        style={{
          color: '#94A3B8',
          fontSize: 11,
          marginBottom: 2,
          fontFamily:
            'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
        }}
      >
        {label}
      </div>
      <SyntaxHighlighter
        language="json"
        customStyle={{
          margin: 0,
          padding: 8,
          fontSize: 11,
          background: '#fff',
          border: '1px solid #E5E7EB',
          borderRadius: 4,
          maxWidth: '100%',
          overflowX: 'auto',
          whiteSpace: 'pre',
          wordBreak: 'normal',
          overflowWrap: 'normal',
        }}
        codeTagProps={{
          style: {
            whiteSpace: 'pre',
            wordBreak: 'normal',
            overflowWrap: 'normal',
          },
        }}
      >
        {prettyJson(value)}
      </SyntaxHighlighter>
    </div>
  );
}
