import { describe, expect, it } from 'vitest';
import { DEFAULT_SANDBOX_SPEC } from '../SandboxSpecSection';

describe('DEFAULT_SANDBOX_SPEC', () => {
  it('enables sandbox by default with baseline resources', () => {
    expect(DEFAULT_SANDBOX_SPEC.sandboxEnabled).toBe(true);
    expect(DEFAULT_SANDBOX_SPEC.sandboxCpu).toBe(1);
    expect(DEFAULT_SANDBOX_SPEC.sandboxMemoryMb).toBe(2048);
    expect(DEFAULT_SANDBOX_SPEC.sandboxAliveMinutes).toBe(10);
    expect(DEFAULT_SANDBOX_SPEC.sandboxMaxConcurrent).toBe(8);
  });
});
