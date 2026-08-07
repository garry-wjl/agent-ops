/**
 * ThinkingPanel — 深度思考折叠面板
 *
 * 视觉上与正文答案明显区分：浅灰底斜体；流式中默认展开方便观察推理过程，
 * 流结束后自动收起以聚焦答案（用户手动展开/收起则不再自动切换）。
 */
import { CaretDownOutlined, CaretRightOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';
import MarkdownContent from './MarkdownContent';

export interface ThinkingPanelProps {
  content: string;
  streaming: boolean;
}

export default function ThinkingPanel({ content, streaming }: ThinkingPanelProps) {
  const [expanded, setExpanded] = useState(true);
  const userToggledRef = useRef(false);

  useEffect(() => {
    if (!streaming && !userToggledRef.current) {
      setExpanded(false);
    }
  }, [streaming]);

  const toggle = () => {
    userToggledRef.current = true;
    setExpanded((v) => !v);
  };

  return (
    <div
      style={{
        background: '#F8FAFC',
        border: '1px solid #E5E7EB',
        borderRadius: 6,
        padding: '10px 14px',
        marginBottom: 12,
      }}
    >
      <div
        onClick={toggle}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          cursor: 'pointer',
          color: '#64748B',
          fontSize: 12,
          fontWeight: 500,
        }}
      >
        {expanded ? <CaretDownOutlined /> : <CaretRightOutlined />}
        <span>深度思考{streaming ? '中…' : ''}</span>
      </div>
      {expanded ? (
        <div
          style={{
            marginTop: 8,
            color: '#475569',
            opacity: 0.92,
            fontSize: 13,
            lineHeight: 1.6,
            borderLeft: '2px solid #CBD5E1',
            paddingLeft: 10,
          }}
        >
          <MarkdownContent content={content} streaming={streaming} />
        </div>
      ) : null}
    </div>
  );
}
