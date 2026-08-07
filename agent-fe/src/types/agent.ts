/**
 * Agent 领域类型 — 对齐 Agent 管理技术方案 §3.2 / §10
 */

export type CreationMode = 'CONFIG' | 'ACP' | 'MCP' | 'A2A' | 'API';
export type AgentType = 'NORMAL' | 'SUPERVISOR' | 'ROUTER';
/**
 * v2.6 新增 PENDING_SYNC：A2A 草稿已确认订阅、等待 Nacos 首次推送 AgentCard 的过渡态。
 */
export type AgentStatus =
  | 'DRAFT_ONLY'
  | 'PENDING_SYNC'
  | 'PUBLISHED'
  | 'OFFLINE';
export type ChangeLevel = 'PATCH' | 'MINOR' | 'MAJOR';

/**
 * v2.5 重构：短期 / 长期记忆策略字段，前端区分 NONE / RECENT_N / SLIDING_WINDOW 与 NONE / VECTOR_RECALL / FULLTEXT_RECALL。
 * 兼容原 shortTermEnabled / longTermEnabled，旧字段过渡期保留可选。
 */
export type ShortTermStrategy = 'NONE' | 'RECENT_N' | 'SLIDING_WINDOW';
export type LongTermStrategy = 'NONE' | 'VECTOR_RECALL' | 'FULLTEXT_RECALL';

export interface MemoryConfig {
  shortTermStrategy?: ShortTermStrategy;
  shortTermN?: number;
  longTermStrategy?: LongTermStrategy;
  /** @deprecated v2.5 起改用 shortTermStrategy；仅兼容旧 snapshot */
  shortTermEnabled?: boolean;
  /** @deprecated v2.5 起改用 longTermStrategy；仅兼容旧 snapshot */
  longTermEnabled?: boolean;
}

/** 接入式 Agent 的 modeConfig 结构（API 模式） */
export interface ApiModeConfig {
  baseUrl: string;
  auth: { type: 'BEARER' | 'BASIC' | 'NONE'; credential?: string };
  invoke: {
    protocol: 'SSE' | 'WEBSOCKET';
    path: string;
    eventMapping?: Record<string, string>;
  };
  createSession?: { path: string; responseSessionIdJsonPath: string };
  listSessions?: {
    path: string;
    pagination?: 'OFFSET_LIMIT' | 'CURSOR';
    responseItemsJsonPath?: string;
    responseTotalJsonPath?: string;
  };
  getSession?: { path: string; responseMessagesJsonPath?: string };
}

/** 通用 ConfigSnapshot — 见 Agent 方案 §3.2.1 */
export interface ConfigSnapshot {
  /** v3.0：Agent 元信息纳入版本快照；发布时同步到 agent 主表 */
  name?: string;
  description?: string;
  creationMode: CreationMode;
  agentType: AgentType;
  systemPrompt?: string;
  userPrompt?: string;
  /**
   * 2026-06-11 Agent 配置优化：模型管理业务编号（num，前缀 MDL）引用。
   * <p>替代原手填 model / modelApiKey / modelBaseUrl；运行时由后端按 modelId 解析模型管理记录装配 LLM。
   * <p>2026-06-17 scope 优化：可选系统模型或当前空间模型，后端在保存/发布阶段校验归属。
   */
  modelId?: string;
  temperature?: number;
  /**
   * 2026-06-17 模型管理优化：是否启用 Plan 模式。
   * <p>本期仅持久化和回显，运行时不消费；缺省按 false 处理。
   */
  enablePlan?: boolean;
  /** 最大迭代轮次（ReAct 循环次数），默认 10 */
  maxIters?: number;
  skillNums?: string[];
  /**
   * 2026-06-11 Agent 配置优化：工具引用列表（原 mcpNums 重命名，语义含 MCP / FunctionCall）。
   * <p>来自工具管理「已发布」工具的 num 列表，多选。
   */
  toolNums?: string[];
  /**
   * 2026-06-17 资源版本钉住：Skill 版本引用列表（新写入优先字段）。
   * <p>元素为 skillNum + versionNum；与 skillNums 并存，旧数据可只有 skillNums，由后端兜底补齐。
   */
  skillRefs?: SkillRefParam[];
  /**
   * 2026-06-17 资源版本钉住：工具版本引用列表（统一契约）。
   * <p>Tool 具备版本能力后按 versionNum 解析；当前 Tool 无版本表时 versionNum 可空。
   */
  toolRefs?: ToolRefParam[];
  /**
   * 2026-06-11 Agent 配置优化：沙箱引用（单选可空）。
   * <p>来自沙箱管理「在线」沙箱的 num；空表示不挂载沙箱。
   */
  sandboxRef?: string;
  childAgentNums?: string[];
  memoryConfig?: MemoryConfig;
  qps?: number;
  dailyBudget?: number;
  /** 接入式 5 mode 的差异化配置 */
  modeConfig?: ApiModeConfig | Record<string, any>;
}

