import { describe, expect, it } from 'vitest';
import { DEFAULT_GRADER_MAPPING } from '@/types';
import {
  buildGraderBindingsPayload,
  defaultMappingRows,
  mappingRowsToRecord,
  newMappingRow,
  recordToMappingRows,
  syncMappingsByGraders,
} from './graderMapping';

describe('graderMapping — 默认约定', () => {
  it('defaultMappingRows 对齐 DEFAULT_GRADER_MAPPING', () => {
    const rows = defaultMappingRows();
    expect(mappingRowsToRecord(rows)).toEqual(DEFAULT_GRADER_MAPPING);
    expect(DEFAULT_GRADER_MAPPING).toEqual({
      response: '$actual_output',
      reference: '$row.reference',
    });
  });

  it('未配置 rows 时回退默认 mapping', () => {
    expect(mappingRowsToRecord(undefined)).toEqual(DEFAULT_GRADER_MAPPING);
  });
});

describe('graderMapping — 自定义变量', () => {
  it('recordToMappingRows / mappingRowsToRecord 往返保留自定义名', () => {
    const src = {
      answer: '$actual_output',
      gold: '$row.expected_answer',
      policy: '$row.policy',
      runTrace: '$trace',
    };
    expect(mappingRowsToRecord(recordToMappingRows(src))).toEqual(src);
  });

  it('支持 $row 自定义列 + 字面量数据源', () => {
    const rows = [
      { id: '1', name: 'answer', source: '$actual_output' },
      { id: '2', name: 'gold', source: '$row.expected_answer' },
      { id: '3', name: 'fixed', source: 'constant-text' },
    ];
    expect(mappingRowsToRecord(rows)).toEqual({
      answer: '$actual_output',
      gold: '$row.expected_answer',
      fixed: 'constant-text',
    });
  });

  it('空行/空白名或源被忽略；有效行保留', () => {
    expect(
      mappingRowsToRecord([
        { id: '1', name: '  ', source: '$actual_output' },
        { id: '2', name: 'answer', source: '  ' },
        { id: '3', name: ' gold ', source: ' $row.expected_answer ' },
      ]),
    ).toEqual({ gold: '$row.expected_answer' });
  });

  it('全部无效时回退默认，避免提交空 mapping', () => {
    expect(
      mappingRowsToRecord([
        { id: '1', name: '', source: '' },
        newMappingRow(),
      ]),
    ).toEqual(DEFAULT_GRADER_MAPPING);
  });

  it('同名变量后者覆盖前者', () => {
    expect(
      mappingRowsToRecord([
        { id: '1', name: 'response', source: '$actual_output' },
        { id: '2', name: 'response', source: '$row.output' },
      ]),
    ).toEqual({ response: '$row.output' });
  });
});

describe('graderMapping — 多评估器同步', () => {
  it('syncMappingsByGraders 保留已有自定义、新选中用默认、去掉未选中', () => {
    const custom = recordToMappingRows({ answer: '$actual_output' });
    const synced = syncMappingsByGraders(
      { G1: custom, G_OLD: defaultMappingRows() },
      ['G1', 'G2'],
    );
    expect(Object.keys(synced).sort()).toEqual(['G1', 'G2']);
    expect(mappingRowsToRecord(synced.G1)).toEqual({
      answer: '$actual_output',
    });
    expect(mappingRowsToRecord(synced.G2)).toEqual(DEFAULT_GRADER_MAPPING);
  });

  it('清空评估器选择时同步为空对象', () => {
    expect(
      syncMappingsByGraders({ G1: defaultMappingRows() }, []),
    ).toEqual({});
  });
});

describe('graderMapping — 创建任务载荷', () => {
  it('默认 mapping 组装为 API graders', () => {
    const payload = buildGraderBindingsPayload(
      ['G1', 'G2'],
      {
        G1: defaultMappingRows(),
        G2: defaultMappingRows(),
      },
    );
    expect(payload).toEqual([
      { graderNum: 'G1', mapping: DEFAULT_GRADER_MAPPING },
      { graderNum: 'G2', mapping: DEFAULT_GRADER_MAPPING },
    ]);
  });

  it('自定义变量组装进创建任务请求', () => {
    const customRows = recordToMappingRows({
      answer: '$actual_output',
      gold: '$row.expected_answer',
    });
    const payload = buildGraderBindingsPayload(['GLM1'], {
      GLM1: customRows,
    });
    expect(payload).toEqual([
      {
        graderNum: 'GLM1',
        mapping: {
          answer: '$actual_output',
          gold: '$row.expected_answer',
        },
      },
    ]);
  });

  it('缺失的 grader mapping 状态回退默认', () => {
    const payload = buildGraderBindingsPayload(['G_NEW'], {});
    expect(payload[0].mapping).toEqual(DEFAULT_GRADER_MAPPING);
  });
});
