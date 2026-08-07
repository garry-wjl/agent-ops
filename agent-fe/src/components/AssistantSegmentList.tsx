/**
 * AssistantSegmentList — 按到达顺序渲染 AssistantSegment 列表
 *
 * 实时流场景使用：thinking → 折叠面板；text → Markdown；tool_use → 卡片
 * 多轮 ReAct 时严格按 segment 顺序自上而下展示，保留真实交错。
 *
 * 视觉：timeline 风格(一根贯穿竖线 + 每段圆点),对齐 VSCode 内 Claude Code 的执行过程。
 * 圆点颜色按 kind 区分;流式中的最后一段圆点带脉冲动画提示活跃状态。
 *
 * 历史消息不走这里(MessageVO 无交错信息)，仍走 Console.AssistantMessage 的降级分支。
 */
import type { AssistantSegment, StepNodeVO } from '@/types';
import { CaretDownOutlined, CaretRightOutlined } from '@ant-design/icons';
import { useState } from 'react';
import MarkdownContent from './MarkdownContent';
import StepNodeCard from './StepNodeCard';
import ThinkingPanel from './ThinkingPanel';

export interface AssistantSegmentListProps {
  segments: AssistantSegment[];
  streaming: boolean;
}

/** 圆点配色 — 与 global.less 的 --color-step-* 同源 */
function dotColorForSegment(seg: AssistantSegment): string {
  if (seg.kind === 'thinking') return 'var(--color-step-thinking)';
  if (seg.kind === 'text') return 'var(--color-step-text)';
  // tool_use
  if (seg.status === 'error') return 'var(--color-step-error)';
  if (seg.status === 'success') return 'var(--color-step-tool-result)';
  return 'var(--color-step-tool-use)';
}

export default function AssistantSegmentList({
  segments,
  streaming,
}: AssistantSegmentListProps) {
  if (!segments?.length) return null;

  return (
    <div className="agent-timeline">
      {segments.map((seg, i) => {
        const isLast = i === segments.length - 1;
        const stillStreaming = streaming && isLast;
        const dotClass = `agent-timeline-dot${
          stillStreaming ? ' agent-timeline-dot-pulse' : ''
        }`;
        const dotStyle = { background: dotColorForSegment(seg) };

        const key =
          seg.kind === 'tool_use' ? `tool-${seg.toolCallId}` : `${seg.kind}-${i}`;

        return (
          <div key={key} className="agent-timeline-item">
            <span className={dotClass} style={dotStyle} />
            {seg.kind === 'thinking' ? (
              <ThinkingPanel content={seg.text} streaming={stillStreaming} />
            ) : seg.kind === 'text' ? (
              <MarkdownContent content={seg.text} streaming={stillStreaming} />
            ) : (
              <ToolUseCard segment={seg} streaming={stillStreaming} />
            )}
          </div>
        );
      })}
    </div>
  );
}

/* ---------------- ToolUseCard ---------------- */

interface ToolUseCardProps {
  segment: Extract<AssistantSegment, { kind: 'tool_use' }>;
  streaming: boolean;
}

/**
 * 把 AssistantSegment(tool_use) 适配成 StepNodeVO 后委托给 StepNodeCard。
 * 参数展示优先用 argsBuffer parse 出来的结构化对象；parse 失败时
 * 显示原始字符串 + "参数生成中"提示（流式中常见）。
 */
function ToolUseCard({ segment, streaming }: ToolUseCardProps) {
  const [expanded, setExpanded] = useState(true);
  const parsedInput = tryParseJson(segment.argsBuffer);
  const inputForDisplay =
    parsedInput ??
    (segment.input && Object.keys(segment.input).length > 0
      ? segment.input
      : segment.argsBuffer || undefined);
  const isStreamingArgs = !parsedInput && streaming;

  const step: StepNodeVO = {
    stepId: segment.toolCallId,
    skillName: segment.toolName,
    input: inputForDisplay,
    output: segment.output,
    status: segment.status,
    latencyMs: segment.latencyMs,
    startedAt: segment.startedAt,
    error: segment.error,
  };

  return (
    <div
      style={{
        background: '#F8FAFC',
        border: '1px solid #E5E7EB',
        borderRadius: 6,
        padding: '10px 14px',
        marginBottom: 12,
      }}
    >
      <div
        onClick={() => setExpanded((v) => !v)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          cursor: 'pointer',
          color: '#64748B',
          fontSize: 12,
          fontWeight: 500,
        }}
      >
        {expanded ? <CaretDownOutlined /> : <CaretRightOutlined />}
        <span>
          工具调用
          {segment.status === 'pending' ? (isStreamingArgs ? '：参数生成中…' : '：执行中…') : ''}
        </span>
      </div>

      {expanded ? (
        <div style={{ marginTop: 8 }}>
          <StepNodeCard step={step} kind="tool_use" defaultOpen />
        </div>
      ) : null}
    </div>
  );
}

/** 容错 JSON parse：流式中参数 JSON 尾部不闭合时返回 null */
function tryParseJson(s: string | undefined): unknown | null {
  if (!s) return null;
  const trimmed = s.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed);
  } catch {
    return null;
  }
}