/**
 * Skill 版本引用（2026-06-17 资源版本钉住，与后端 SkillRefParam 对齐）。
 */
export interface SkillRefParam {
  /** Skill 业务编号 */
  skillNum: string;
  /** 发布版本号 */
  versionNum: string;
}

/**
 * 工具版本引用（2026-06-17 资源版本钉住，与后端 ToolRefParam 对齐）。
 * <p>当前 Tool 无独立版本表时 versionNum 可为空。
 */
export interface ToolRefParam {
  /** 工具业务编号 */
  toolNum: string;
  /** 发布版本号；可为空 */
  versionNum?: string;
}

/** 列表项 — 对齐 PRD v2.1 / v2.2：精简字段并新增 agentSource 派生字段 */
export interface AgentVO {
  num: string;
  name: string;
  description?: string;
  status: AgentStatus;
  /** 挂载/远端能力 Skill 数 */
  skillNum: number;
  /** Skill 名称数组（tooltip 用；CONFIG 取 configSnapshot.skillNums 反查名称，A2A 取 a2aSource.remoteSkills[].name） */
  skillNames: string[];
  /** v2.2 新增：MANUAL = 人工创建（CONFIG）；NACOS = Nacos 注册同步（A2A） */
  agentSource: 'MANUAL' | 'NACOS';
  creationMode: CreationMode;
  /** v2.6 新增：CONFIG 模式当前在线版本号；A2A 恒为 undefined。详情页基本信息卡片需展示 */
  currentVersionNum?: string;
  createTime: string;
  updateTime: string;
}

/** A2A 模式 Agent Card 中声明的远端 Skill */
export interface RemoteSkill {
  name: string;
  description?: string;
}

/** v2.3：A2A 模式 Agent Card 中声明的远端 MCP 接入项 */
export interface RemoteMcp {
  name: string;
  description?: string;
  serverUrl?: string;
}

/** A2A 来源信息 — 对齐技术方案 §10.3 / §3.2 */
export interface A2aSourceVO {
  nacosGroup: string;
  nacosService: string;
  instanceIp?: string;
  instancePort?: number;
  endpointPath?: string;
  remoteVersion?: string;
  remoteSkills: RemoteSkill[];
  /** v2.3 新增：与 remoteSkills 对称，远端 Agent Card 声明的 MCP 接入数组 */
  remoteMcps?: RemoteMcp[];
  /** 远端 Agent Card 原文（JSON 字符串），详情页"查看原始 Agent Card"按钮使用 */
  agentCardJson?: string;
  lastSyncedAt?: string;
  lastSyncEventType?: string;
}

/** 详情 — CONFIG 含 currentVersion / hasDraft；A2A 含 a2aSource */
export interface AgentDetailVO {
  num: string;
  name: string;
  description?: string;
  /** 业务标签（CONFIG / A2A 共用） */
  tags?: string[];
  status: AgentStatus;
  creationMode: CreationMode;
  agentType?: AgentType;
  ownerUserId?: string;
  model?: string;
  createTime?: string;
  updateTime?: string;
  /** CONFIG 模式：当前在线版本号（A2A 恒为 undefined） */
  currentVersionNum?: string;
  /** CONFIG 模式：是否存在草稿 */
  hasDraft?: boolean;
  /** CONFIG 模式：当前版本快照 */
  currentVersion?: AgentVersionVO & { configSnapshot?: ConfigSnapshot };
  /** A2A 模式：Nacos 来源 + Agent Card 元数据 */
  a2aSource?: A2aSourceVO;
  /** 接入式探活（保留以兼容历史代码） */
  healthStatus?: 'GREEN' | 'YELLOW' | 'RED';
}

/**
 * v3.0：版本生命周期状态
 * - DRAFT     : 草稿（编辑中），可 [编辑] / [发布] / [删除]
 * - PUBLISHED : 已发布；current=true 表示在线版本，current=false 表示已被新版本替换的历史
 * - ARCHIVED  : 历史归档（兼容后端老语义；前端与 PUBLISHED+current=false 同样视为「历史」）
 */
