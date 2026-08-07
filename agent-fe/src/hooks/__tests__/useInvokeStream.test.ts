/**
 * useInvokeStream — appendBlock / applyToolResult / trackReasoningChunkSnapshot 纯函数单元测试
 *
 * 覆盖目标：
 * - thinking 同类相邻合并
 * - text 同类相邻合并
 * - thinking → text → thinking 类型切换新建段
 * - tool_use fragment 协议按 toolCallId 拼接 content
 * - 不同 toolCallId 的 tool_use 新建段
 * - TOOL_RESULT 按 id 回填 output / status / latencyMs
 * - 边界：空 delta 不产生空段；不同类型块在同一帧的混合处理
 * - REASONING isLast 帧跳过：chunk 流 + PostReasoning 快照不重复追加；纯非流式 isLast 兜底
 */
import { describe, expect, it } from 'vitest';
import {
  appendBlock,
  applyToolResult,
  flattenToolOutput,
  trackReasoningChunkSnapshot,
} from '../useInvokeStream';
import type { AgentContentBlock, AgentScopeEvent, AssistantSegment } from '@/types';

function thinking(text: string): AgentContentBlock {
  return { type: 'thinking', thinking: text };
}

function text(t: string): AgentContentBlock {
  return { type: 'text', text: t };
}

function toolUseFirst(id: string, name: string, contentChunk = ''): AgentContentBlock {
  return { type: 'tool_use', id, name, input: {}, content: contentChunk };
}

function toolUseFragment(id: string, contentChunk: string): AgentContentBlock {
  return { type: 'tool_use', id, name: '__fragment__', input: {}, content: contentChunk };
}

function toolResult(id: string, output: AgentContentBlock[]): AgentContentBlock {
  return { type: 'tool_result', id, output };
}

describe('appendBlock — thinking 合并', () => {
  it('连续 5 帧 thinking → 1 个 segment', () => {
    let segs: AssistantSegment[] = [];
    for (const t of ['第', '一', '次', '思', '考']) {
      segs = appendBlock(segs, thinking(t));
    }
    expect(segs).toHaveLength(1);
    expect(segs[0]).toEqual({ kind: 'thinking', text: '第一次思考' });
  });

  it('空 delta 不创建段', () => {
    const segs = appendBlock([], thinking(''));
    expect(segs).toHaveLength(0);
  });
});

describe('appendBlock — text 合并', () => {
  it('连续 text 帧拼接', () => {
    let segs: AssistantSegment[] = [];
    for (const t of ['Hello', ', ', 'world', '!']) {
      segs = appendBlock(segs, text(t));
    }
    expect(segs).toHaveLength(1);
    expect(segs[0]).toEqual({ kind: 'text', text: 'Hello, world!' });
  });
});

describe('appendBlock — 类型切换', () => {
  it('thinking → text → thinking 产生 3 段', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, thinking('想一下'));
    segs = appendBlock(segs, text('回答 A'));
    segs = appendBlock(segs, thinking('再想'));
    expect(segs).toHaveLength(3);
    expect(segs.map((s) => s.kind)).toEqual(['thinking', 'text', 'thinking']);
    expect((segs[0] as any).text).toBe('想一下');
    expect((segs[1] as any).text).toBe('回答 A');
    expect((segs[2] as any).text).toBe('再想');
  });

  it('thinking → text → text 后段合并', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, thinking('T'));
    segs = appendBlock(segs, text('A'));
    segs = appendBlock(segs, text('B'));
    expect(segs).toHaveLength(2);
    expect((segs[1] as any).text).toBe('AB');
  });
});

