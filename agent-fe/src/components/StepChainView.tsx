/**
 * StepChainView — Skill 步骤链可视化（Figma 节点 132:32 / 132:75 还原）
 * 视觉：浅灰底卡片 + 标题行（已执行 N 步 · type1 → type2 → ...）+ 圆点列表
 * 多色 type 映射：thinking 紫 / tool_use 绿 / tool_result 橙 / text 蓝
 *
 * 单步渲染抽到 StepNodeCard；本组件保留链式外壳（卡片背景 / 标题 / 折叠）。
 */
import { CaretRightOutlined, CaretDownOutlined } from '@ant-design/icons';
import { useState } from 'react';
import type { StepChainVO } from '@/types';
import StepNodeCard, { STEP_KIND_LABEL, inferKindFromStep } from './StepNodeCard';

export interface StepChainViewProps {
  chain: StepChainVO;
}

export default function StepChainView({ chain }: StepChainViewProps) {
  const [expanded, setExpanded] = useState(true);

  if (!chain.steps?.length) return null;

  const kinds = chain.steps.map(inferKindFromStep);
  const headerLine = `已执行 ${chain.steps.length} 步 · ${kinds
    .map((k) => STEP_KIND_LABEL[k])
    .join(' → ')}`;

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
        <span>{headerLine}</span>
      </div>

      {expanded ? (
        <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
          {chain.steps.map((step, i) => (
            <StepNodeCard key={step.stepId} step={step} kind={kinds[i]} />
          ))}
        </div>
      ) : null}
    </div>
  );
}
