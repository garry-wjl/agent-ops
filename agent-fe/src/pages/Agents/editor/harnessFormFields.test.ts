import { describe, expect, it } from 'vitest';
import {
  HARNESS_DROPPED_CONFIG_FIELDS,
  omitUnusedHarnessConfig,
} from './harnessFormFields';

describe('omitUnusedHarnessConfig', () => {
  it('drops snapshot-only fields and keeps Harness-consumed ones', () => {
    const result = omitUnusedHarnessConfig({
      name: '客服',
      temperature: 0.7,
      enablePlan: true,
      maxIters: 12,
      enableLongTermMemory: true,
      compaction: { triggerMessages: 30, keepMessages: 8 },
      userPrompt: 'hello {{x}}',
      memoryConfig: { shortTermStrategy: 'RECENT_N' },
      qps: 10,
      dailyBudget: 100,
    });

    expect(result).toEqual({
      name: '客服',
      temperature: 0.7,
      enablePlan: true,
      maxIters: 12,
      enableLongTermMemory: true,
      compaction: { triggerMessages: 30, keepMessages: 8 },
    });
    for (const key of HARNESS_DROPPED_CONFIG_FIELDS) {
      expect(result).not.toHaveProperty(key);
    }
  });
});