describe('appendBlock — tool_use fragment 协议', () => {
  it('首帧 + 50 个 fragment → 1 个 tool_use segment，argsBuffer 完整拼接', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, toolUseFirst('call_abc', 'write_text_file', '{'));

    const parts = ['"content"', ':', ' "import time\\n', '...'];
    for (const p of parts) {
      segs = appendBlock(segs, toolUseFragment('call_abc', p));
    }
    segs = appendBlock(segs, toolUseFragment('call_abc', '}'));

    expect(segs).toHaveLength(1);
    const seg = segs[0] as Extract<AssistantSegment, { kind: 'tool_use' }>;
    expect(seg.kind).toBe('tool_use');
    expect(seg.toolCallId).toBe('call_abc');
    expect(seg.toolName).toBe('write_text_file'); // 不被后续 __fragment__ 覆盖
    expect(seg.argsBuffer).toBe('{"content": "import time\\n...}');
    expect(seg.status).toBe('pending');
  });

  it('fragment 在前（异常顺序）：toolName 兜底成 (unknown)', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, toolUseFragment('call_xyz', '{"foo": '));
    segs = appendBlock(segs, toolUseFragment('call_xyz', '"bar"}'));

    expect(segs).toHaveLength(1);
    const seg = segs[0] as Extract<AssistantSegment, { kind: 'tool_use' }>;
    expect(seg.toolName).toBe('(unknown)');
    expect(seg.argsBuffer).toBe('{"foo": "bar"}');
  });

  it('两个不同 toolCallId → 2 个 tool_use segment', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, toolUseFirst('call_1', 'tool_a', '{'));
    segs = appendBlock(segs, toolUseFragment('call_1', '}'));
    segs = appendBlock(segs, toolUseFirst('call_2', 'tool_b', '{'));
    segs = appendBlock(segs, toolUseFragment('call_2', '}'));

    expect(segs).toHaveLength(2);
    expect((segs[0] as any).toolCallId).toBe('call_1');
    expect((segs[1] as any).toolCallId).toBe('call_2');
    expect((segs[0] as any).toolName).toBe('tool_a');
    expect((segs[1] as any).toolName).toBe('tool_b');
  });

  it('多轮 ReAct 交错：thinking → tool → thinking → tool → text', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, thinking('我先查 A'));
    segs = appendBlock(segs, toolUseFirst('call_1', 'query_a'));
    segs = appendBlock(segs, thinking('A 完了再查 B'));
    segs = appendBlock(segs, toolUseFirst('call_2', 'query_b'));
    segs = appendBlock(segs, text('最终答案'));

    expect(segs.map((s) => s.kind)).toEqual([
      'thinking',
      'tool_use',
      'thinking',
      'tool_use',
      'text',
    ]);
  });
});

describe('appendBlock — 不可变性', () => {
  it('appendBlock 不修改入参数组', () => {
    const original: AssistantSegment[] = [{ kind: 'thinking', text: 'A' }];
    const copy = [...original];
    appendBlock(original, thinking('B'));
    expect(original).toEqual(copy);
  });
});

describe('applyToolResult — 回填 output', () => {
  it('按 id 匹配并填 output/status/latencyMs', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, toolUseFirst('call_1', 'query', '{"q":"hi"}'));

    const resultBlock = toolResult('call_1', [{ type: 'text', text: 'result text' }]) as Extract<
      AgentContentBlock,
      { type: 'tool_result' }
    >;
    segs = applyToolResult(segs, resultBlock, 1234);

    const seg = segs[0] as Extract<AssistantSegment, { kind: 'tool_use' }>;
    expect(seg.status).toBe('success');
    expect(seg.output).toBe('result text');
    expect(seg.latencyMs).toBe(1234);
  });

  it('未匹配 id → segments 不变', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, toolUseFirst('call_1', 'query'));
    const before = segs;
    const resultBlock = toolResult('call_unknown', []) as Extract<
      AgentContentBlock,
      { type: 'tool_result' }
    >;
    segs = applyToolResult(segs, resultBlock);
    expect(segs).toBe(before);
  });

  it('多个 tool_use 同 id 时回填最近的', () => {
    let segs: AssistantSegment[] = [];
    segs = appendBlock(segs, toolUseFirst('call_dup', 'tool_x'));
    segs = appendBlock(segs, thinking('再来一次'));
    segs = appendBlock(segs, toolUseFirst('call_dup', 'tool_x'));

    const resultBlock = toolResult('call_dup', [{ type: 'text', text: 'r' }]) as Extract<
      AgentContentBlock,
      { type: 'tool_result' }
    >;
    segs = applyToolResult(segs, resultBlock);

    // 第一个（idx=0）保持 pending；最近的（idx=2）变 success
    expect((segs[0] as any).status).toBe('pending');
    expect((segs[2] as any).status).toBe('success');
  });
});

