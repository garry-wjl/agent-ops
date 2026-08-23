import { describe, expect, it } from 'vitest';
import {
  groupMountableChildren,
  isWholeGroupBindingKey,
  normalizeToolRefs,
  selectWholeGroup,
  summarizeToolSelection,
  toggleChildToolKey,
  toolBindingKey,
} from './toolBinding';
import type { MountableToolItem, ToolRefParam } from '@/types';

describe('toolBindingKey', () => {
  it('whole group uses toolNum only', () => {
    expect(toolBindingKey({ toolNum: 'TOOL-1' })).toBe('TOOL-1');
  });

  it('FC endpoint mirrors BE bindingKey', () => {
    const ref: ToolRefParam = {
      toolNum: 'FC-1',
      itemKind: 'FC_ENDPOINT',
      method: 'get',
      path: '/users/{id}',
    };
    expect(toolBindingKey(ref)).toBe('FC-1|FC_ENDPOINT|GET|/users/{id}');
  });

  it('MCP tool includes name', () => {
    expect(
      toolBindingKey({
        toolNum: 'MCP-1',
        itemKind: 'MCP_TOOL',
        mcpToolName: 'maps_weather',
      }),
    ).toBe('MCP-1|MCP_TOOL|maps_weather');
  });
});

describe('isWholeGroupBindingKey / groupMountableChildren', () => {
  it('detects whole-group keys', () => {
    expect(isWholeGroupBindingKey('FC-1')).toBe(true);
    expect(isWholeGroupBindingKey('FC-1|FC_ENDPOINT|GET|/a')).toBe(false);
  });

  it('lists children under a parent toolNum', () => {
    const items: MountableToolItem[] = [
      {
        bindingKey: 'FC-1|FC_ENDPOINT|GET|/a',
        toolNum: 'FC-1',
        toolName: 'G',
        toolType: 'FUNCTION_CALL',
        itemKind: 'FC_ENDPOINT',
        name: 'GET /a',
      },
      {
        bindingKey: 'FC-2|FC_ENDPOINT|POST|/b',
        toolNum: 'FC-2',
        toolName: 'H',
        toolType: 'FUNCTION_CALL',
        itemKind: 'FC_ENDPOINT',
        name: 'POST /b',
      },
    ];
    expect(groupMountableChildren(items, 'FC-1')).toHaveLength(1);
    expect(groupMountableChildren(items, 'FC-1')[0].name).toBe('GET /a');
  });
});

describe('picker selection helpers', () => {
  const childKeys = ['G|FC_ENDPOINT|GET|/a', 'G|FC_ENDPOINT|POST|/b'];

  it('selectWholeGroup clears children of that group', () => {
    const next = selectWholeGroup(
      ['OTHER', childKeys[0]],
      'G',
      childKeys,
    );
    expect(next).toEqual(['OTHER', 'G']);
  });

  it('toggleChild while whole-group switches to single child', () => {
    const next = toggleChildToolKey(['G'], 'G', childKeys[1], childKeys);
    expect(next).toEqual([childKeys[1]]);
  });

  it('toggleChild accumulates concrete tools', () => {
    let next = toggleChildToolKey([], 'G', childKeys[0], childKeys);
    next = toggleChildToolKey(next, 'G', childKeys[1], childKeys);
    expect(next).toEqual(childKeys);
  });

  it('summarizeToolSelection counts groups and items', () => {
    expect(summarizeToolSelection(['G1', childKeys[0]])).toEqual({
      groupCount: 1,
      itemCount: 1,
    });
  });
});

describe('normalizeToolRefs (dual-mode)', () => {
  it('keeps whole group + concrete on different parents', () => {
    const refs: ToolRefParam[] = [
      { toolNum: 'GROUP-A' },
      {
        toolNum: 'FC-B',
        itemKind: 'FC_ENDPOINT',
        method: 'POST',
        path: '/x',
      },
    ];
    expect(normalizeToolRefs(refs)).toHaveLength(2);
  });

  it('drops concrete under same parent when whole group selected', () => {
    const refs: ToolRefParam[] = [
      { toolNum: 'FC-1' },
      {
        toolNum: 'FC-1',
        itemKind: 'FC_ENDPOINT',
        method: 'GET',
        path: '/a',
      },
      {
        toolNum: 'FC-1',
        itemKind: 'FC_ENDPOINT',
        method: 'POST',
        path: '/b',
      },
      {
        toolNum: 'FC-2',
        itemKind: 'FC_ENDPOINT',
        method: 'GET',
        path: '/c',
      },
    ];
    const next = normalizeToolRefs(refs);
    expect(next.map(toolBindingKey)).toEqual([
      'FC-1',
      'FC-2|FC_ENDPOINT|GET|/c',
    ]);
  });

  it('keeps multiple concrete endpoints without whole group', () => {
    const refs: ToolRefParam[] = [
      {
        toolNum: 'FC-1',
        itemKind: 'FC_ENDPOINT',
        method: 'GET',
        path: '/a',
      },
      {
        toolNum: 'FC-1',
        itemKind: 'FC_ENDPOINT',
        method: 'POST',
        path: '/b',
      },
    ];
    expect(normalizeToolRefs(refs)).toHaveLength(2);
  });
});
