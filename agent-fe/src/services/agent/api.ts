/**
 * Agent 服务 — 对齐 rd-agent-be v2.4 / v2.6 实际暴露的 REST 接口
 *
 * 后端控制器拆分（impl-adapter-module 约定）：
 *   - AgentCommandController（POST + SSE）：写操作 + a2aResync + invoke
 *     + v2.6 新增 createA2a / saveA2aDraft / unsubscribeA2a
 *   - AgentQueryController（GET）：list / detail / draft/detail / versions / version/detail
 *     + v2.6 新增 a2a-history（A2A 同步历史）
 *
 * v2.4 起删除已不存在的接口 stub（delete / testConnection / rollback / compareVersions）；
 * 对应 hooks 与 UI callsite 同步清理。
 */
import { get, post } from '../request';
import type {
  AgentVO,
  AgentDetailVO,
  AgentVersionVO,
  AgentVersionDetailVO,
  AgentDebugVersionVO,
  AgentSkillBindingStatusVO,
  AgentDraftVO,
  AgentCreateParam,
  AgentDraftParam,
  ConfigSnapshot,
  PublishParam,
  AgentPageQuery,
  PageVO,
  A2aCreateParam,
  A2aDraftParam,
  A2aSyncHistoryVO,
  AgentApiKeyVO,
  AgentApiKeyCreatedVO,
  AgentApiKeyPlainVO,
} from '@/types';

/** 后端 AgentCommandController / AgentQueryController 共享路径前缀 */
const AGENT_BASE = '/api/v1/agents';

/** Agent 对外调用秘钥接口前缀（AgentApiKeyController，登录态） */
const API_KEY_BASE = '/api/v1/agents/apiKey';

