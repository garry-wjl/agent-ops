import { describe, expect, it } from 'vitest';
import { fieldsToSchema, schemaToFields } from './schemaConvert';

describe('schemaConvert', () => {
  it('round-trips a nested object schema', () => {
    const fields = [
      {
        name: 'title',
        type: 'string' as const,
        required: true,
        description: '标题',
      },
      {
        name: 'meta',
        type: 'object' as const,
        required: false,
        children: [
          { name: 'score', type: 'integer' as const, required: true },
        ],
      },
    ];
    const schema = fieldsToSchema(fields);
    const back = schemaToFields(schema);
    expect(back.ok).toBe(true);
    if (!back.ok) return;
    expect(back.fields.map((field) => field.name)).toEqual(['title', 'meta']);
    expect(back.fields[0]?.required).toBe(true);
    expect(back.fields[1]?.children?.[0]?.name).toBe('score');
  });

  it('rejects schemas the field table cannot express', () => {
    const result = schemaToFields({
      type: 'object',
      anyOf: [{ type: 'object' }],
    });
    expect(result.ok).toBe(false);
  });
});
