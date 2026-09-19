/**
 * Harness Agent 创建页字段取舍。
 *
 * 运行时 {@code HarnessAgent.builder()} 仍读取 temperature / enablePlan（→ enableTaskList）
 * / maxIters。userPrompt、memoryConfig、qps、dailyBudget 只落快照、装配时不读，提交时去掉。
 */
export const HARNESS_DROPPED_CONFIG_FIELDS = [
  'userPrompt',
  'memoryConfig',
  'qps',
  'dailyBudget',
] as const;

export type HarnessDroppedConfigField =
  (typeof HARNESS_DROPPED_CONFIG_FIELDS)[number];

export function omitUnusedHarnessConfig<T extends object>(payload: T): T {
  const next: Record<string, unknown> = { ...(payload as Record<string, unknown>) };
  for (const key of HARNESS_DROPPED_CONFIG_FIELDS) {
    delete next[key];
  }
  return next as T;
}
