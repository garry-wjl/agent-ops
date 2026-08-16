/**
 * useInvokeStream — 调试台流式 hook（v4.0 segments 按到达顺序）
 *
 * 协议：BE 返回 Flux<io.agentscope.core.agent.Event>，SSE 每帧裸 JSON：
 *   data: {"type":"REASONING"|"TOOL_RESULT"|...,
 *          "message":{"role":..,"content":[ContentBlock,..]}, "isLast":bool, "source":..}
 *
 * v4.0 变化（vs v3.1）：
 * - 数据模型从"三桶（thinking/text/stepChain）"改为"segments 数组按到达顺序"
 * - 同类相邻 chunk 合并到上一段；类型切换开新段；多轮 ReAct 的真实交错顺序得以保留
 * - 正确处理 tool_use 的 fragment 协议（name === '__fragment__' 帧按 toolCallId 拼 content）
 *
 * 事件处理：
 * - REASONING：遍历 message.content blocks，逐块走 appendBlock 合入 segments；
 *   若为 isLast 且带 usage，先记入 pendingMeta（被 chunk 去重跳过内容时仍保留 usage）
 * - TOOL_RESULT：遍历 message.content blocks，逐块走 applyToolResult 按 id 回填
 * - AGENT_RESULT：读取 message.usage（本轮汇总），写入 totalTokens / usage；内容不展示；
 *   并以业务终态结束 loading（不单依赖 HTTP 连接关闭，避免半关闭 chunked 导致前端卡在「处理中」）
 * - HINT / SUMMARY：本期不展示，仅 console.debug
 *
 * 出字速率：跟随 SSE 到达速率，rAF 合并到下一帧 setState。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { invokeStream } from '@/utils/sse';
import type {
  AgentContentBlock,
  AgentScopeEvent,
  AssistantSegment,
  ChatUsage,
  DebugInvokeRequest,
  SsePlatformEvent,
} from '@/types';

export interface UseInvokeStreamState {
  loading: boolean;
  /** 按到达顺序的分段列表；UI 直接 map 渲染即可 */
  segments: AssistantSegment[];
  sessionNum?: string;
  traceId?: string;
  /** 本轮汇总 Token（优先 AGENT_RESULT.message.usage） */
  totalTokens?: number;
  /** 本轮完整 usage；UI 可展示 in/out */
  usage?: ChatUsage;
  totalLatencyMs?: number;
  error?: string;
}

const initial: UseInvokeStreamState = {
  loading: false,
  segments: [],
};

/**
 * 把 ToolResultBlock.output（List<ContentBlock>）压平成 step UI 能展示的形态。
 * 优先抽 text；若只有结构化块则原样返回让 prettyJson 处理。
 */
export function flattenToolOutput(output: AgentContentBlock[] | undefined): unknown {
  if (!output || output.length === 0) return undefined;
  const texts = output
    .filter((b): b is Extract<AgentContentBlock, { type: 'text' }> => b.type === 'text')
    .map((b) => b.text);
  if (texts.length === output.length) return texts.join('\n');
  return output;
}

/**
 * 规范化 message.usage；缺字段时补 totalTokens = input + output。
 */
export function normalizeChatUsage(raw: ChatUsage | undefined | null): ChatUsage | undefined {
  if (!raw || typeof raw !== 'object') return undefined;
  const inputTokens = Number(raw.inputTokens);
  const outputTokens = Number(raw.outputTokens);
  if (!Number.isFinite(inputTokens) && !Number.isFinite(outputTokens)) return undefined;
  const inTok = Number.isFinite(inputTokens) ? inputTokens : 0;
  const outTok = Number.isFinite(outputTokens) ? outputTokens : 0;
  const cached =
    raw.cachedTokens == null ? undefined : Number(raw.cachedTokens);
  const time = raw.time == null ? undefined : Number(raw.time);
  const totalFromRaw =
    raw.totalTokens == null ? undefined : Number(raw.totalTokens);
  return {
    inputTokens: inTok,
    outputTokens: outTok,
    ...(Number.isFinite(cached) ? { cachedTokens: cached } : {}),
    ...(Number.isFinite(time) ? { time } : {}),
    totalTokens: Number.isFinite(totalFromRaw) ? totalFromRaw : inTok + outTok,
  };
}

/**
 * 把单个 ContentBlock 合入 segments 列表（不可变更新）。
 * 合并规则：
 * - thinking / text：上一段同类则追加文本，否则新建段
 * - tool_use：上一段是 tool_use 且 toolCallId 相同 → 追加 argsBuffer（fragment 拼接）；
 *             否则新建段，name === '__fragment__' 时兜底成 '(unknown)'
 * - 其他类型（image/audio/video/tool_result）：在此函数里忽略；tool_result 走 applyToolResult
 */
