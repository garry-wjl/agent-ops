import { describe, expect, it } from 'vitest';
import type { AssistantSegment } from '@/types';
import { normalizeSegmentsForDisplay } from '../userFormSegments';

describe('normalizeSegmentsForDisplay', () => {
  it('keeps user_form and drops request_user_input tool_use to avoid JSON cards', () => {
    const segs: AssistantSegment[] = [
      { kind: 'text', text: '前' },
      {
        kind: 'tool_use',
        toolCallId: 'c1',
        toolName: 'request_user_input',
        argsBuffer: '',
        input: {
          title: '城市',
          fields: [{ name: 'city', label: '城市', type: 'string' }],
        },
        status: 'pending',
      },
      {
        kind: 'user_form',
        status: 'submitted',
        form: {
          requestId: 'UIR1',
          title: '城市',
          fields: [{ name: 'city', label: '城市', type: 'string' }],
        },
        values: { city: '深圳' },
      },
      { kind: 'text', text: '后' },
    ];
    const out = normalizeSegmentsForDisplay(segs);
    expect(out.map((s) => s.kind)).toEqual(['text', 'user_form', 'text']);
    expect(out[1]).toMatchObject({
      kind: 'user_form',
      values: { city: '深圳' },
    });
  });

  it('synthesizes a read-only form card from legacy request_user_input tool_use', () => {
    const segs: AssistantSegment[] = [
      {
        kind: 'tool_use',
        toolCallId: 'c1',
        toolName: 'request_user_input',
        argsBuffer: '',
        input: {
          title: '第1问城市',
          fields: [{ name: 'city', label: '城市', type: 'string', required: true }],
        },
        status: 'pending',
      },
    ];
    const out = normalizeSegmentsForDisplay(segs);
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({
      kind: 'user_form',
      status: 'submitted',
      form: { title: '第1问城市', requestId: 'c1' },
    });
    if (out[0].kind === 'user_form') {
      expect(out[0].form.fields[0].name).toBe('city');
    }
  });

  it('reads submitted values from tool_use.output JSON', () => {
    const segs: AssistantSegment[] = [
      {
        kind: 'tool_use',
        toolCallId: 'c1',
        toolName: 'request_user_input',
        argsBuffer: '',
        input: {
          title: '城市',
          fields: [{ name: 'city', label: '城市', type: 'string' }],
        },
        status: 'success',
        output: JSON.stringify({ status: 'submitted', values: { city: '深圳' } }),
      },
    ];
    const out = normalizeSegmentsForDisplay(segs);
    expect(out[0]).toMatchObject({
      kind: 'user_form',
      values: { city: '深圳' },
    });
  });
});
