/**
 * 沙箱状态展示常量 — 列表胶囊 / 详情共用。
 * 色彩对齐 PRD §7.2.8：草稿灰 / 初始化蓝 / 在线绿 / 下线橙 / 失败红。
 */
import type { SandboxStatus, SandboxType } from "@/types";

/** 状态 → 中文标签 + 主色（用于 `● 标签` 胶囊）。 */
export const SANDBOX_STATUS_META: Record<
  SandboxStatus,
  { label: string; color: string }
> = {
  DRAFT: { label: "草稿", color: "#D97706" },
  INITIALIZED: { label: "初始化", color: "#2563EB" },
  ONLINE: { label: "在线", color: "#16A34A" },
  OFFLINE: { label: "下线", color: "#EA580C" },
  FAILED: { label: "失败", color: "#DC2626" },
};

/** 沙箱类型 → 中文标签（本期仅 CODE）。 */
export const SANDBOX_TYPE_LABEL: Record<SandboxType, string> = {
  CODE: "代码沙箱",
};

/** 规格可编辑（全字段）的状态：仅草稿 / 失败态。其余态后端仅接受备注变更。 */
export function isSpecEditable(status: SandboxStatus): boolean {
  return status === "DRAFT" || status === "FAILED";
}

/** CPU / 内存 / 存活时间区间常量（与后端 SandboxConstants 一致）。 */
export const SANDBOX_LIMITS = {
  NAME_MAX: 64,
  REMARK_MAX: 100,
  CPU_MIN: 0.5,
  CPU_MAX: 16,
  CPU_STEP: 0.5,
  MEMORY_MIN: 128,
  MEMORY_MAX: 65536,
  ALIVE_MIN: 1,
  ALIVE_MAX: 1440,
} as const;
