/**
 * Skill 资源文件树工具（2026-06-10 Skill 管理优化）
 *
 * 技术方案 §4.2.1 SkillResourceFile / §6.3 资源文件树 / §13 大小与 MIME 口径。
 * 纯前端侧：把扁平 `SkillResourceFileVO[]` 组装成树、做大小核算、路径合法性即时校验、
 * 图片 File → Base64 入库串。后端会在发布检测时重做权威校验，这里只做交互期即时反馈。
 */
import type {
  SkillResourceEncoding,
  SkillResourceFileType,
  SkillResourceFileVO,
} from '@/types';

/** 根 SKILL.md 文件名（系统保留：不可删除 / 不可重命名）。 */
export const SKILL_ROOT_FILE = 'SKILL.md';

/** 大小上限：解码后原始字节 ≤ 10MB（PRD §6.5 / 技术方案 §13）。 */
export const SKILL_SIZE_LIMIT_BYTES = 10 * 1024 * 1024;

/** 单文件软上限：≤ 2MB（技术方案 §5.1 expandZip 口径，前端侧即时拦截大图）。 */
export const SKILL_SINGLE_FILE_LIMIT_BYTES = 2 * 1024 * 1024;

/** 图片 MIME 白名单（技术方案 §13）。 */
export const IMAGE_MIME_WHITELIST = [
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/svg+xml',
  'image/webp',
] as const;

/** 文本扩展名白名单（与 BE TEXT_EXTENSIONS 对齐的前端子集）。 */
const TEXT_EXTENSIONS = new Set([
  'md',
  'markdown',
  'txt',
  'json',
  'yaml',
  'yml',
  'xml',
  'csv',
  'tsv',
  'js',
  'ts',
  'jsx',
  'tsx',
  'py',
  'sh',
  'bash',
  'java',
  'go',
  'rs',
  'rb',
  'php',
  'sql',
  'html',
  'css',
  'less',
  'scss',
  'toml',
  'ini',
  'env',
  'properties',
  'gitignore',
  'dockerfile',
]);

/** 扩展名 → MIME 兜底映射（图片优先用 File.type，文本统一标 text/*）。 */
const EXT_MIME: Record<string, string> = {
  md: 'text/markdown',
  markdown: 'text/markdown',
  txt: 'text/plain',
  json: 'application/json',
  yaml: 'text/yaml',
  yml: 'text/yaml',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  svg: 'image/svg+xml',
  webp: 'image/webp',
};

/** 资源树节点（UI 用：扁平 VO + children）。 */
export interface SkillResourceTreeNode extends SkillResourceFileVO {
  children: SkillResourceTreeNode[];
}

/** 取路径末段文件名。 */
export function basename(path: string): string {
  const seg = path.split('/').filter(Boolean).pop();
  return seg ?? path;
}

/** 取路径扩展名（小写，无点）。 */
export function extname(path: string): string {
  const name = basename(path);
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
}

/** 该扩展名是否按文本处理（否则按二进制 / Base64）。 */
export function isTextExt(path: string): boolean {
  return TEXT_EXTENSIONS.has(extname(path));
}

/** 该节点是否为图片（按 mime 或扩展名判定）。 */
export function isImageNode(node: Pick<SkillResourceFileVO, 'mime' | 'path'>): boolean {
  if (node.mime && node.mime.startsWith('image/')) return true;
  return ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp'].includes(extname(node.path));
}

/** 推断 MIME：优先显式类型，其次按扩展名，最后 application/octet-stream。 */
export function guessMime(path: string, explicit?: string | null): string {
  if (explicit) return explicit;
  return EXT_MIME[extname(path)] ?? 'application/octet-stream';
}

/** 计算父路径（根节点为 null）。 */
export function parentOf(path: string): string | null {
  const idx = path.lastIndexOf('/');
  return idx >= 0 ? path.slice(0, idx) : null;
}

/**
 * 把扁平资源列表组装成树（按 parentPath 关联，FOLDER 在前、同级按 name 升序）。
 */
