/**
 * Agent 编辑器共享展示组件 —— Agent 配置优化（2026-06-11）
 *
 * 抽出创建页与详情页共用的小组件，避免详情页反向依赖整页编辑器模块：
 * - RequiredLabel：必填项标签（红色 * 统一在文字前）。
 * - ModelMetaCard：选中模型只读元信息卡片（名称 / 模型 ID / API Key 脱敏 / Base URL），
 *   保证创建页与详情页的模型卡片展示一致。
 *   2026-06-17 scope 优化：模型来源改为 selectable 接口（不含 Key），卡片不再展示 API Key，
 *   改展示归属（系统 / 空间）Tag。
 */
import { Tag } from 'antd';
import type { ModelSelectableVO } from '@/types';

const COLOR = {
  textMuted: '#90A1B9',
  textSecondary: '#45556C',
  bgInfo: '#EFF6FF',
  borderInfo: '#DBEAFE',
} as const;

/** 模型归属 Tag 配色（与 Models/constants MODEL_SCOPE_META 对齐）。 */
const SCOPE_TAG: Record<string, { label: string; color: string }> = {
  PLATFORM: { label: '系统', color: 'purple' },
  SPACE: { label: '空间', color: 'blue' },
};

/** 必填标签：红色 * 统一放在文字前面。 */
export function RequiredLabel({ text }: { text: string }) {
  return (
    <span>
      <span style={{ color: '#DC2626', marginRight: 4 }}>*</span>
      {text}
    </span>
  );
}

/**
 * 选中模型只读元信息卡片（创建页与详情页共用）。
 * 2026-06-17：selectable 不含 Key，卡片展示归属 Tag + 名称 / 模型 ID / Base URL。
 */
export function ModelMetaCard({ model }: { model: ModelSelectableVO }) {
  const scopeMeta = SCOPE_TAG[model.scope] ?? SCOPE_TAG.SPACE;
  return (
    <div
      style={{
        background: COLOR.bgInfo,
        border: `1px solid ${COLOR.borderInfo}`,
        borderRadius: 8,
        padding: '12px 16px',
        display: 'grid',
        gridTemplateColumns: 'repeat(3, minmax(0,1fr))',
        gap: '8px 24px',
        maxWidth: 480,
      }}
    >
      <MetaItem
        label="归属"
        valueNode={<Tag color={scopeMeta.color}>{scopeMeta.label}</Tag>}
      />
      <MetaItem label="名称" value={model.name} />
      <MetaItem label="模型 ID" value={model.modelId} mono />
      <MetaItem label="Base URL" value={model.baseUrl} mono span={3} />
    </div>
  );
}

function MetaItem({
  label,
  value,
  valueNode,
  mono,
  span,
}: {
  label: string;
  value?: string;
  valueNode?: React.ReactNode;
  mono?: boolean;
  span?: number;
}) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        gridColumn: span ? `span ${span}` : undefined,
      }}
    >
      <span style={{ fontSize: 12, color: COLOR.textMuted }}>{label}</span>
      <span
        style={{
          fontSize: 13,
          color: COLOR.textSecondary,
          wordBreak: 'break-all',
          fontFamily: mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
        }}
      >
        {valueNode ?? value}
      </span>
    </div>
  );
}
