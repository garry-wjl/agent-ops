/**
 * Prompt 中心展示常量与前端校验器 — 列表 / 详情 / 编辑器共用。
 *
 * 校验对齐技术方案 §4.2.1 领域模型 / §4.2.3 领域规则：
 * - promptKey 必填、≤128；templateContent 必填、≤20000；description ≤500；
 * - tags ≤20 个、单标签 ≤32 字符、无空白项。
 * 校验器为纯函数，供编辑器实时校验与「保存」按钮 disabled 判定。
 */

/** 各字段约束上限（与技术方案 §4.2.1 / §8.1 一致）。 */
export const PROMPT_LIMITS = {
  /** 引用键最大长度 */
  KEY_MAX: 128,
  /** 描述最大长度 */
  DESC_MAX: 500,
  /** 模板原文最大字符数 */
  TEMPLATE_MAX: 20000,
  /** 单标签最大长度 */
  TAG_MAX: 32,
  /** 标签数量上限 */
  TAG_COUNT_MAX: 20,
} as const;

/**
 * promptKey 推荐形态：点分小写命名（如 greeting.intro）。
 * 仅做软提示，不强制；后端唯一性以 (workspaceNum, promptKey) 为准。
 */
export const PROMPT_KEY_HINT =
  "建议用点分命名，如 greeting.intro / order.refund";

/** 校验结果通用形态。 */
export interface ValidateResult {
  ok: boolean;
  error?: string;
}

/** 校验标签数组（数量 / 单标签长度 / 空白项）。 */
export function validateTags(tags: string[] = []): ValidateResult {
  if (tags.length > PROMPT_LIMITS.TAG_COUNT_MAX) {
    return { ok: false, error: `标签不超过 ${PROMPT_LIMITS.TAG_COUNT_MAX} 个` };
  }
  for (const t of tags) {
    if (!t.trim()) return { ok: false, error: "存在空白标签" };
    if (t.length > PROMPT_LIMITS.TAG_MAX) {
      return {
        ok: false,
        error: `标签「${t}」超过 ${PROMPT_LIMITS.TAG_MAX} 字符`,
      };
    }
  }
  return { ok: true };
}
