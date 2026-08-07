/**
 * 调试台主页 — `/agent/debug`
 *
 * 像素级还原 Figma 节点 59:2（AgentSphere · Debug）
 * 视觉骨架（从上到下）：
 *   1. Header (56px)：Agent 调试台
 *   2. SubToolbar (56px)：左 [🤖 agent ▾] [📂 session · N turns ▾] / 右 [+ 新对话] [⌘J JSON 模式] [🧪 fixtures]
 *   3. 对话区（max-width 820 居中）：消息流（用户灰底气泡 / Agent 透明 + 步骤卡片 + 操作按钮）
 *   4. Sender (max-width 820 居中)：单输入 + 底部提示 + 双按钮
 *
 * 关键决策：
 *   - 砍掉 ContextPicker（Skill 选择 PRD §6.1 §1.2 P0 同步改述）
 *   - 砍掉 Welcome/Prompts 空态（PRD §6.2 §2.8 P0 同步改述）
 *   - JSON 模式 toggle 从 Sender 上移到 SubToolbar（PRD §6.2 §2.4 P0 同步改述）
 *   - 会话切换收到 SubToolbar 胶囊
 */
import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Avatar, Button, Dropdown, Input, Modal, Select, Tag, Tooltip, message as antdMessage } from 'antd';
import {
  BoldOutlined,
  CopyOutlined,
  DownOutlined,
  ExperimentOutlined,
  FolderOpenOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  SaveOutlined,
  ShareAltOutlined,
  ThunderboltFilled,
  UserOutlined,
} from '@ant-design/icons';
import DebugSender from '@/components/DebugSender';
import MarkdownContent from '@/components/MarkdownContent';
import StepChainView from '@/components/StepChainView';
import ThinkingPanel from '@/components/ThinkingPanel';
import AssistantSegmentList from '@/components/AssistantSegmentList';
import { useInvokeStream } from '@/hooks/useInvokeStream';
import { AgentApi, SessionApi } from '@/services';
import { useAgentDebugVersionsQuery } from '@/services/agent';
import type {
  AgentDebugVersionVO,
  AgentVO,
  AssistantSegment,
  MessageVO,
  SessionListVO,
} from '@/types';
import { copyToClipboard, relativeTime } from '@/utils/format';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** 深度思考内容（历史回放降级用；实时流走 segments） */
  thinking?: string;
  inputType?: 'TEXT' | 'JSON';
  /** 历史回放的 stepChain（实时流走 segments） */
  stepChain?: { steps: any[] };
  /**
   * 实时流分段：按 Agent 实际执行顺序的 thinking/text/tool_use 列表。
   * 历史消息加载时为 undefined，走 thinking/content/stepChain 降级渲染。
   */
  segments?: AssistantSegment[];
  traceId?: string;
  /** 用户名 / agent 名（消息 header 用） */
  authorName?: string;
  /** 时间戳（HH:MM:SS） */
  timeLabel?: string;
  /** Agent 状态（done / streaming / error） */
  status?: 'streaming' | 'done' | 'error';
  /** Agent run id（取自 traceId 截短） */
  runId?: string;
  /** 总耗时秒（取自 totalLatencyMs） */
  durationLabel?: string;
}

const COLOR = {
  divider: '#E5E7EB',
  capsuleBorder: '#E5E7EB',
  textPrimary: '#0F172B',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  bgUserBubble: '#F8FAFC',
  bgUserAvatar: '#EFF6FF',
  bgAgentAvatar: '#3B82F6',
  bgSubToolbar: '#fff',
  bgSidebar: '#FAFBFC',
  primary: '#3B82F6',
  primaryDeep: '#2563EB',
  bgInfo: '#EFF6FF',
  bgSuccess: '#ECFDF5',
  textSuccess: '#10B981',
} as const;

const CONTENT_MAX_WIDTH = 820;

function formatTime(ts?: string | number): string {
  if (!ts) return '';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return '';
  return d.toTimeString().slice(0, 8);
}

