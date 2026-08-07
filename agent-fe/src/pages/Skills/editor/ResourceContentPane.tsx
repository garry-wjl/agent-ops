/**
 * 资源内容编辑/预览面板（直接创建模式右栏）— PRD §6.3.4 / §7.2。
 *
 * - 选中 SKILL.md / 其它文本文件 → Monaco 源码编辑器（onChange 回写 content）
 * - 选中图片 → Base64 预览（只读）
 * - 选中文件夹 / 未选 → 占位说明
 */
import Editor from '@monaco-editor/react';
import { Empty } from 'antd';
import type { SkillResourceFileVO } from '@/types';
import {
  SKILL_ROOT_FILE,
  extname,
  formatBytes,
  isImageNode,
  nodeByteLength,
  toDataUri,
} from '@/utils/skillResource';
import { COLOR, MONACO_OPTIONS } from './constants';

interface Props {
  node?: SkillResourceFileVO;
  disabled?: boolean;
  onContentChange: (path: string, content: string) => void;
}

/** 扩展名 → Monaco language。 */
function monacoLang(path: string): string {
  const ext = extname(path);
  const map: Record<string, string> = {
    md: 'markdown',
    markdown: 'markdown',
    json: 'json',
    yaml: 'yaml',
    yml: 'yaml',
    js: 'javascript',
    ts: 'typescript',
    jsx: 'javascript',
    tsx: 'typescript',
    py: 'python',
    sh: 'shell',
    bash: 'shell',
    html: 'html',
    css: 'css',
    xml: 'xml',
    sql: 'sql',
  };
  return map[ext] ?? 'plaintext';
}

export default function ResourceContentPane(props: Props) {
  const { node, disabled, onContentChange } = props;

  if (!node || node.type === 'FOLDER') {
    return (
      <div
        style={{
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Empty
          description={
            node?.type === 'FOLDER'
              ? '文件夹节点 —— 在左侧选择文件以编辑'
              : '在左侧选择一个文件进行编辑 / 预览'
          }
        />
      </div>
    );
  }

  // 图片：Base64 预览（只读）
  if (isImageNode(node)) {
    return (
      <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <PaneHeader node={node} />
        <div
          style={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: COLOR.headerBg,
            overflow: 'auto',
            padding: 16,
          }}
        >
          <img
            src={toDataUri(node)}
            alt={node.name}
            style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
          />
        </div>
      </div>
    );
  }

  // 文本 / SKILL.md：Monaco
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <PaneHeader node={node} />
      <div style={{ flex: 1, minHeight: 0 }}>
        <Editor
          height="100%"
          language={monacoLang(node.path)}
          path={node.path}
          value={node.content ?? ''}
          onChange={(v) => onContentChange(node.path, v ?? '')}
          options={{ ...MONACO_OPTIONS, readOnly: disabled }}
        />
      </div>
    </div>
  );
}

function PaneHeader({ node }: { node: SkillResourceFileVO }) {
  const isRoot = node.path === SKILL_ROOT_FILE;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '8px 12px',
        borderBottom: `1px solid ${COLOR.border}`,
        fontSize: 12,
      }}
    >
      <span
        style={{
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
          color: COLOR.textBody,
        }}
      >
        {node.path}
      </span>
      {isRoot && (
        <span
          style={{
            background: COLOR.selfBg,
            color: COLOR.selfText,
            fontSize: 11,
            padding: '1px 8px',
            borderRadius: 999,
          }}
        >
          根 · 双向同步
        </span>
      )}
      <span style={{ marginLeft: 'auto', color: COLOR.textMuted }}>
        {node.encoding === 'base64' ? 'Base64' : 'UTF-8'} ·{' '}
        {formatBytes(nodeByteLength(node))}
      </span>
    </div>
  );
}
