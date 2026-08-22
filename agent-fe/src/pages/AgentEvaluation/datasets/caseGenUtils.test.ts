import { describe, expect, it } from 'vitest';
import {
  caseGenDebugVersionValue,
  caseGenStatusColor,
  caseGenStatusLabel,
} from './caseGenUtils';

describe('caseGenUtils', () => {
  it('maps status labels', () => {
    expect(caseGenStatusLabel('PENDING')).toBe('排队中');
    expect(caseGenStatusLabel('RUNNING')).toBe('生成中');
    expect(caseGenStatusLabel('FINISHED')).toBe('已完成');
    expect(caseGenStatusLabel('FAILED')).toBe('失败');
    expect(caseGenStatusLabel('CANCELLED')).toBe('已取消');
    expect(caseGenStatusLabel(undefined)).toBe('-');
  });

  it('maps status colors', () => {
    expect(caseGenStatusColor('FINISHED')).toBe('success');
    expect(caseGenStatusColor('FAILED')).toBe('error');
    expect(caseGenStatusColor('RUNNING')).toBe('processing');
    expect(caseGenStatusColor('PENDING')).toBe('processing');
    expect(caseGenStatusColor('OTHER')).toBe('default');
  });

  it('debug version value uses DRAFT literal', () => {
    expect(caseGenDebugVersionValue({ status: 'DRAFT', versionNum: null })).toBe(
      'DRAFT',
    );
    expect(
      caseGenDebugVersionValue({ status: 'PUBLISHED', versionNum: 'v1.0.0' }),
    ).toBe('v1.0.0');
  });
});