export type AgentVersionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/**
 * v3.0：统一版本视图。草稿与已发布版本共用此结构。
 * - DRAFT 行：versionNum / changeLevel / publishedBy / publishedAt / remark 均为空；
 *   editorUserId / lockUntil 由 runtime 写入。
 * - PUBLISHED / ARCHIVED 行：完整字段；不写 editorUserId / lockUntil。
 */
export interface AgentVersionVO {
  num: string;
  agentNum: string;
  /** v3.0：版本生命周期状态 */
  status: AgentVersionStatus;
  /** 已发布版本号 v1.0.0；DRAFT 行为 undefined */
  versionNum?: string;
  /** 兼容字段：旧代码中读 version；后端按需仍可下发 */
  version?: string;
  changeLevel?: ChangeLevel;
  remark?: string;
  publishedBy?: string;
  publishedAt?: string;
  /** 是否在线 — 仅对 PUBLISHED 行有意义 */
  current?: boolean;
  /** DRAFT 行：当前编辑者 user id */
  editorUserId?: string;
  /** DRAFT 行：编辑锁过期时间 */
  lockUntil?: string;
  /**
   * v3.0：可选 configSnapshot 直接随 versionList 一起下发，前端无需再二次拉取；
   * 若后端未实现，FE 退化为按 versionId / versionNum 调 versionDetail。
   */
  configSnapshot?: ConfigSnapshot;
}

export interface AgentVersionDetailVO extends AgentVersionVO {
  configSnapshot: ConfigSnapshot;
}

/**
 * 调试可选版本（调试台版本选择器数据源）。
 * 对齐后端 AgentDebugVersionVO（GET /api/v1/agents/debug-versions）。
 * status=DRAFT 时 versionNum 为空，调试须以 'DRAFT' 令牌作为 target_version 请求。
 */
export interface AgentDebugVersionVO {
  /** 已发布版本号；DRAFT 行为空 */
  versionNum?: string;
  /** 版本生命周期状态 */
  status: AgentVersionStatus;
  /** 状态中文标注：草稿态 / 发布态 / 历史态 */
  statusLabel: string;
  /** 是否为当前在线版本 */
  current: boolean;
  /** 发布时间；DRAFT 行为空 */
  publishedTime?: string;
  /** 发布备注 */
  remark?: string;
}

/**
 * Agent 已绑定 Skill 的版本状态（是否有新版本 / 是否已失效）。
 * 对齐后端 AgentSkillBindingStatusVO（GET /api/v1/agents/skill-binding-status）。
 */
export interface AgentSkillBindingStatusVO {
  skillNum: string;
  skillName?: string;
  /** 已绑定的版本号 */
  boundVersion?: string;
  /** Skill 当前最新在线版本号 */
  latestVersion?: string;
  /** 绑定版本落后于最新版 */
  hasNewer: boolean;
  /** 绑定的版本已下架 / 失效 */
  boundDeprecated: boolean;
}

export interface AgentDraftVO {
  draftNum: string;
  agentNum: string;
  baseVersionNum?: string;
  configDraft: ConfigSnapshot;
  editorUserId: string;
  lockUntil?: string;
}

/**
 * 创建 Agent 入参（v2.4 简化 + v2.5 扩展 + 2026-06-11 资产化改造 + 2026-06-17 资源版本钉住）
 * - 后端 v2.0 起 creationMode 由 server 固定为 CONFIG，前端不再传
 * - modeConfig 已下线（ACP/MCP/A2A/API 接入式整体移除）
 * - 2026-06-11 Agent 配置优化：删手填 model/modelApiKey/modelBaseUrl，
 *   改为 modelId（模型管理 num 引用）；新增 sandboxRef（沙箱引用）；mcpNums → toolNums（工具引用）
 * - 2026-06-17：新增 enablePlan（仅持久化/展示）；skillRefs/toolRefs 钉住资源版本
 */
