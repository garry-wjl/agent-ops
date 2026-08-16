/**
 * 会话消息展示组件（只读模式） — 复用调试台消息渲染 UI。
 *
 * 职责：接收消息列表，按顺序渲染用户/助手消息气泡。
 * 同时供 Console 页面和 SessionHistoryTab 右侧抽屉复用。
 */
import React from 'react';
import { Avatar } from 'antd';
import {
  CopyOutlined,
  ReloadOutlined,
  SaveOutlined,
  ShareAltOutlined,
  ThunderboltFilled,
  UserOutlined,
} from '@ant-design/icons';
import AttachmentCards from '@/components/AttachmentCards';
import MarkdownContent from '@/components/MarkdownContent';
import StepChainView from '@/components/StepChainView';
import ThinkingPanel from '@/components/ThinkingPanel';
import AssistantSegmentList from '@/components/AssistantSegmentList';
import type { AssistantSegment, AttachmentRef, MessageVO } from '@/types';
import { parseMultimodalContent } from '@/utils/multimodalContent';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  thinking?: string;
  inputType?: 'TEXT' | 'JSON' | 'MULTIMODAL';
  attachments?: AttachmentRef[];
  stepChain?: { steps: any[] };
  segments?: AssistantSegment[];
  traceId?: string;
  authorName?: string;
  timeLabel?: string;
  status?: 'streaming' | 'done' | 'error';
  runId?: string;
  durationLabel?: string;
}

export function toChatMessage(vo: MessageVO): ChatMessage {
  const multimodal =
    vo.inputType === 'MULTIMODAL' ? parseMultimodalContent(vo.content) : null;
  const content = multimodal
    ? (multimodal.text ?? '')
    : typeof vo.content === 'object'
      ? JSON.stringify(vo.content, null, 2)
      : vo.content;
  return {
    id: vo.num,
    role: vo.role === 'USER' ? 'user' : 'assistant',
    content,
    thinking: (vo as any).thinking,
    inputType: vo.inputType ?? undefined,
    attachments: multimodal?.attachments,
    segments: vo.segments ?? undefined,
    stepChain: vo.stepChain ?? undefined,
    traceId: vo.traceId,
    authorName: vo.role === 'USER' ? '我' : 'Assistant',
    timeLabel: vo.createTime ? formatTime(vo.createTime) : undefined,
    status: 'done',
  };
}

function formatTime(ts?: string | number): string {
  if (!ts) return '';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return '';
  return d.toTimeString().slice(0, 8);
}

const COLOR = {
  textPrimary: '#0F172B',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  bgUserBubble: '#F8FAFC',
  bgUserAvatar: '#EFF6FF',
  bgAgentAvatar: '#3B82F6',
  primaryDeep: '#2563EB',
  bgInfo: '#EFF6FF',
  bgSuccess: '#ECFDF5',
  textSuccess: '#10B981',
} as const;

interface SessionMessagesViewProps {
  messages: ChatMessage[];
  loading?: boolean;
  /** 空态提示文本 */
  emptyText?: string;
}

const SessionMessagesView: React.FC<SessionMessagesViewProps> = ({
  messages,
  loading,
  emptyText,
}) => {
  if (loading) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: COLOR.textMuted, fontSize: 13 }}>
        加载中...
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: COLOR.textMuted, fontSize: 13 }}>
        {emptyText || '暂无消息'}
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        padding: '24px 0',
      }}
    >
      {messages.map((m) =>
        m.role === 'user' ? (
          <UserMessage key={m.id} msg={m} />
        ) : (
          <AssistantMessage key={m.id} msg={m} />
        ),
      )}
    </div>
  );
};

/* =================== sub-components =================== */

function MessageHeader(props: {
  authorName?: string;
  timeLabel?: string;
  rightSlot?: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        marginBottom: 4,
      }}
    >
      <span style={{ fontSize: 13, fontWeight: 500, color: COLOR.textPrimary }}>
        {props.authorName ?? ''}
      </span>
      {props.timeLabel ? (
        <span style={{ fontSize: 11, color: COLOR.textMuted }}>
          {props.timeLabel}
        </span>
      ) : null}
      {props.rightSlot}
    </div>
  );
}