export function appendBlock(
  segs: AssistantSegment[],
  block: AgentContentBlock,
): AssistantSegment[] {
  const last = segs[segs.length - 1];

  if (block.type === 'thinking') {
    const delta = block.thinking ?? '';
    if (!delta) return segs;
    if (last?.kind === 'thinking') {
      return [...segs.slice(0, -1), { ...last, text: last.text + delta }];
    }
    return [...segs, { kind: 'thinking', text: delta }];
  }

  if (block.type === 'text') {
    const delta = block.text ?? '';
    if (!delta) return segs;
    if (last?.kind === 'text') {
      return [...segs.slice(0, -1), { ...last, text: last.text + delta }];
    }
    return [...segs, { kind: 'text', text: delta }];
  }

  if (block.type === 'tool_use') {
    const isFragment = block.name === '__fragment__';
    const sameTool = last?.kind === 'tool_use' && last.toolCallId === block.id;

    if (sameTool && last.kind === 'tool_use') {
      // fragment 拼接：content 增量加到 argsBuffer；非空 input 才覆盖（避免被空对象 {} 清掉）
      const hasInput = block.input && Object.keys(block.input).length > 0;
      return [
        ...segs.slice(0, -1),
        {
          ...last,
          argsBuffer: last.argsBuffer + (block.content ?? ''),
          input: hasInput ? block.input : last.input,
          // 后续帧若拿到真名，覆盖 '(unknown)'
          toolName: !isFragment && block.name ? block.name : last.toolName,
        },
      ];
    }

    return [
      ...segs,
      {
        kind: 'tool_use',
        toolCallId: block.id,
        toolName: isFragment ? '(unknown)' : block.name,
        argsBuffer: block.content ?? '',
        input: block.input,
        status: 'pending',
        startedAt: new Date().toISOString(),
      },
    ];
  }

  return segs;
}

/**
 * 把 TOOL_RESULT 的 tool_result block 回填到对应 tool_use segment（按 id 反向匹配最近的一个）。
 */
export function applyToolResult(
  segs: AssistantSegment[],
  block: Extract<AgentContentBlock, { type: 'tool_result' }>,
  latencyMs?: number,
): AssistantSegment[] {
  // 反向找最近一个匹配 id 的 tool_use；同 id 通常只有一个，但找最近的更稳
  let idx = -1;
  for (let i = segs.length - 1; i >= 0; i--) {
    const s = segs[i];
    if (s.kind === 'tool_use' && s.toolCallId === block.id) {
      idx = i;
      break;
    }
  }
  if (idx < 0) return segs;
  const target = segs[idx] as Extract<AssistantSegment, { kind: 'tool_use' }>;
  const updated: AssistantSegment = {
    ...target,
    output: flattenToolOutput(block.output),
    status: 'success',
    latencyMs,
  };
  return [...segs.slice(0, idx), updated, ...segs.slice(idx + 1)];
}

/**
 * 判断当前 REASONING 帧是否应被跳过（去重 AgentScope PostReasoning 完整快照），并返回更新后的 id 集合。
 *
 * AgentScope `StreamOptions.defaults()` 同时发出：
 * - N 个 ReasoningChunkEvent（isLast=false，增量 delta）
 * - 1 个 PostReasoningEvent（isLast=true，完整 message 快照）
 *
 * 前端按 delta 累加 segments，如果再把 isLast=true 帧的完整 content 当 delta 追加，
 * 会出现"你好世界你好世界"式重复（tool_use 的 args 同理被双倍拼接）。
 *
 * 策略：chunk 帧把 msgId 记入 set；遇到同 id 的 isLast 帧直接跳过并清理；
 * 若 set 不含该 id（纯非流式 LLM 一次性发完，无 chunk），按完整 message 正常 append。
 *
 * 返回新集合而不可变更新，便于在 ref / useState 间安全切换。
 */
export function trackReasoningChunkSnapshot(
  evt: AgentScopeEvent,
  chunkedIds: Set<string>,
): { skip: boolean; nextIds: Set<string> } {
  const msgId = evt.message.id;
  if (evt.isLast && msgId && chunkedIds.has(msgId)) {
    const nextIds = new Set(chunkedIds);
    nextIds.delete(msgId);
    return { skip: true, nextIds };
  }
  if (!evt.isLast && msgId && !chunkedIds.has(msgId)) {
    const nextIds = new Set(chunkedIds);
    nextIds.add(msgId);
    return { skip: false, nextIds };
  }
  return { skip: false, nextIds: chunkedIds };
}