export function buildResourceTree(
  files: SkillResourceFileVO[],
): SkillResourceTreeNode[] {
  const nodes = new Map<string, SkillResourceTreeNode>();
  for (const f of files) {
    nodes.set(f.path, { ...f, children: [] });
  }
  const roots: SkillResourceTreeNode[] = [];
  for (const node of nodes.values()) {
    const parentPath = node.parentPath ?? parentOf(node.path);
    if (parentPath && nodes.has(parentPath)) {
      nodes.get(parentPath)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const sortRec = (list: SkillResourceTreeNode[]) => {
    list.sort((a, b) => {
      if (a.type !== b.type) return a.type === 'FOLDER' ? -1 : 1;
      // 根 SKILL.md 永远置顶
      if (a.path === SKILL_ROOT_FILE) return -1;
      if (b.path === SKILL_ROOT_FILE) return 1;
      return a.name.localeCompare(b.name);
    });
    list.forEach((n) => sortRec(n.children));
  };
  sortRec(roots);
  return roots;
}

/** UTF-8 字符串字节长度（解码后口径用于文本文件）。 */
export function utf8ByteLength(s: string): number {
  return new TextEncoder().encode(s).length;
}

/** Base64 串解码后的原始字节数（不解码、按公式估算，避免大字符串 atob 开销）。 */
export function base64ByteLength(b64: string): number {
  const clean = (b64 ?? '').replace(/=+$/, '');
  return Math.floor((clean.length * 3) / 4);
}

/** 单个资源节点的解码后字节数（FOLDER=0）。 */
export function nodeByteLength(node: SkillResourceFileVO): number {
  if (node.type === 'FOLDER' || !node.content) return 0;
  return node.encoding === 'base64'
    ? base64ByteLength(node.content)
    : utf8ByteLength(node.content);
}

/** 整棵资源树的解码后总字节数（用于 ≤10MB 大小提示）。 */
export function totalDecodedBytes(files: SkillResourceFileVO[]): number {
  return files.reduce((sum, f) => sum + nodeByteLength(f), 0);
}

/** 人类可读字节数（用于 “2.3MB / 10MB”）。 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)}MB`;
}

/**
 * 路径合法性校验（PRD §6.3.7：禁 `..` 穿越 / 绝对路径 / 同级重名）。
 * @returns 错误信息，合法返回 null。
 */
export function validateNewPath(
  path: string,
  existing: SkillResourceFileVO[],
  ignorePath?: string,
): string | null {
  const p = path.trim();
  if (!p) return '路径不能为空';
  if (p.startsWith('/')) return '不允许绝对路径';
  if (p.split('/').some((seg) => seg === '..' || seg === '.')) {
    return '路径不允许包含 .. / . 穿越';
  }
  if (/[\\:*?"<>|]/.test(p)) return '路径含非法字符';
  const dup = existing.some(
    (f) => f.path !== ignorePath && f.path.toLowerCase() === p.toLowerCase(),
  );
  if (dup) return '同级已存在同名节点';
  return null;
}

/** 资源树是否含根 SKILL.md（FILE）。 */
export function hasRootSkillMd(files: SkillResourceFileVO[]): boolean {
  return files.some((f) => f.path === SKILL_ROOT_FILE && f.type === 'FILE');
}

/** 取根 SKILL.md 节点。 */
export function getRootSkillMd(
  files: SkillResourceFileVO[],
): SkillResourceFileVO | undefined {
  return files.find((f) => f.path === SKILL_ROOT_FILE && f.type === 'FILE');
}

/** 构造一个新文件 / 文件夹节点（自动推断 encoding / mime / parentPath）。 */
export function makeNode(
  path: string,
  type: SkillResourceFileType,
  content?: string,
  encoding?: SkillResourceEncoding,
  mime?: string,
): SkillResourceFileVO {
  const node: SkillResourceFileVO = {
    path,
    type,
    name: basename(path),
    parentPath: parentOf(path),
  };
  if (type === 'FILE') {
    node.encoding = encoding ?? (isTextExt(path) ? 'text' : 'base64');
    node.mime = guessMime(path, mime);
    node.content = content ?? '';
  }
  return node;
}

/** 把 ArrayBuffer 转纯 Base64 串（不含 data: 前缀）。 */
export function arrayBufferToBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

/** 读取浏览器 File 为文本（UTF-8）。 */
export function readFileAsText(file: File): Promise<string> {
  return file.text();
}

/** 读取浏览器 File 为纯 Base64 串。 */
export async function readFileAsBase64(file: File): Promise<string> {
  const buf = await file.arrayBuffer();
  return arrayBufferToBase64(buf);
}

/** 把整个 File 转为资源节点（文本走 text，二进制走 base64）。 */
export async function fileToResourceNode(
  file: File,
  targetPath: string,
): Promise<SkillResourceFileVO> {
  if (isTextExt(targetPath)) {
    const text = await readFileAsText(file);
    return makeNode(targetPath, 'FILE', text, 'text', guessMime(targetPath, file.type));
  }
  const b64 = await readFileAsBase64(file);
  return makeNode(
    targetPath,
    'FILE',
    b64,
    'base64',
    guessMime(targetPath, file.type),
  );
}

/** 构造可用于 <img src> 的 data URI（Base64 图片预览）。 */
export function toDataUri(node: SkillResourceFileVO): string {
  const mime = guessMime(node.path, node.mime);
  return `data:${mime};base64,${node.content ?? ''}`;
}

/** 生成只含一个空 SKILL.md 的初始资源树（直接创建模式默认）。 */
export function initialResourceFiles(frontMatter: string): SkillResourceFileVO[] {
  return [makeNode(SKILL_ROOT_FILE, 'FILE', frontMatter, 'text', 'text/markdown')];
}