describe('flattenToolOutput', () => {
  it('纯 text 块拼接成字符串', () => {
    expect(
      flattenToolOutput([
        { type: 'text', text: '行1' },
        { type: 'text', text: '行2' },
      ]),
    ).toBe('行1\n行2');
  });

  it('空 / undefined → undefined', () => {
    expect(flattenToolOutput(undefined)).toBeUndefined();
    expect(flattenToolOutput([])).toBeUndefined();
  });

  it('含非 text 块 → 原样返回', () => {
    const out: AgentContentBlock[] = [
      { type: 'text', text: 'a' },
      { type: 'image' as const, foo: 'bar' },
    ];
    expect(flattenToolOutput(out)).toBe(out);
  });
});

describe('trackReasoningChunkSnapshot — PostReasoning 完整快照去重', () => {
  function chunk(id: string, text: string): AgentScopeEvent {
    return {
      type: 'REASONING',
      message: { id, role: 'ASSISTANT', content: [{ type: 'text', text }] },
      isLast: false,
    };
  }
  function snapshot(id: string, text: string): AgentScopeEvent {
    return {
      type: 'REASONING',
      message: { id, role: 'ASSISTANT', content: [{ type: 'text', text }] },
      isLast: true,
    };
  }

  it('chunk 后的同 id isLast 帧应被跳过，并从集合中移除该 id', () => {
    let ids = new Set<string>();
    let r = trackReasoningChunkSnapshot(chunk('m1', '你好'), ids);
    expect(r.skip).toBe(false);
    expect(r.nextIds.has('m1')).toBe(true);
    ids = r.nextIds;

    r = trackReasoningChunkSnapshot(chunk('m1', '世界'), ids);
    expect(r.skip).toBe(false);
    expect(r.nextIds.has('m1')).toBe(true);
    ids = r.nextIds;

    r = trackReasoningChunkSnapshot(snapshot('m1', '你好世界'), ids);
    expect(r.skip).toBe(true);
    expect(r.nextIds.has('m1')).toBe(false);
  });

  it('无 chunk 历史的 isLast 帧应被保留（纯非流式 LLM）', () => {
    const r = trackReasoningChunkSnapshot(snapshot('m2', '完整答案'), new Set());
    expect(r.skip).toBe(false);
    expect(r.nextIds.has('m2')).toBe(false);
  });

  it('多 message：跳过 m1 不影响 m2 仍在累积', () => {
    let ids = new Set<string>();
    ids = trackReasoningChunkSnapshot(chunk('m1', 'A'), ids).nextIds;
    ids = trackReasoningChunkSnapshot(chunk('m2', 'B'), ids).nextIds;

    const r = trackReasoningChunkSnapshot(snapshot('m1', 'AAA'), ids);
    expect(r.skip).toBe(true);
    expect(r.nextIds.has('m1')).toBe(false);
    expect(r.nextIds.has('m2')).toBe(true);
  });

  it('缺 message.id 的 isLast 帧不跳过（兜底放行，避免误吞）', () => {
    const evt: AgentScopeEvent = {
      type: 'REASONING',
      message: { role: 'ASSISTANT', content: [{ type: 'text', text: 'x' }] },
      isLast: true,
    };
    const r = trackReasoningChunkSnapshot(evt, new Set());
    expect(r.skip).toBe(false);
  });

  it('入参集合不被修改（不可变更新）', () => {
    const ids = new Set<string>(['m1']);
    const before = new Set(ids);
    trackReasoningChunkSnapshot(snapshot('m1', 'x'), ids);
    expect(ids).toEqual(before);
  });
});