export interface AgentCreateParam {
  name: string;
  description?: string;
  /** 业务标签（可空，CONFIG / A2A 共用） */
  tags?: string[];
  agentType: AgentType;
  /** 系统提示词，作用于全部对话（对齐后端 AgentCreateParam.systemPrompt） */
  systemPrompt?: string;
  /** 用户提示词模板，可含占位符（对齐后端 AgentCreateParam.userPrompt） */
  userPrompt?: string;
  /** 模型管理业务编号（num，前缀 MDL）引用；运行时按 modelId 解析模型管理记录装配 LLM */
  modelId?: string;
  temperature?: number;
  /** 2026-06-17：是否启用 Plan 模式（仅持久化/展示，运行时不消费） */
  enablePlan?: boolean;
  /** 最大迭代轮次（ReAct 循环次数），默认 10 */
  maxIters?: number;
  skillNums?: string[];
  /** 挂载工具编码列表（原 mcpNums，语义含 MCP / FunctionCall，多选） */
  toolNums?: string[];
  /** 2026-06-17：Skill 版本引用（优先字段，与 skillNums 并存） */
  skillRefs?: SkillRefParam[];
  /** 2026-06-17：工具版本引用（统一契约） */
  toolRefs?: ToolRefParam[];
  /** 沙箱引用（单选可空，来自沙箱管理「在线」沙箱 num） */
  sandboxRef?: string;
  childAgentNums?: string[];
  memoryConfig?: MemoryConfig;
  qps?: number;
  dailyBudget?: number;
}

export interface AgentDraftParam {
  num: string;
  configDraft: ConfigSnapshot;
}

/**
 * v2.6：A2A 模式接入入参 — POST /api/v1/agent/command/createA2a
 * - draftAgentNum 非空表示由"草稿"转正接入
 */
export interface A2aCreateParam {
  nacosAgentName: string;
  displayName?: string;
  description?: string;
  remark?: string;
  draftAgentNum?: string;
}

/**
 * v2.6：A2A 模式保存草稿入参 — POST /api/v1/agent/command/saveA2aDraft
 * - agentNum 非空表示更新已有草稿；否则新建草稿
 */
export interface A2aDraftParam {
  nacosAgentName?: string;
  displayName?: string;
  description?: string;
  remark?: string;
  agentNum?: string;
}

/**
 * v2.6：A2A 同步历史项 — GET /api/v1/agent/version/a2a-history
 */
export interface A2aSyncHistoryVO {
  id: string;
  remoteVersion?: string;
  syncEventType: string;
  triggeredBy: 'AUTO' | 'MANUAL';
  syncedAt: string;
  agentCardJson?: string;
}

/**
 * v3.0：发布入参
 * - versionId：优先字段，指向 DRAFT 版本的 num；后端据此把该 DRAFT 升为 PUBLISHED
 * - agentNum：兼容期保留，旧调用方仍可只传 agentNum，后端自动选取唯一 DRAFT
 */
export interface PublishParam {
  agentNum?: string;
  versionId?: string;
  remark: string;
}

/* ============ Agent 对外调用秘钥（apiKey）—— Agent 优化 ============ */

/** 秘钥列表项（仅掩码，不含密文 / 明文） */
export interface AgentApiKeyVO {
  /** 秘钥业务编号 AK... */
  num: string;
  /** 归属 Agent 业务编号 */
  agentNum: string;
  /** 用户备注 */
  remark: string;
  /** 掩码展示串（key_prefix + ****） */
  keyMasked: string;
  /** 创建人工号 */
  createNo?: string;
  /** 创建时间 */
  createTime?: string;
  /** 最近一次成功认证时间；从未使用为 null */
  lastUsedAt?: string;
}

/** 创建秘钥入参 */
export interface AgentApiKeyCreateParam {
  agentNum: string;
  remark: string;
}

/** 创建秘钥出参：本次明文仅此一次回显 */
export interface AgentApiKeyCreatedVO {
  num: string;
  key: string;
}

/** 小眼睛单条解密出参 */
export interface AgentApiKeyPlainVO {
  num: string;
  key: string;
}


export interface TestResultItem {
  name: string;
  ok: boolean;
  latencyMs: number;
  detail?: string;
}

export interface TestResultVO {
  items: TestResultItem[];
  allOk: boolean;
}

export interface JsonPatchOp {
  op: 'add' | 'remove' | 'replace' | 'move' | 'copy' | 'test';
  path: string;
  oldValue?: unknown;
  newValue?: unknown;
  value?: unknown;
}

export interface VersionDiffVO {
  patches: JsonPatchOp[];
}

export interface AgentPageQuery
  extends Partial<{
    creationMode: CreationMode;
    status: AgentStatus;
    keyword: string;
  }> {
  pageNo: number;
  pageSize: number;
}
