import { describe, expect, it } from 'vitest';
import {
  buildAgentDatasetSchema,
  extractPromptContextKeys,
} from '@/types/evaluation';

describe('extractPromptContextKeys', () => {
  it('extracts unique vars and skips SESSION_NUM', () => {
    expect(
      extractPromptContextKeys(
        '订单 {{orderId}} 用户 {{userId}} 会话 {{SESSION_NUM}} 再 {{orderId}}',
      ),
    ).toEqual(['orderId', 'userId']);
  });

  it('returns empty for blank prompt', () => {
    expect(extractPromptContextKeys('')).toEqual([]);
    expect(extractPromptContextKeys(null)).toEqual([]);
  });
});

describe('buildAgentDatasetSchema', () => {
  it('embeds agent name and context properties from prompt', () => {
    const schema = buildAgentDatasetSchema({
      agentNum: 'AGT1',
      agentName: '客服助手',
      systemPrompt: '当前订单：{{orderId}}',
    });
    const parsed = JSON.parse(schema) as Array<Record<string, unknown>>;
    expect(parsed).toHaveLength(3);
    expect(parsed[0].name).toBe('input');
    expect(String(parsed[0].description)).toContain('客服助手');
    const ctx = parsed[2] as {
      name: string;
      properties?: Record<string, unknown>;
    };
    expect(ctx.name).toBe('context');
    expect(ctx.properties).toHaveProperty('orderId');
  });

  it('works without prompt vars', () => {
    const schema = buildAgentDatasetSchema({ agentNum: 'AGT2' });
    const parsed = JSON.parse(schema) as Array<{ name: string }>;
    expect(parsed.map((f) => f.name)).toEqual([
      'input',
      'reference',
      'context',
    ]);
  });
});
