/**
 * 调试台 Agent 回复的 Markdown 渲染器。
 * <p>
 * 内部用 {@link https://x.ant.design/x-markdowns/introduce-cn @ant-design/x-markdown}（X v2 子包）：
 *  - 原生支持流式（`streaming`）：不完整 markdown 不会闪烁，token 流入边渲染边补全
 *  - 自带代码高亮 / Mermaid / LaTeX 等插件
 *  - peer 仅要 React >= 18，与项目 React 18 + antd 5 兼容
 *
 * 入口在 {@code Console} 的 `AssistantMessage`；当 `stream.loading=true` 时把 `streaming` 打开。
 */
import { XMarkdown } from '@ant-design/x-markdown';

export interface MarkdownContentProps {
  content: string;
  /** 是否处于流式接收阶段；流式期 XMarkdown 会做尾部缓冲 + 光标提示 */
  streaming?: boolean;
}

export default function MarkdownContent({ content, streaming = false }: MarkdownContentProps) {
  if (!content) return null;
  return (
    <div className="md-content" style={{ color: '#0F172B', fontSize: 13, lineHeight: 1.6 }}>
      <XMarkdown content={content} streaming={streaming ? { hasNextChunk: true } : undefined} />
    </div>
  );
}