export const agentApi = {
  // ---- 写接口（AgentCommandController） ----
  create: (param: AgentCreateParam) =>
    post<{ agentNum: string }>(`${AGENT_BASE}/create`, param),
  saveDraft: (param: AgentDraftParam) =>
    post<{ draftNum: string; lockUntil: string }>(
      `${AGENT_BASE}/draft/save`,
      param,
    ),
  discardDraft: (agentNum: string) =>
    post<void>(
      `${AGENT_BASE}/draft/discard?agentNum=${encodeURIComponent(agentNum)}`,
    ),
  offline: (agentNum: string) =>
    post<void>(
      `${AGENT_BASE}/offline?agentNum=${encodeURIComponent(agentNum)}`,
    ),
  publish: (param: PublishParam) =>
    post<{ versionNum: string }>(`${AGENT_BASE}/publish`, param),

  // ---- v3.0：版本生命周期接口（统一在 /version/* 下） ----
  /**
   * 创建草稿版本：基于当前在线版本快照生成一行 status=DRAFT 的版本。
   * 后端约束：每个 Agent 同时只能有 1 份 DRAFT；冲突由后端返回 4xxx 业务错误。
   */
  createVersion: (agentNum: string) =>
    post<{ versionId: string }>(`${AGENT_BASE}/version/create`, { agentNum }),
  /**
   * 编辑草稿：覆盖 DRAFT 版本的 configDraft；DRAFT 之外的状态不可调用。
   */
  editDraftVersion: (versionId: string, configDraft: ConfigSnapshot) =>
    post<void>(`${AGENT_BASE}/version/edit`, { versionId, configDraft }),
  /**
   * 删除草稿：仅可删 DRAFT 版本；PUBLISHED / ARCHIVED 不可删。
   */
  deleteDraftVersion: (versionId: string) =>
    post<void>(`${AGENT_BASE}/version/delete`, { versionId }),

  // ---- 读接口（AgentQueryController） ----
  pageList: (query: AgentPageQuery) =>
    get<PageVO<AgentVO>>(`${AGENT_BASE}/page`, query as Record<string, any>),
  detail: (agentNum: string) =>
    get<AgentDetailVO>(`${AGENT_BASE}/detail`, { agentNum }),
  draftDetail: (agentNum: string) =>
    get<AgentDraftVO>(`${AGENT_BASE}/draft/detail`, { agentNum }),
  versionList: (agentNum: string, limit = 50) =>
    get<AgentVersionVO[]>(`${AGENT_BASE}/versions`, { agentNum, limit }),
  versionDetail: (agentNum: string, versionNum: string) =>
    get<AgentVersionDetailVO>(`${AGENT_BASE}/version/detail`, {
      agentNum,
      versionNum,
    }),

  /**
   * 2026-07-28：调试可选版本列表（调试台版本选择器数据源）。
   * 含 DRAFT / 在线 PUBLISHED / 历史版本，后端已按 草稿→在线→历史 排序并给出 statusLabel。
   */
  debugVersions: (agentNum: string) =>
    get<AgentDebugVersionVO[]>(`${AGENT_BASE}/debug-versions`, { agentNum }),
  /**
   * 2026-07-28：指定版本下 Agent 已绑定 Skill 的版本状态（hasNewer / boundDeprecated）。
   * targetVersion 空 = 当前在线版本。
   */
  skillBindingStatus: (agentNum: string, targetVersion?: string) =>
    get<AgentSkillBindingStatusVO[]>(`${AGENT_BASE}/skill-binding-status`, {
      agentNum,
      targetVersion,
    }),

  // ---- A2A 同步（v2.4 起合并入 AgentCommandController） ----
  /** A2A 详情页「[手动重新同步]」按钮触发 */
  a2aManualResync: (num: string) =>
    post<{ num: string; lastSyncedAt: string }>(
      `${AGENT_BASE}/a2aResync?num=${encodeURIComponent(num)}`,
    ),

  // ---- A2A 接入（v2.6 新增；与现有 controller 同前缀 /api/v1/agents） ----
  /**
   * 校验并接入：调远端拉一次 AgentCard，成功则进入 PENDING_SYNC（等待 Nacos 推送）。
   * 重复接入（已存在 num）由后端返回原 detail，前端做跳转提示。
   */
  createA2a: (param: A2aCreateParam) =>
    post<AgentDetailVO>(`${AGENT_BASE}/createA2a`, param),
  /**
   * 保存草稿：仅落库 displayName/description/nacosAgentName，不触发远端校验。
   */
  saveA2aDraft: (param: A2aDraftParam) =>
    post<AgentVO>(`${AGENT_BASE}/saveA2aDraft`, param),
  /**
   * 取消订阅：从平台移除 A2A Agent；不影响 Nacos 上原服务。
   */
  unsubscribeA2a: (num: string) =>
    post<void>(
      `${AGENT_BASE}/unsubscribeA2a?num=${encodeURIComponent(num)}`,
    ),
  /**
   * A2A 同步历史：每次 Nacos 推送/手动 resync 都会写一条记录。
   */
  a2aSyncHistory: (agentNum: string, limit = 100) =>
    get<A2aSyncHistoryVO[]>(`${AGENT_BASE}/a2a-history`, {
      agentNum,
      limit,
    }),

  // ---- Agent 对外调用秘钥（AgentApiKeyController，登录态） ----
  /** 秘钥列表（仅掩码，不含密文 / 明文） */
  apiKeyList: (agentNum: string) =>
    get<AgentApiKeyVO[]>(`${API_KEY_BASE}/query/list`, { agentNum }),
  /** 创建秘钥并返回本次明文（仅此次回显） */
  apiKeyCreate: (agentNum: string, remark: string) =>
    post<AgentApiKeyCreatedVO>(`${API_KEY_BASE}/command/create`, {
      agentNum,
      remark,
    }),
  /** 查看秘钥（小眼睛，单条解密）；登录态 + 归属校验 + 后端审计 */
  apiKeyReveal: (agentNum: string, num: string) =>
    get<AgentApiKeyPlainVO>(`${API_KEY_BASE}/query/reveal`, { agentNum, num }),
  /** 删除秘钥（逻辑删，认证立即失效） */
  apiKeyDelete: (agentNum: string, num: string) =>
    post<void>(`${API_KEY_BASE}/command/delete`, { agentNum, num }),
};

export const agentQueryKeys = {
  page: () => ['agent', 'page'] as const,
  detail: (num: string) => ['agent', 'detail', num] as const,
  versions: (agentNum: string) => ['agent', 'versions', agentNum] as const,
  /** 2026-07-28：调试可选版本 */
  debugVersions: (agentNum: string) =>
    ['agent', 'debugVersions', agentNum] as const,
  versionDetail: (agentNum: string, versionNum: string) =>
    ['agent', 'version', agentNum, versionNum] as const,
  /** v2.6 新增：A2A 同步历史 */
  a2aHistory: (agentNum: string) =>
    ['agent', 'a2aHistory', agentNum] as const,
  /** Agent 优化：对外调用秘钥列表 */
  apiKeys: (agentNum: string) =>
    ['agent', 'apiKeys', agentNum] as const,
};
