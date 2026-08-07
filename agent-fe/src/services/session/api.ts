/**
 * 会话与调试台 invoke 服务 — 对应调试台技术方案 §5.2.1
 * - REST 走 fetch 包装；SSE invoke 走 utils/sse.ts
 */
import { get, post } from '../request';
import type {
  SessionVO,
  SessionDetailVO,
  SessionListVO,
  MessageVO,
  PageVO,
} from '@/types';

export const sessionApi = {
  create: (param: { agentNum: string; skillHint?: string; title?: string }) =>
    post<SessionVO>('/api/v1/session/command/create', param),
  rename: (num: string, newTitle: string) =>
    post<void>('/api/v1/session/command/rename', { num, newTitle }),
  delete: (num: string) => post<void>('/api/v1/session/command/delete', { num }),

  pageList: (query: {
    pageNo: number;
    pageSize: number;
    agentNum?: string;
    origin?: string;
    keyword?: string;
  }) =>
    post<PageVO<SessionListVO>>('/api/v1/session/query/list', query),
  detail: (num: string) =>
    get<SessionDetailVO>('/api/v1/session/query/detail', { num }),
  listMessages: (sessionNum: string) =>
    get<MessageVO[]>('/api/v1/session/query/messages', { sessionNum }),
};