function formatRunId(traceId?: string): string {
  if (!traceId) return '';
  return `run_${traceId.replace(/[^a-zA-Z0-9]/g, '').slice(0, 6).toLowerCase()}`;
}

/** 版本选择器 option 的 value：DRAFT 用 'DRAFT' 令牌，其余用版本号。 */
function debugVersionValue(v: AgentDebugVersionVO): string {
  return v.status === 'DRAFT' ? 'DRAFT' : v.versionNum ?? '';
}

/** 版本状态标签色：草稿态=warning，在线发布态=success，历史态=default。 */
function debugVersionTagColor(v: AgentDebugVersionVO): string {
  if (v.status === 'DRAFT') return 'warning';
  if (v.current) return 'success';
  return 'default';
}

export default function ConsolePage() {
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [agentNum, setAgentNum] = useState<string | undefined>();
  /**
   * 2026-07-28 版本化调试：目标 Agent 版本（target_version）。
   * undefined / 在线版本号 = 调当前在线版；'DRAFT' = 调草稿态；历史版本号 = 调该历史版本。
   */
  const [selectedVersion, setSelectedVersion] = useState<string | undefined>();
  const [sessions, setSessions] = useState<SessionListVO[]>([]);
  const [activeSession, setActiveSession] = useState<string | undefined>();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [mode, setMode] = useState<'text' | 'json'>('text');
  const [renameModal, setRenameModal] = useState<{
    open: boolean;
    num?: string;
    title?: string;
  }>({ open: false });

  const stream = useInvokeStream();
  const activeAgent = useMemo(
    () => agents.find((a) => a.num === agentNum),
    [agents, agentNum],
  );

  /* ----- 版本化调试：当前 Agent 的可调试版本列表 ----- */
  const { data: debugVersions } = useAgentDebugVersionsQuery(agentNum ?? '');

  // Agent 变更或版本列表就绪后，默认选中「在线发布版」；无在线版则回退到草稿态。
  useEffect(() => {
    if (!agentNum) {
      setSelectedVersion(undefined);
      return;
    }
    if (!debugVersions || debugVersions.length === 0) return;
    const online = debugVersions.find(
      (v) => v.current && v.status !== 'DRAFT',
    );
    const draft = debugVersions.find((v) => v.status === 'DRAFT');
    setSelectedVersion(
      online?.versionNum ??
        (draft ? 'DRAFT' : debugVersions[0]?.versionNum ?? undefined),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentNum, debugVersions]);

  /* ----- 滚动控制 ----- */
  /** 消息区滚动容器 */
  const scrollContainerRef = useRef<HTMLElement | null>(null);
  /** 当前刚发送的「最后一条 user msg」DOM；新一轮 send 后定位到顶 */
  const lastUserMsgRef = useRef<HTMLDivElement | null>(null);
  /** 流式底部哨兵；流式期间持续把它滚入视口 */
  const streamBottomRef = useRef<HTMLDivElement | null>(null);
  /** 标记「最近一次用户主动 send」时间戳；避免「切会话拉历史」也触发定位 */
  const lastSentAtRef = useRef(0);
  /** 用户是否手动滚到上方（流式追加时不打扰其阅读） */
  const userScrolledAwayRef = useRef(false);
  /** 旗标：下一次 messages commit 后强制滚到最底（用于切换会话加载历史） */
  const pendingScrollToBottomRef = useRef(false);
  /** 旗标：本轮发送刚为新会话建好 session，setActiveSession 后跳过"用空历史覆盖本地消息" */
  const suppressHistoryRef = useRef(false);

  const isNearBottom = (): boolean => {
    const el = scrollContainerRef.current;
    if (!el) return true;
    return el.scrollTop + el.clientHeight >= el.scrollHeight - 80;
  };

  /* ----- data load ----- */
  // URL ?agent=AGT... — 从智能体管理"调试"按钮跳转过来时携带；列表加载完后自动选中该 Agent。
  const [searchParams] = useSearchParams();
  const agentFromUrl = searchParams.get('agent') || undefined;

  useEffect(() => {
    // 版本化调试：不再仅限 PUBLISHED —— 拉取全部状态 Agent，草稿态 Agent 也可在发布前调试。
    AgentApi.pageList({ pageNo: 1, pageSize: 100 }).then(
      (res) => {
        setAgents(res.list);
        // 仅当 URL 上指定的 agent 在列表里存在 + 当前还没选 agent 时才自动选中。
        // 不在列表（已下线 / 不存在 / 拼写错）时静默忽略，避免列表 dropdown 显示空名。
        if (
          agentFromUrl &&
          !agentNum &&
          res.list.some((a) => a.num === agentFromUrl)
        ) {
          setAgentNum(agentFromUrl);
        }
      },
    );
    // 会话按 Agent 隔离：未选 Agent 不主动拉取，由下方 agentNum 副作用驱动。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentFromUrl]);

  /**
   * 拉当前 Agent 名下的会话列表。
   * - agent 未选时清空，不发请求
   * - 切 Agent 后由 useEffect 自动调用
   * - create / delete 后手动调用刷新
   */
  const reloadSessions = async () => {
    if (!agentNum) {
      setSessions([]);
      return;
    }
    const res = await SessionApi.pageList({
      pageNo: 1,
      pageSize: 50,
      agentNum,
      origin: 'DEBUG_CONSOLE',
    });
    setSessions(res.list);
  };

  // agentNum 变化时重新拉对应会话；切到空 agent 时清空
  useEffect(() => {
    reloadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentNum]);

  useEffect(() => {
    if (!activeSession) {
      setMessages([]);
      return;
    }
    // 新建会话刚发送：保留本地刚追加的消息，不要用空历史覆盖
    if (suppressHistoryRef.current) {
      suppressHistoryRef.current = false;
      return;
    }
    SessionApi.listMessages(activeSession).then((list) => {
      // 标旗：等下一次 messages commit 完，由 useLayoutEffect 同步滚到底
      pendingScrollToBottomRef.current = true;
      setMessages(list.map(toChatMessage));
    });
  }, [activeSession]);

  /**
   * 切换会话加载历史 → 强制定位到最新消息（最底）。
   * <p>
   * 难点：XMarkdown 在 React commit 后**异步**完成 markdown → DOM 的渲染，
   * 容器 scrollHeight 在 useLayoutEffect 时往往**还没拿到最终值**。
   * 一次 scroll 经常落在"伪底部"。
   * 解法：分 4 拍持续把 scrollTop 推到 scrollHeight，覆盖 markdown 异步渲染窗口。
   *  - layout 提交后立即 1 次（catch 普通文本）
   *  - rAF 后 1 次（catch 同步 markdown）
   *  - 100ms 后 1 次（catch 一般异步 markdown）
   *  - 350ms 后 1 次（catch 包含代码块 / 表格的复杂 markdown）
   * 多次 scrollTop 写入是幂等的，已在底部时不产生副作用。
   */
  useLayoutEffect(() => {
    if (!pendingScrollToBottomRef.current) return;
    if (messages.length === 0) return;
    pendingScrollToBottomRef.current = false;
    const stickToBottom = () => {
      const el = scrollContainerRef.current;
      if (el) el.scrollTop = el.scrollHeight;
    };
    stickToBottom();
    const r = requestAnimationFrame(stickToBottom);
    const t1 = setTimeout(stickToBottom, 100);
    const t2 = setTimeout(stickToBottom, 350);
    return () => {
      cancelAnimationFrame(r);
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [messages]);

  /* ----- stream → assistant placeholder ----- */
  useEffect(() => {
    const hasContent = stream.segments.length > 0;
    if (!stream.loading && !hasContent && !stream.error) return;
    // content 字段从所有 text segments 拼出来，用于"复制"按钮等场景
    const aggregatedText = stream.segments
      .filter((s): s is Extract<AssistantSegment, { kind: 'text' }> => s.kind === 'text')
      .map((s) => s.text)
      .join('');
    setMessages((prev) => {
      const next = [...prev];
      const last = next[next.length - 1];
      if (last?.role === 'assistant') {
        next[next.length - 1] = {
          ...last,
          content: stream.error ? `❌ ${stream.error}` : aggregatedText,
          segments: stream.segments,
          traceId: stream.traceId,
          status: stream.error
            ? 'error'
            : stream.loading
              ? 'streaming'
              : 'done',
          runId: formatRunId(stream.traceId),
          durationLabel:
            typeof stream.totalLatencyMs === 'number'
              ? `${(stream.totalLatencyMs / 1000).toFixed(1)}s`
              : undefined,
        };
      }
      return next;
    });
  }, [
    stream.segments,
    stream.error,
    stream.traceId,
    stream.loading,
    stream.totalLatencyMs,
  ]);

  useEffect(() => {
    if (stream.sessionNum && stream.sessionNum !== activeSession) {
      setActiveSession(stream.sessionNum);
      reloadSessions();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stream.sessionNum]);

  /* ----- handlers ----- */
  const handleSend = async (
    input: string | Record<string, any>,
    inputType: 'text' | 'json',
  ) => {
    if (!agentNum) {
      antdMessage.warning('请先选择 Agent');
      return;
    }
    // 复用当前会话；首次发送（无会话）先显式创建一个会话拿到 num，
    // 否则每次都传 session_num=undefined 会导致后端反复新建会话。
    let sessionNum = activeSession;
    const isNewSession = !sessionNum;
    if (isNewSession) {
      try {
        const created = await SessionApi.create({ agentNum });
        sessionNum = created.num;
      } catch {
        // 创建失败已由全局拦截器 toast
        return;
      }
    }
    const userMsg: ChatMessage = {
      id: `local-${Date.now()}`,
      role: 'user',
      content:
        inputType === 'text' ? String(input) : JSON.stringify(input, null, 2),
      inputType: inputType === 'text' ? 'TEXT' : 'JSON',
      authorName: 'me',
      timeLabel: formatTime(Date.now()),
    };
    const assistantPlaceholder: ChatMessage = {
      id: `local-${Date.now() + 1}`,
      role: 'assistant',
      content: '',
      segments: [],
      authorName: activeAgent?.name ?? 'agent',
      timeLabel: formatTime(Date.now()),
      status: 'streaming',
    };
    setMessages((prev) => [...prev, userMsg, assistantPlaceholder]);
    // 标记本轮发送时间，给「定位到我刚发的消息」effect 用
    lastSentAtRef.current = Date.now();
    userScrolledAwayRef.current = false;
    stream.start({
      agentNum,
      session_num: sessionNum,
      input,
      input_type: inputType,
      target_version: selectedVersion,
    });
    // 新会话：发送后再设为当前会话（避免历史加载覆盖本地消息）并刷新左侧列表
    if (isNewSession) {
      suppressHistoryRef.current = true;
      setActiveSession(sessionNum);
      reloadSessions();
    }
  };

  const handleNewSession = () => {
    setActiveSession(undefined);
    setMessages([]);
    stream.reset();
  };

  /**
   * Effect 1：用户刚发送 → 把 user msg 滚到容器顶部，留出空间给即将到来的回复。
   * lastSentAtRef 戳防止「切换会话拉历史」误触发。
   */
  useEffect(() => {
    if (lastSentAtRef.current === 0) return;
    if (Date.now() - lastSentAtRef.current > 1000) return;
    // 等下一帧让 DOM 渲染完
    const id = requestAnimationFrame(() => {
      lastUserMsgRef.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      });
    });
    return () => cancelAnimationFrame(id);
  }, [messages.length]);

  /**
   * Effect 2：流式回复进展中 → 跟随最新内容向下滚动。
   * 若用户主动滚到上方阅读历史，则不打扰。
   */
  useEffect(() => {
    if (!stream.loading) return;
    if (userScrolledAwayRef.current) return;
    streamBottomRef.current?.scrollIntoView({
      behavior: 'auto',
      block: 'end',
    });
  }, [stream.segments, stream.loading]);

  /** 检测用户是否手动滚动离开了底部，记入 ref 让 effect 2 不跟随 */
  const handleScroll = () => {
    if (!stream.loading) return;
    userScrolledAwayRef.current = !isNearBottom();
  };

  const handleAgentChange = (val: string) => {
    if (val === agentNum) return;
    if (messages.length > 0) {
      Modal.confirm({
        title: '切换 Agent 将清空当前会话',
        content: '当前对话历史将丢失，确认继续？',
        okText: '切换并新建会话',
        cancelText: '取消',
        onOk: () => {
          setSelectedVersion(undefined);
          setAgentNum(val);
          handleNewSession();
        },
      });
      return;
    }
    // 切 agent 必须清空当前 active session（旧 agent 的 session 在新 agent 列表里不存在）
    setSelectedVersion(undefined);
    setAgentNum(val);
    handleNewSession();
  };

  const handleDeleteSession = (num: string) => {
    Modal.confirm({
      title: '删除会话',
      content: '会话内消息将被一并删除，确认？',
      okType: 'danger',
      onOk: async () => {
        await SessionApi.delete(num);
        antdMessage.success('已删除');
        if (num === activeSession) handleNewSession();
        reloadSessions();
      },
    });
  };

  const handleRename = async () => {
    if (!renameModal.num || !renameModal.title) return;
    await SessionApi.rename(renameModal.num, renameModal.title);
    antdMessage.success('已重命名');
    setRenameModal({ open: false });
    reloadSessions();
  };

  /* ----- ⌘J 快捷键 ----- */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'j') {
        e.preventDefault();
        setMode((m) => (m === 'text' ? 'json' : 'text'));
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  /* ----- agent dropdown menu ----- */
  const agentMenuItems = useMemo(
    () =>
      agents.map((a) => ({
        key: a.num,
        label: a.name,
        onClick: () => handleAgentChange(a.num),
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [agents, agentNum, messages.length],
  );

  /* ----- session dropdown menu ----- */
  const activeSessionMeta = useMemo(
    () => sessions.find((s) => s.num === activeSession),
    [sessions, activeSession],
  );
  const turnCount = useMemo(
    () => messages.filter((m) => m.role === 'user').length,
    [messages],
  );

  const sessionMenuItems = useMemo(
    () => {
      // 未选 Agent：只给提示，不展示任何会话
      if (!agentNum) {
        return [
          {
            key: '__no-agent__',
            label: (
              <span style={{ color: COLOR.textMuted, fontSize: 12 }}>
                请先在左侧选择 Agent
              </span>
            ),
            disabled: true,
          },
        ];
      }
      return [
        ...sessions.map((s) => ({
          key: s.num,
          label: (
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 12,
                minWidth: 260,
              }}
            >
              <div style={{ flex: 1, overflow: 'hidden' }}>
                <div
                  style={{
                    fontSize: 13,
                    color: COLOR.textPrimary,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {s.title || '未命名会话'}
                </div>
                <div style={{ fontSize: 11, color: COLOR.textMuted }}>
                  {s.lastMessageAt ? relativeTime(s.lastMessageAt) : '新建会话'}
                </div>
              </div>
              <Dropdown
                menu={{
                  items: [
                    { key: 'rename', label: '重命名' },
                    { key: 'delete', label: '删除', danger: true },
                  ],
                  onClick: ({ key, domEvent }) => {
                    domEvent.stopPropagation();
                    if (key === 'rename')
                      setRenameModal({ open: true, num: s.num, title: s.title });
                    if (key === 'delete') handleDeleteSession(s.num);
                  },
                }}
                trigger={['click']}
                placement="bottomRight"
              >
                <Button
                  type="text"
                  size="small"
                  icon={<MoreOutlined />}
                  onClick={(e) => e.stopPropagation()}
                />
              </Dropdown>
            </div>
          ),
          onClick: () => setActiveSession(s.num),
        })),
        ...(sessions.length > 0 ? [{ type: 'divider' as const }] : []),
        {
          key: '__new__',
          label: (
            <span style={{ color: COLOR.primaryDeep, fontSize: 13 }}>
              <PlusOutlined /> 新对话
            </span>
          ),
          onClick: handleNewSession,
        },
      ];
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [agentNum, sessions, activeSession, turnCount],
  );

  return (
    <div
      style={{
        height: 'calc(100vh - 56px)',
        display: 'flex',
        flexDirection: 'column',
        background: '#fff',
      }}
    >
      {/* Header — 标题样式对齐 Agent 管理页（24px/700 主标题 + 14px 副标题） */}
      <div
        style={{
          padding: '24px 24px 16px',
          borderBottom: `1px solid ${COLOR.divider}`,
          background: '#fff',
        }}
      >
        <div
          style={{
            margin: 0,
            color: COLOR.textPrimary,
            fontSize: 24,
            fontWeight: 700,
            lineHeight: '32px',
          }}
        >
          Agent 调试
        </div>
        <div
          style={{
            color: COLOR.textSecondary,
            fontSize: 14,
            marginTop: 4,
          }}
        >
          选择 Agent 与版本，发起对话调试其运行表现
        </div>
      </div>

      {/* SubToolbar (56px) — Figma 132:2 */}
      <div
        style={{
          height: 56,
          padding: '0 24px',
          borderBottom: `1px solid ${COLOR.divider}`,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          background: COLOR.bgSubToolbar,
        }}
      >
        {/* 🤖 agent dropdown */}
        <Dropdown
          menu={{ items: agentMenuItems }}
          trigger={['click']}
          placement="bottomLeft"
        >
          <ToolbarButton>
            <span style={{ fontSize: 13 }}>🤖</span>
            <span style={{ fontWeight: 500 }}>
              {activeAgent?.name ?? '选择 Agent'}
            </span>
            <DownOutlined style={{ fontSize: 10, color: COLOR.textSecondary }} />
          </ToolbarButton>
        </Dropdown>

        {/* 🏷️ 版本选择器 —— 标注草稿态 / 发布态 / 历史态；含草稿态以支持发布前调试 */}
        {agentNum && (
          <Select
            size="small"
            style={{ minWidth: 176 }}
            value={selectedVersion}
            placeholder="选择版本"
            onChange={(v) => setSelectedVersion(v)}
            options={(debugVersions ?? []).map((v) => ({
              value: debugVersionValue(v),
              label: (
                <span
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
                >
                  <Tag
                    color={debugVersionTagColor(v)}
                    style={{ marginInlineEnd: 0, fontSize: 11 }}
                  >
                    {v.statusLabel}
                  </Tag>
                  <span>{v.status === 'DRAFT' ? '草稿（未发布）' : v.versionNum}</span>
                </span>
              ),
            }))}
          />
        )}

        {/* 📂 session capsule */}
        <Dropdown
          menu={{ items: sessionMenuItems }}
          trigger={['click']}
          placement="bottomLeft"
        >
          <ToolbarButton>
            <FolderOpenOutlined style={{ fontSize: 12 }} />
            <span style={{ fontWeight: 500 }}>
              {activeSessionMeta?.title ?? '新会话'}
            </span>
            {turnCount > 0 ? (
              <span style={{ color: COLOR.textMuted }}>
                · {turnCount} turn{turnCount === 1 ? '' : 's'}
              </span>
            ) : null}
            <DownOutlined style={{ fontSize: 10, color: COLOR.textSecondary }} />
          </ToolbarButton>
        </Dropdown>

        <div style={{ flex: 1 }} />

        {/* trace tag */}
        {stream.traceId ? (
          <Tooltip title="复制 trace_id">
            <Tag
              icon={<CopyOutlined />}
              style={{
                cursor: 'pointer',
                borderRadius: 999,
                fontSize: 11,
                color: COLOR.textSecondary,
              }}
              onClick={async () => {
                const ok = await copyToClipboard(stream.traceId!);
                antdMessage[ok ? 'success' : 'error'](
                  ok ? '已复制' : '复制失败',
                );
              }}
            >
              trace: {stream.traceId.slice(0, 12)}...
            </Tag>
          </Tooltip>
        ) : null}

        {/* + 新对话 */}
        <ToolbarButton onClick={handleNewSession}>
          <PlusOutlined style={{ fontSize: 11 }} />
          <span>新对话</span>
        </ToolbarButton>

        {/* ⌘J JSON 模式 toggle */}
        <ToolbarButton
          onClick={() => setMode((m) => (m === 'text' ? 'json' : 'text'))}
          active={mode === 'json'}
        >
          <BoldOutlined style={{ fontSize: 11, opacity: 0.6 }} />
          <span>⌘J JSON 模式</span>
        </ToolbarButton>

        {/* 🧪 fixtures (M2 占位) */}
        <ToolbarButton
          onClick={() =>
            antdMessage.info('Fixtures 库 M2 上线，敬请期待')
          }
        >
          <ExperimentOutlined style={{ fontSize: 11 }} />
          <span>fixtures</span>
        </ToolbarButton>
      </div>

      {/* 对话区 — 居中 max-w 820 */}
      <section
        ref={(el) => { scrollContainerRef.current = el; }}
        onScroll={handleScroll}
        style={{ flex: 1, overflow: 'auto', padding: '24px 0' }}
      >
        <div
          style={{
            maxWidth: CONTENT_MAX_WIDTH,
            margin: '0 auto',
            padding: '0 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: 24,
          }}
        >
          {messages.length === 0 ? (
            <EmptyHint hasAgent={!!agentNum} />
          ) : (
            messages.map((m, i) => {
              // 倒数第二条且为 user → 本轮新发送的消息，挂 lastUserMsgRef
              const isLastUser =
                m.role === 'user' && i === messages.length - 2;
              return (
                <div
                  key={m.id}
                  ref={isLastUser ? lastUserMsgRef : undefined}
                >
                  {m.role === 'user' ? (
                    <UserMessage msg={m} />
                  ) : (
                    <AssistantMessage msg={m} />
                  )}
                </div>
              );
            })
          )}
          {/* 流式底部哨兵：effect 2 滚到此处 */}
          <div ref={streamBottomRef} aria-hidden="true" />
        </div>
      </section>

      {/* Sender — 居中 max-w 820 */}
      <footer
        style={{
          padding: '16px 0 24px',
          background: '#fff',
        }}
      >
        <div
          style={{
            maxWidth: CONTENT_MAX_WIDTH,
            margin: '0 auto',
            padding: '0 24px',
          }}
        >
          <DebugSender
            loading={stream.loading}
            disabled={!agentNum}
            mode={mode}
            onSubmit={handleSend}
            onCancel={() => stream.abort()}
          />
        </div>
      </footer>

      <Modal
        open={renameModal.open}
        title="重命名会话"
        onOk={handleRename}
        onCancel={() => setRenameModal({ open: false })}
      >
        <Input
          value={renameModal.title}
          onChange={(e) =>
            setRenameModal((p) => ({ ...p, title: e.target.value }))
          }
          placeholder="新名称"
        />
      </Modal>
    </div>
  );
}

/* =================== sub-components =================== */

/**
 * SubToolbar 通用胶囊按钮。
 * <p>
 * 必须用 forwardRef + 透传 ...rest，否则 antd v6 Dropdown / Tooltip 注入的
 * onClick / onMouseEnter / ref 拿不到，trigger 失效（v5 时代靠隐式 span 包裹兜底）。
 */
const ToolbarButton = React.forwardRef<
  HTMLButtonElement,
  React.ButtonHTMLAttributes<HTMLButtonElement> & {
    children: React.ReactNode;
    active?: boolean;
  }
>(function ToolbarButton({ children, active, style, ...rest }, ref) {
  return (
    <button
      ref={ref}
      type="button"
      {...rest}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        height: 32,
        padding: '0 12px',
        background: active ? COLOR.bgInfo : '#fff',
        border: `1px solid ${active ? COLOR.primaryDeep : COLOR.capsuleBorder}`,
        borderRadius: 6,
        fontSize: 13,
        color: active ? COLOR.primaryDeep : COLOR.textPrimary,
        cursor: 'pointer',
        transition: 'all 0.15s',
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      {children}
    </button>
  );
});

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
  return (
    <div style={{ display: 'flex', gap: 12 }}>
      <Avatar
        size={28}
        style={{ background: COLOR.bgUserAvatar, color: COLOR.primaryDeep, flexShrink: 0 }}
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
          ) : (
            <span style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</span>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * 深度思考面板已抽到 src/components/ThinkingPanel.tsx，供历史降级渲染与 AssistantSegmentList 共用。
 */

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
          // 实时流 / 已完成的本轮会话：按 Agent 实际执行顺序渲染
          <AssistantSegmentList
            segments={msg.segments}
            streaming={msg.status === 'streaming'}
          />
        ) : msg.status === 'streaming' ? (
          // 已发请求但首帧未到 — 显示 timeline 风格脉冲占位,与后续段衔接自然
          <StreamingPlaceholder />
        ) : (
          // 历史回放降级：thinking → 正文 → 工具链（BE 持久化未保留交错顺序）
          // 此分支必然非 streaming(上层已分流),streaming 参数固定 false
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
            <ActionButton
              icon={<CopyOutlined />}
              label="复制"
              onClick={async () => {
                const ok = await copyToClipboard(msg.content);
                antdMessage[ok ? 'success' : 'error'](ok ? '已复制' : '复制失败');
              }}
            />
            <ActionButton icon={<ReloadOutlined />} label="重新生成" />
            <ActionButton icon={<SaveOutlined />} label="保存为示例" />
            <ActionButton
              icon={<ShareAltOutlined />}
              label="分享 run_id"
              onClick={async () => {
                if (!msg.runId) return;
                const ok = await copyToClipboard(msg.runId);
                antdMessage[ok ? 'success' : 'error'](ok ? '已复制' : '复制失败');
              }}
            />
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

/**
 * StreamingPlaceholder — 已发请求但首帧未到的等待占位。
 * <p>
 * 视觉上沿用 timeline(贯穿竖线 + 圆点),圆点带脉冲动画并附"Agent 正在处理中"文案。
 * 首帧到达后,AssistantMessage 会切换到 AssistantSegmentList,timeline 结构保持连贯。
 */
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

function EmptyHint({ hasAgent }: { hasAgent: boolean }) {
  return (
    <div
      style={{
        marginTop: 80,
        textAlign: 'center',
        color: COLOR.textMuted,
        fontSize: 13,
      }}
    >
      <RobotOutlined style={{ fontSize: 32, color: COLOR.primary }} />
      <div style={{ marginTop: 12 }}>
        {hasAgent
          ? '从下方输入框开始对话'
          : '请先在顶部选择执行 Agent'}
      </div>
    </div>
  );
}

function normalizeContent(c: unknown): string {
  if (c == null) return '';
  if (typeof c === 'string') return c;
  try {
    return JSON.stringify(c, null, 2);
  } catch {
    return String(c);
  }
}

function toChatMessage(m: MessageVO): ChatMessage {
  return {
    id: m.num,
    role: m.role === 'USER' ? 'user' : 'assistant',
    content: normalizeContent(m.content),
    inputType: m.inputType ?? undefined,
    stepChain: m.stepChain ?? undefined,
    // BE v3.x:历史助手消息也带 segments,FE 与本轮流式共用 AssistantSegmentList 渲染。
    // 旧消息或非 assistant 消息为 null/undefined,渲染时自动降级到 content + stepChain。
    segments: m.segments ?? undefined,
    traceId: m.traceId,
    authorName: m.role === 'USER' ? 'me' : 'agent',
    timeLabel: formatTime(m.createTime),
    status: 'done',
    runId: formatRunId(m.traceId),
  };
}
