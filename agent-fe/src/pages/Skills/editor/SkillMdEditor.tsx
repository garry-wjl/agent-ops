/**
 * SKILL.md 富 Markdown 编辑器封装（基于 md-editor-rt）。
 *
 * 对应「双模式创建 · 直接创建」编辑器右栏 / SKILL.md Tab：
 * - 工具栏（加粗 / 斜体 / 标题 / 引用 / 代码 / 表格 / 列表 / 任务）+ 左编辑右预览分屏 + 全屏
 * - 图片以本地 Base64 内嵌，不走对象存储（noUploadImg：禁用上传弹窗，统一由资源树管理图片）
 *
 * 受控：value + onChange；front-matter 双向同步逻辑在父级处理。
 */
import { useMemo } from 'react';
import { MdEditor } from 'md-editor-rt';
import type { ToolbarNames } from 'md-editor-rt';
import 'md-editor-rt/lib/style.css';

interface Props {
  value: string;
  onChange: (v: string) => void;
  readOnly?: boolean;
  /** 编辑器唯一 id（多实例时区分） */
  id?: string;
  height?: number | string;
}

/** 工具栏：保留常用排版项，去掉图片上传 / mermaid / katex / github（与本场景无关）。 */
const TOOLBARS: ToolbarNames[] = [
  'bold',
  'italic',
  'strikeThrough',
  '-',
  'title',
  'sub',
  'sup',
  'quote',
  '-',
  'link',
  'codeRow',
  'code',
  'table',
  '-',
  'unorderedList',
  'orderedList',
  'task',
  '=',
  'pageFullscreen',
  'preview',
  'previewOnly',
  'catalog',
];

export default function SkillMdEditor(props: Props) {
  const { value, onChange, readOnly, id = 'skill-md-editor', height = '100%' } =
    props;

  const style = useMemo(() => ({ height }), [height]);

  return (
    <MdEditor
      id={id}
      value={value}
      onChange={onChange}
      readOnly={readOnly}
      language="zh-CN"
      theme="light"
      previewTheme="github"
      toolbars={TOOLBARS}
      noUploadImg
      footers={['markdownTotal', '=', 'scrollSwitch']}
      style={style}
    />
  );
}
