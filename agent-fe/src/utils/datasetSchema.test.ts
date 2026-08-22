import { describe, expect, it } from 'vitest';
import {
  buildDataFromCaseTableEdits,
  buildEmptyCaseData,
  buildFormValuesFromDataJson,
  buildRowDataFromForm,
  flattenCaseDataToTableRows,
  flattenSchemaToTableRows,
  hydrateJsonLeaves,
  parseSchemaNodes,
} from '@/utils/datasetSchema';

describe('parseSchemaNodes', () => {
  it('expands nested object properties', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string' },
        {
          name: 'context',
          type: 'object',
          properties: {
            orderId: { type: 'string' },
            tags: { type: 'array', items: { type: 'string' } },
          },
        },
      ]),
    );
    expect(nodes[1].properties?.map((p) => p.name)).toEqual([
      'orderId',
      'tags',
    ]);
    expect(nodes[1].properties?.[1].items?.type).toBe('string');
  });
});

describe('hydrateJsonLeaves + prune', () => {
  it('builds nested row data', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string' },
        {
          name: 'context',
          type: 'object',
          properties: { orderId: { type: 'string' } },
        },
      ]),
    );
    const data = buildRowDataFromForm(
      hydrateJsonLeaves(
        { input: 'hi', context: { orderId: 'O1' } },
        nodes,
      ),
    );
    expect(data).toEqual({ input: 'hi', context: { orderId: 'O1' } });
  });
});

describe('buildFormValuesFromDataJson', () => {
  it('maps nested dataJson into form values', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string' },
        {
          name: 'context',
          type: 'object',
          properties: { orderId: { type: 'string' } },
        },
        {
          name: 'payload',
          type: 'object',
        },
      ]),
    );
    const values = buildFormValuesFromDataJson(
      JSON.stringify({
        input: 'hi',
        context: { orderId: 'O1' },
        payload: { a: 1 },
      }),
      nodes,
    );
    expect(values.input).toBe('hi');
    expect(values.context).toEqual({ orderId: 'O1' });
    expect(values.payload).toContain('"a": 1');
  });
});

describe('buildEmptyCaseData', () => {
  it('prepares nested empty structure with one array item', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string' },
        {
          name: 'context',
          type: 'object',
          properties: { orderId: { type: 'string' } },
        },
        {
          name: 'messages',
          type: 'array',
          items: {
            type: 'object',
            properties: {
              role: { type: 'string' },
              content: { type: 'string' },
            },
          },
        },
      ]),
    );
    const data = buildEmptyCaseData(nodes);
    expect(data.input).toBe('');
    expect(data.context).toEqual({ orderId: '' });
    expect(data.messages).toEqual([{ role: '', content: '' }]);
  });
});

describe('flattenCaseDataToTableRows', () => {
  it('flattens data values along schema paths', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string', description: '用户输入' },
        {
          name: 'context',
          type: 'object',
          properties: { orderId: { type: 'string' } },
        },
        {
          name: 'messages',
          type: 'array',
          items: {
            type: 'object',
            properties: {
              role: { type: 'string' },
              content: { type: 'string' },
            },
          },
        },
      ]),
    );
    const rows = flattenCaseDataToTableRows(
      nodes,
      JSON.stringify({
        input: 'hi',
        context: { orderId: 'O1' },
        messages: [{ role: 'user', content: 'hello' }],
      }),
    );
    const byPath = Object.fromEntries(rows.map((r) => [r.path, r]));
    expect(byPath.input?.value).toBe('hi');
    expect(byPath.input?.editable).toBe(true);
    expect(byPath.context?.editable).toBe(false);
    expect(byPath['context.orderId']?.value).toBe('O1');
    expect(byPath['messages[0].role']?.value).toBe('user');
    expect(byPath['messages[0].content']?.value).toBe('hello');
  });
});

describe('buildDataFromCaseTableEdits', () => {
  it('applies leaf edits back to nested object', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string' },
        {
          name: 'context',
          type: 'object',
          properties: { orderId: { type: 'string' } },
        },
      ]),
    );
    const data = buildDataFromCaseTableEdits(
      nodes,
      JSON.stringify({ input: 'hi', context: { orderId: 'O1' } }),
      { input: 'hello', 'context.orderId': 'O2' },
    );
    expect(data).toEqual({ input: 'hello', context: { orderId: 'O2' } });
  });
});

describe('flattenSchemaToTableRows', () => {
  it('flattens nested object and array of objects', () => {
    const nodes = parseSchemaNodes(
      JSON.stringify([
        { name: 'input', type: 'string', description: '用户输入' },
        {
          name: 'context',
          type: 'object',
          description: '上下文',
          properties: {
            orderId: { type: 'string', description: '订单号' },
            meta: {
              type: 'object',
              properties: { channel: { type: 'string' } },
            },
          },
        },
        {
          name: 'messages',
          type: 'array',
          items: {
            type: 'object',
            properties: {
              role: { type: 'string' },
              content: { type: 'string' },
            },
          },
        },
      ]),
    );
    const rows = flattenSchemaToTableRows(nodes);
    const paths = rows.map((r) => r.path);
    expect(paths).toContain('input');
    expect(paths).toContain('context');
    expect(paths).toContain('context.orderId');
    expect(paths).toContain('context.meta');
    expect(paths).toContain('context.meta.channel');
    expect(paths).toContain('messages');
    expect(paths).toContain('messages[]');
    expect(paths).toContain('messages[].role');
    expect(paths).toContain('messages[].content');
    expect(rows.find((r) => r.path === 'input')?.description).toBe('用户输入');
    expect(rows.find((r) => r.path === 'messages')?.type).toBe('array<object>');
  });
});