function UserMessage({ msg }: { msg: ChatMessage }) {
  const atts = msg.attachments;
  return (
    <div style={{ display: 'flex', gap: 12 }}>
      <Avatar
        size={28}
        style={{
          background: COLOR.bgUserAvatar,
          color: COLOR.primaryDeep,
          flexShrink: 0,
        }}
        icon={<UserOutlined />}
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        <MessageHeader authorName={msg.authorName} timeLabel={msg.timeLabel} />
        <div
          style={{
            background: COLOR.bgUserBubble,
            borderRadius: 8,
            padding: '12px 16px',
            color: COLOR.textPrimary,
            fontSize: 13,
            lineHeight: 1.6,
          }}
        >
          {msg.inputType === 'JSON' ? (
            <pre
              style={{
                margin: 0,
                fontSize: 12,
                fontFamily:
                  'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                whiteSpace: 'pre-wrap',
              }}
            >
              {msg.content}
            </pre>
          ) : msg.content ? (
            <span style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</span>
          ) : null}
          {atts?.length ? <AttachmentCards attachments={atts} /> : null}
        </div>
      </div>
    </div>
  );
}

function AssistantMessage({ msg }: { msg: ChatMessage }) {
  const statusChip =
    msg.status === 'streaming' ? (
      <span
        style={{
          background: COLOR.bgInfo,
          color: COLOR.primaryDeep,
          fontSize: 11,
          padding: '3px 8px',
          borderRadius: 4,
        }}
      >
        ● 处理中
      </span>
    ) : msg.status === 'error' ? (
      <span
        style={{
          background: '#FEF2F2',
          color: '#DC2626',
          fontSize: 11,
          padding: '3px 8px',
          borderRadius: 4,
        }}
      >
        ✕ 失败
      </span>
    ) : (
      <span
        style={{
          background: COLOR.bgSuccess,
          color: COLOR.textSuccess,
          fontSize: 11,
          padding: '3px 8px',
          borderRadius: 4,
        }}
      >
        ✓ 已完成
      </span>
    );

  const meta =
    msg.runId || msg.durationLabel
      ? [msg.runId, msg.durationLabel].filter(Boolean).join(' · ')
      : '';

  return (
    <div style={{ display: 'flex', gap: 12 }}>
      <Avatar
        size={28}
        style={{
          background: COLOR.bgAgentAvatar,
          color: '#fff',
          flexShrink: 0,
        }}
        icon={<ThunderboltFilled />}
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        <MessageHeader
          authorName={msg.authorName}
          timeLabel={msg.timeLabel}
          rightSlot={
            <>
              {statusChip}
              {meta ? (
                <span style={{ fontSize: 11, color: COLOR.textMuted }}>
                  {meta}
                </span>
              ) : null}
            </>
          }
        />

        {msg.segments && msg.segments.length > 0 ? (
          <AssistantSegmentList
            segments={msg.segments}
            streaming={msg.status === 'streaming'}
          />
        ) : msg.status === 'streaming' ? (
          <StreamingPlaceholder />
        ) : (
          <>
            {msg.thinking ? (
              <ThinkingPanel content={msg.thinking} streaming={false} />
            ) : null}
            <MarkdownContent content={msg.content} streaming={false} />
            {msg.stepChain && msg.stepChain.steps.length > 0 ? (
              <StepChainView chain={msg.stepChain as any} />
            ) : null}
          </>
        )}

        {msg.status === 'done' || msg.status === 'error' ? (
          <div
            style={{
              marginTop: 8,
              display: 'flex',
              gap: 16,
              fontSize: 11,
              color: COLOR.textMuted,
            }}
          >
            <ActionButton icon={<CopyOutlined />} label="复制" />
            <ActionButton icon={<ReloadOutlined />} label="重新生成" />
            <ActionButton icon={<SaveOutlined />} label="保存为示例" />
            <ActionButton icon={<ShareAltOutlined />} label="分享 run_id" />
          </div>
        ) : null}
      </div>
    </div>
  );
}

function ActionButton(props: {
  icon: React.ReactNode;
  label: string;
  onClick?: () => void;
}) {
  return (
    <span
      onClick={props.onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        cursor: 'pointer',
      }}
    >
      {props.icon}
      <span>{props.label}</span>
    </span>
  );
}

function StreamingPlaceholder() {
  return (
    <div className="agent-timeline">
      <div
        className="agent-timeline-item"
        style={{ minHeight: 24, display: 'flex', alignItems: 'center' }}
      >
        <span className="agent-timeline-dot agent-timeline-dot-pulse" />
        <span style={{ color: COLOR.textMuted, fontSize: 13 }}>Agent 正在处理中</span>
      </div>
    </div>
  );
}

export default SessionMessagesView;