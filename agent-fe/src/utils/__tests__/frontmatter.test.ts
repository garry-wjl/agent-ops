import { describe, expect, it } from 'vitest';
import { applyFrontMatter, parseSkillMarkdown } from '../frontmatter';

describe('parseSkillMarkdown', () => {
  it('parses scalar name/description and ignores version for form sync', () => {
    const parsed = parseSkillMarkdown(`---
name: pdf
description: Comprehensive PDF toolkit
version: 1.0.0
---

# body`);
    expect(parsed.error).toBeNull();
    expect(parsed.data).toEqual({
      name: 'pdf',
      description: 'Comprehensive PDF toolkit',
    });
  });

  it('parses block-scalar description and still returns synced fields', () => {
    const parsed = parseSkillMarkdown(`---
name: demo
description: |
  Multi-line description
  second line
version: 1.0.0
---

# Hi`);
    expect(parsed.data.name).toBe('demo');
    expect(parsed.data.description).toBe('Multi-line description second line');
  });

  it('keeps synced fields when nested metadata exists', () => {
    const parsed = parseSkillMarkdown(`---
name: nested
description: hello
metadata:
  author: bob
version: 2.0.0
---
`);
    expect(parsed.data).toEqual({
      name: 'nested',
      description: 'hello',
    });
  });

  it('still exposes synced fields when an orphan line sets error', () => {
    const parsed = parseSkillMarkdown(`---
name: demo
description: First line
Second line without indent
version: 1.0.0
---
`);
    expect(parsed.error).toBeTruthy();
    expect(parsed.data.name).toBe('demo');
    expect(parsed.data.description).toBe('First line');
  });
});

describe('applyFrontMatter', () => {
  it('collapses block-scalar description when patching and preserves version line', () => {
    const next = applyFrontMatter(
      `---
name: demo
description: |
  old
  lines
version: 1.0.0
---

# body`,
      { description: 'new desc' },
    );
    expect(next).toContain('description: new desc');
    expect(next).not.toContain('old');
    expect(next).toContain('name: demo');
    expect(next).toContain('version: 1.0.0');
  });

  it('writes template without requiring version', () => {
    const next = applyFrontMatter('', { name: 'a', description: 'b' });
    expect(next).toContain('name: a');
    expect(next).toContain('description: b');
    expect(next).not.toContain('version:');
  });
});
