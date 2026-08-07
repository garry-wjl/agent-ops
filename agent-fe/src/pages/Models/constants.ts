/**
 * 模型状态展示常量 — 列表胶囊 / 详情共用。
 * 三态色彩对齐 Sandbox 风格：草稿橙 / 启用绿 / 禁用灰。
 */
import type { ModelScope, ModelStatus } from '@/types';

/** 状态 → 中文标签 + 主色（用于 `● 标签` 胶囊）。 */
export const MODEL_STATUS_META: Record<ModelStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: '#D97706' },
  ENABLED: { label: '启用', color: '#16A34A' },
  DISABLED: { label: '禁用', color: '#64748B' },
};

/**
 * 模型归属范围展示常量（2026-06-17 scope 优化）。
 * 用于列表 / 选择器中的「系统 / 空间」Tag。
 */
export const MODEL_SCOPE_META: Record<
  ModelScope,
  { label: string; color: string; tagColor: string }
> = {
  PLATFORM: { label: '系统', color: '#7C3AED', tagColor: 'purple' },
  SPACE: { label: '空间', color: '#2563EB', tagColor: 'blue' },
};

/** 字段长度上限（与后端 ModelConstants 一致）。 */
export const MODEL_LIMITS = {
  NAME_MAX: 128,
  MODEL_ID_MAX: 128,
  REMARK_MAX: 500,
  BASE_URL_MAX: 512,
} as const;
