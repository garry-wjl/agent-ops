/**
 * SKILL.md front-matter 双向同步工具（2026-06-10 Skill 管理优化）
 *
 * 技术方案 §0-Q5 / PRD §6.2：`name` / `description` / `version` 三字段在
 * 「元信息表单 ↔ SKILL.md front-matter」之间**实时双向同步**，由前端解析/序列化，
 * 后端按结构化字段落库、不解析 SKILL.md 衔接头。
 *
 * 同步范围严格限定三字段；`tags` 不参与同步（元信息层属性，避免语义冲突）。
 *
 * 设计取舍：仅同步 3 个标量字段，故内置一个**极简 YAML 子集**解析/写回器
 * （只处理 `key: value` 标量 + `# 注释` + 引号），不引入 js-yaml 依赖；
 * 对无法识别的复杂 front-matter 不静默破坏——保留原文、解析失败时回报 error。
 */

/** 参与双向同步的 front-matter 字段。 */
export interface SkillFrontMatter {
  name?: string;
  description?: string;
  version?: string;
}

/** front-matter 解析结果。 */
export interface ParsedFrontMatter {
  /** 是否存在 `---` 包裹的 front-matter 块 */
  present: boolean;
  /** 解析出的同步字段（仅 name/description/version） */
  data: SkillFrontMatter;
  /** front-matter 之后的正文（不含分隔线） */
  body: string;
  /** YAML 解析失败时的错误信息；成功为 null（PRD §6.2 同步冲突处理：不静默覆盖） */
  error: string | null;
}

const FRONT_MATTER_RE = /^﻿?---[ \t]*\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n([\s\S]*))?$/;
/** 仅这三个 key 参与双向同步。 */
const SYNCED_KEYS = ['name', 'description', 'version'] as const;

/** 去除标量值两端的成对引号；非引号包裹原样返回。 */
function unquote(raw: string): string {
  const v = raw.trim();
  if (v.length >= 2) {
    const head = v[0];
    if ((head === '"' || head === "'") && v[v.length - 1] === head) {
      const inner = v.slice(1, -1);
      return head === '"' ? inner.replace(/\\"/g, '"').replace(/\\\\/g, '\\') : inner;
    }
  }
  return v;
}

/** 给写回的标量值按需加引号（含特殊字符 / 前后空格 / 冒号时用双引号）。 */
function quoteIfNeeded(value: string): string {
  if (value === '') return '""';
  const needs = /[:#"'\n]|^\s|\s$|^[[{>|&*!?%@`-]/.test(value);
  if (!needs) return value;
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

/**
 * 解析 SKILL.md，抽出 front-matter 三字段与正文。
 *
 * - 无 front-matter 块时 `present=false`，`data` 为空，`body` 为全文；
 * - YAML 形态异常（缺闭合 `---`、键值非法）时 `error` 非空，调用方据此标红提示。
 */
export function parseSkillMarkdown(md: string): ParsedFrontMatter {
  const text = md ?? '';
  const match = text.match(FRONT_MATTER_RE);
  if (!match) {
    // 起手是 `---` 但没有闭合 → 视为非法 front-matter，提示而非静默
    if (/^﻿?---[ \t]*\r?\n/.test(text)) {
      return {
        present: false,
        data: {},
        body: text,
        error: 'front-matter 缺少闭合的 `---` 分隔线',
      };
    }
    return { present: false, data: {}, body: text, error: null };
  }

  const yamlBlock = match[1] ?? '';
  const body = match[2] ?? '';
  const data: SkillFrontMatter = {};
  let error: string | null = null;

  const lines = yamlBlock.split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) continue;
    const colon = line.indexOf(':');
    if (colon === -1) {
      error = `front-matter 行无法解析为 key: value —— "${trimmed}"`;
      continue;
    }
    const key = line.slice(0, colon).trim();
    const valueRaw = line.slice(colon + 1);
    // 嵌套 / 列表（行首缩进或值以 - 起头的多行结构）超出同步范围，忽略不报错
    if (/^\s/.test(line) || valueRaw.trim().startsWith('-')) continue;
    if ((SYNCED_KEYS as readonly string[]).includes(key)) {
      data[key as keyof SkillFrontMatter] = unquote(valueRaw);
    }
  }

  return { present: true, data, body, error };
}

/**
 * 把表单三字段写回 SKILL.md front-matter，保留正文与其它非同步键。
 *
 * - 已存在的同步键就地更新（保持原顺序）；缺失的键追加到 front-matter 末尾；
 * - 原文无 front-matter 块时，新建一段 `---...---` 置于顶部；
 * - 值为 `undefined` 的字段不写入（让 BE/格式检测按缺字段处理）。
 */
export function applyFrontMatter(md: string, patch: SkillFrontMatter): string {
  const text = md ?? '';
  const match = text.match(FRONT_MATTER_RE);

  const renderLine = (key: string, value: string) =>
    `${key}: ${quoteIfNeeded(value)}`;

  if (!match) {
    const headLines = SYNCED_KEYS.filter(
      (k) => patch[k] !== undefined,
    ).map((k) => renderLine(k, patch[k] as string));
    if (headLines.length === 0) return text;
    return `---\n${headLines.join('\n')}\n---\n\n${text.replace(/^﻿?/, '')}`;
  }

  const yamlBlock = match[1] ?? '';
  const body = match[2] ?? '';
  const lines = yamlBlock.split(/\r?\n/);
  const handled = new Set<string>();

  const nextLines = lines.map((line) => {
    const colon = line.indexOf(':');
    if (colon === -1 || /^\s/.test(line) || line.trim().startsWith('#')) {
      return line;
    }
    const key = line.slice(0, colon).trim();
    if ((SYNCED_KEYS as readonly string[]).includes(key) && patch[key as keyof SkillFrontMatter] !== undefined) {
      handled.add(key);
      return renderLine(key, patch[key as keyof SkillFrontMatter] as string);
    }
    return line;
  });

  for (const key of SYNCED_KEYS) {
    if (patch[key] !== undefined && !handled.has(key)) {
      nextLines.push(renderLine(key, patch[key] as string));
    }
  }

  const rebuiltYaml = nextLines.join('\n');
  return `---\n${rebuiltYaml}\n---\n${body ? `\n${body}` : ''}`;
}

/** 判断两个 front-matter patch 在同步字段上是否等价（避免无谓回写引起光标跳动）。 */
export function frontMatterEquals(a: SkillFrontMatter, b: SkillFrontMatter): boolean {
  return SYNCED_KEYS.every((k) => (a[k] ?? '') === (b[k] ?? ''));
}