export function useInvokeStream() {
  const [state, setState] = useState<UseInvokeStreamState>(initial);
  const abortRef = useRef<AbortController | null>(null);
  /**
   * 业务终态已到达（AGENT_RESULT）。
   * 服务端常在 AGENT_RESULT 后以不完整 chunked 关连接：浏览器 fetch ReadableStream
   * 可能永不 done、也不抛错，导致仅依赖 onDone 时 loading 卡死在 true。
   */
  const finishedRef = useRef(false);

  // 流内容累积：收到即写 ref，rAF 合并刷到 state
  const pendingSegmentsRef = useRef<AssistantSegment[]>([]);
  // rAF tick id；null 表示没有待执行的刷新
  const tickIdRef = useRef<number | null>(null);
  // meta 缓冲（非内容，rAF 一并 flush）
  const pendingMetaRef = useRef<Partial<UseInvokeStreamState>>({});
  // tool_use 发出时间记录，用于 tool_result 到达时算 latencyMs
  const stepStartTimesRef = useRef<Map<string, number>>(new Map());
  /**
   * 已通过 chunk 累积过的 REASONING messageId 集合。
   * AgentScope 默认 StreamOptions 同时发增量 chunk（isLast=false）和最终完整快照
   * （isLast=true 的 PostReasoning），后者会让前端把已累积内容再追加一次造成重复。
   * 见 io.agentscope.core.agent.StreamingHook —— 收到 chunk 时记录 id，遇到同 id 的
   * 最终帧直接跳过；若 set 不含该 id（纯非流式 LLM 一次性发完）则正常 append。
   */
  const chunkedReasoningMsgIdsRef = useRef<Set<string>>(new Set());

  /** 每帧把累积刷到 state（无节流，速率跟随事件到达） */
  const tick = useCallback(() => {
    tickIdRef.current = null;
    const meta = pendingMetaRef.current;
    pendingMetaRef.current = {};
    setState((prev) => {
      const next: UseInvokeStreamState = { ...prev, ...meta };
      if (pendingSegmentsRef.current !== prev.segments) {
        next.segments = pendingSegmentsRef.current;
      }
      return next;
    });
  }, []);

  const ensureTick = useCallback(() => {
    if (tickIdRef.current == null) {
      tickIdRef.current = requestAnimationFrame(tick);
    }
  }, [tick]);

  /** 立即把累积刷到 state，附带 extra 字段（abort / onDone / onError 用） */
  const flushFinal = useCallback((extra?: Partial<UseInvokeStreamState>) => {
    if (tickIdRef.current != null) cancelAnimationFrame(tickIdRef.current);
    tickIdRef.current = null;
    const meta = pendingMetaRef.current;
    pendingMetaRef.current = {};
    setState((prev) => ({
      ...prev,
      ...meta,
      segments: pendingSegmentsRef.current,
      ...(extra ?? {}),
    }));
  }, []);

  /** AGENT_RESULT 到达后结束 loading，并中止底层 fetch，避免连接半关闭挂起。 */
  const finishStream = useCallback(() => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    flushFinal({ loading: false });
    abortRef.current?.abort();
    abortRef.current = null;
  }, [flushFinal]);

  const reset = useCallback(() => {
    if (tickIdRef.current != null) cancelAnimationFrame(tickIdRef.current);
    tickIdRef.current = null;
    pendingSegmentsRef.current = [];
    pendingMetaRef.current = {};
    stepStartTimesRef.current = new Map();
    chunkedReasoningMsgIdsRef.current = new Set();
    finishedRef.current = false;
    setState(initial);
  }, []);

  const abort = useCallback(() => {
    finishedRef.current = true;
    abortRef.current?.abort();
    abortRef.current = null;
    flushFinal({ loading: false });
  }, [flushFinal]);

  useEffect(
    () => () => {
      if (tickIdRef.current != null) cancelAnimationFrame(tickIdRef.current);
    },
    [],
  );

  /** REASONING：遍历 content blocks 逐块合入 segments */
  const handleReasoning = useCallback(
    (evt: AgentScopeEvent) => {
      // 末帧 usage：即使内容因 PostReasoning 快照被 skip，也要记入（多轮 ReAct 中间帧）
      if (evt.isLast) {
        const usage = normalizeChatUsage(evt.message?.usage);
        if (usage) {
          pendingMetaRef.current = {
            ...pendingMetaRef.current,
            usage,
            totalTokens: usage.totalTokens,
          };
          ensureTick();
        }
      }

      const blocks = evt.message.content ?? [];
      if (blocks.length === 0) return;

      const tracking = trackReasoningChunkSnapshot(evt, chunkedReasoningMsgIdsRef.current);
      chunkedReasoningMsgIdsRef.current = tracking.nextIds;
      if (tracking.skip) {
        if (import.meta.env.DEV) {
          console.debug('[useInvokeStream] 跳过冗余 REASONING isLast 帧', {
            msgId: evt.message.id,
          });
        }
        return;
      }

      let nextSegs = pendingSegmentsRef.current;
      const before = nextSegs;
      for (const block of blocks) {
        // 记录 tool_use 首次出现的时间（用于后续 tool_result 算 latencyMs）
        if (block.type === 'tool_use' && !stepStartTimesRef.current.has(block.id)) {
          stepStartTimesRef.current.set(block.id, Date.now());
        }
        nextSegs = appendBlock(nextSegs, block);
      }

      if (nextSegs !== before) {
        pendingSegmentsRef.current = nextSegs;
        ensureTick();
      }

      if (import.meta.env.DEV) {
        console.debug(
          '[useInvokeStream] REASONING blocks:',
          blocks.map((b) => b.type),
          { segmentCount: nextSegs.length },
        );
      }
    },
    [ensureTick],
  );

  /** AGENT_RESULT：本轮汇总 usage（BE 已在 AgentRunnerService 回填） */
  const handleAgentResult = useCallback(
    (evt: AgentScopeEvent) => {
      const usage = normalizeChatUsage(evt.message?.usage);
      if (!usage) {
        if (import.meta.env.DEV) {
          console.debug('[useInvokeStream] AGENT_RESULT 无 usage', evt);
        }
        return;
      }
      pendingMetaRef.current = {
        ...pendingMetaRef.current,
        usage,
        totalTokens: usage.totalTokens,
      };
      ensureTick();
    },
    [ensureTick],
  );

  /** TOOL_RESULT：按 id 回填对应 tool_use segment */
  const handleToolResult = useCallback(
    (evt: AgentScopeEvent) => {
      const blocks = evt.message.content ?? [];
      if (blocks.length === 0) return;

      let nextSegs = pendingSegmentsRef.current;
      const before = nextSegs;
      for (const block of blocks) {
        if (block.type !== 'tool_result') continue;
        const startedAt = stepStartTimesRef.current.get(block.id);
        const latencyMs = startedAt ? Date.now() - startedAt : undefined;
        stepStartTimesRef.current.delete(block.id);
        nextSegs = applyToolResult(nextSegs, block, latencyMs);
      }

      if (nextSegs !== before) {
        pendingSegmentsRef.current = nextSegs;
        ensureTick();
      } else if (import.meta.env.DEV) {
        console.debug('[useInvokeStream] TOOL_RESULT 未匹配到 pending tool_use', evt);
      }
    },
    [ensureTick],
  );

  const start = useCallback(
    async (req: DebugInvokeRequest) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      // 重置缓冲
      pendingSegmentsRef.current = [];
      pendingMetaRef.current = {};
      stepStartTimesRef.current = new Map();
      chunkedReasoningMsgIdsRef.current = new Set();
      finishedRef.current = false;
      setState({ ...initial, loading: true });

      await invokeStream(
        {
          url: '/api/v1/debug-console/invoke',
          body: req,
          signal: controller.signal,
        },
        {
          onEvent: (parsed: SsePlatformEvent) => {
            if (finishedRef.current) return;
            if (parsed.event === 'error') {
              pendingMetaRef.current = {
                ...pendingMetaRef.current,
                error: parsed.data?.message ?? '调用失败',
              };
              finishedRef.current = true;
              flushFinal({ loading: false, error: parsed.data?.message ?? '调用失败' });
              abortRef.current?.abort();
              abortRef.current = null;
              return;
            }
            // parsed.event === 'message'：BE 不设 event 名，全部走这一路
            const evt = parsed.data;
            switch (evt.type) {
              case 'REASONING':
                handleReasoning(evt);
                break;
              case 'TOOL_RESULT':
                handleToolResult(evt);
                break;
              case 'AGENT_RESULT':
                handleAgentResult(evt);
                // 以业务终态结束，不依赖 HTTP 连接是否干净关闭
                finishStream();
                break;
              case 'HINT':
              case 'SUMMARY':
                if (import.meta.env.DEV) {
                  console.debug(`[useInvokeStream] ${evt.type} 暂不展示`, evt);
                }
                break;
              default: {
                const _exhaustive: never = evt.type;
                if (import.meta.env.DEV) {
                  console.warn('[useInvokeStream] 未知 EventType', _exhaustive, evt);
                }
              }
            }
          },
          onDone: () => {
            if (finishedRef.current) return;
            finishedRef.current = true;
            flushFinal({ loading: false });
          },
          onError: (err) => {
            // 终态后主动 abort / 半关闭连接产生的异常忽略，避免覆盖已完成状态
            if (finishedRef.current) return;
            finishedRef.current = true;
            flushFinal({ loading: false, error: err.message });
          },
        },
      );
    },
    [
      ensureTick,
      finishStream,
      flushFinal,
      handleAgentResult,
      handleReasoning,
      handleToolResult,
    ],
  );

  return { ...state, start, abort, reset };
}
