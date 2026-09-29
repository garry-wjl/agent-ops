/**
 * SandboxSpecSection 环境变量行 ↔ Record 互转。
 */
import { describe, expect, it } from 'vitest';
import {
  DEFAULT_SANDBOX_SPEC,
  envRecordToRows,
  envRowsToRecord,
} from '../SandboxSpecSection';

describe('SandboxSpecSection env helpers', () => {
  it('DEFAULT_SANDBOX_SPEC keeps sandbox on by default', () => {
    expect(DEFAULT_SANDBOX_SPEC.sandboxEnabled).toBe(true);
    expect(DEFAULT_SANDBOX_SPEC.sandboxCpu).toBe(1);
    expect(DEFAULT_SANDBOX_SPEC.sandboxEnv).toEqual([]);
  });

  it('envRecordToRows maps object to rows', () => {
    expect(envRecordToRows({ API_KEY: 'secret', FLAG: '1' })).toEqual([
      { key: 'API_KEY', value: 'secret' },
      { key: 'FLAG', value: '1' },
    ]);
    expect(envRecordToRows(null)).toEqual([]);
    expect(envRecordToRows(undefined)).toEqual([]);
  });

  it('envRowsToRecord skips blank keys', () => {
    expect(
      envRowsToRecord([
        { key: ' FOO ', value: 'bar' },
        { key: '', value: 'x' },
        { key: '  ', value: 'y' },
      ]),
    ).toEqual({ FOO: 'bar' });
    expect(envRowsToRecord([])).toBeUndefined();
    expect(envRowsToRecord(null)).toBeUndefined();
  });
});
