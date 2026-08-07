/**
 * 模型管理类型 — 与后端 rd-agent-be 模型管理技术方案 v1.0 + 2026-06-17 scope 优化对齐
 *
 * 字段以后端 client.model.vo / dto 的实际契约为准（技术方案 §7.2.1 / §12）：
 * - **scope 维度**：SPACE=空间模型（归属某工作空间）/ PLATFORM=系统模型（全平台共享，workspaceNum 为空）
 *   空间入口走 `/api/v1/model/*`，系统入口走 `/api/v1/system/model/*`（由 `system:manage_settings` 权限控制）
 * - API Key 出参一律脱敏字段 `apiKeyMasked`（prefix + ****），**前端绝不接收明文 / 密文**；
 *   **系统模型 VO 连 apiKeyMasked 也没有**（后端 PLATFORM 不组装该字段）
 * - 编辑入参 `apiKey` 留空表示保留原值，填了新值才覆盖重加密（技术方案 §6.2 / 共识 #8）
 * - **编辑不允许变更 scope**（方案 §1.2 决策 4，归属在创建时由可信入口决定）
 * - 审计字段为 `createNo / updateNo / createTime / updateTime`（与 Sandbox / Tool 同构）
 * - num 由系统生成（前缀 MDL）；modelId 为用户填写的对外模型标识，两者分离（共识 #2）
 *
 * 三态生命周期（共识 #4 / §4.2.2）：
 *   DRAFT 草稿（仅此态可软删）→ enable → ENABLED 启用
 *   ENABLED ⇄ DISABLED（disable / enable 互转）
 *   enable 允许 DRAFT/DISABLED → ENABLED；disable 仅 ENABLED → DISABLED；delete 仅 DRAFT。
 */
import type { PageParam } from './common';

/** 模型生命周期状态（3 态）。 */
export type ModelStatus = 'DRAFT' | 'ENABLED' | 'DISABLED';

/**
 * 模型归属范围（2026-06-17 scope 优化）。
 * - SPACE：空间模型，归属某工作空间，仅该空间可管理 / 选用
 * - PLATFORM：系统模型，由平台管理员维护，全平台可选用
 */
export type ModelScope = 'SPACE' | 'PLATFORM';

/** 模型视图对象（列表项 / 命令返回，与后端 ModelVO 一一对应）。
 *  <p>系统模型（scope=PLATFORM）的 `apiKeyMasked` 后端恒为空。 */
export interface ModelVO {
  /** 业务编号 MDL+yyyyMMddHHmm+4位序号 */
  num: string;
  /** 归属工作空间业务编号；系统模型为空 */
  workspaceNum?: string;
  /** 归属范围：SPACE / PLATFORM */
  scope?: ModelScope;
  /** 模型名称（空间内 / 平台内唯一） */
  name: string;
  /** 用户填写的模型标识（空间内 / 平台内唯一） */
  modelId: string;
  /** API Key 脱敏串（prefix + ****），绝不含明文 / 密文；系统模型为空 */
  apiKeyMasked?: string;
  /** 模型服务端点 Base URL */
  baseUrl: string;
  /** 状态 */
  status: ModelStatus;
  /** 备注 */
  remark?: string;
  /** 创建人工号 */
  createNo: string;
  /** 更新人工号 */
  updateNo: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/** 模型列表分页查询入参（继承通用分页参数；筛选字段均可选）。 */
export interface ModelPageQueryParam extends PageParam {
  /** 按名称筛选；为空表示不限 */
  name?: string;
  /** 按模型标识筛选；为空表示不限 */
  modelId?: string;
  /** 按状态筛选；为空表示不限 */
  status?: ModelStatus;
  /** 关键词（在 num / name / modelId / remark 内 LIKE 匹配） */
  keyword?: string;
}

/**
 * 新建模型入参（与后端 ModelCreateParam 对齐）。
 * <p>scope 由前端按入口设定（空间入口 SPACE + workspaceNum；系统入口 PLATFORM + workspaceNum 留空），
 *    后端最终按入口和权限重判（不信任前端）。workspaceNum 亦可由 request 拦截器 X-Workspace-Num 头注入。
 */
export interface ModelCreateParam {
  /** 归属工作空间业务编号（SPACE 必填；PLATFORM 留空由后端置 null） */
  workspaceNum?: string;
  /** 归属范围：SPACE / PLATFORM */
  scope?: ModelScope;
  /** 模型名称（必填，≤128，空间内 / 平台内唯一） */
  name: string;
  /** 用户填写的模型标识（必填，≤128，空间内 / 平台内唯一） */
  modelId: string;
  /** 模型 API Key 明文（必填，提交后端加密落库） */
  apiKey: string;
  /** 模型服务端点（必填，须 http(s) 开头） */
  baseUrl: string;
  /** 备注（可空，≤500 字） */
  remark?: string;
}

/**
 * 编辑模型入参（与后端 ModelUpdateParam 对齐）。
 * <p>**不含 scope**：编辑不允许变更模型归属（方案 §1.2 决策 4），归属一致性由后端入口守卫。
 *    apiKey 留空（空串 / 不传）表示保留原密文，填了新值才覆盖重加密；状态由专用接口流转，update 不改状态。
 */
export interface ModelUpdateParam {
  /** 模型业务编号（必填） */
  num: string;
  /** 模型名称（≤128，空间内 / 平台内唯一） */
  name: string;
  /** 用户填写的模型标识（≤128，空间内 / 平台内唯一） */
  modelId: string;
  /** API Key 明文；留空保留原值，填了才覆盖 */
  apiKey?: string;
  /** 模型服务端点（须 http(s) 开头） */
  baseUrl: string;
  /** 备注（可空，≤500 字） */
  remark?: string;
}

/** 单编号操作入参（delete / enable / disable 复用，与后端 ModelOperateParam 对齐）。 */
export interface ModelOperateParam {
  /** 模型业务编号 */
  num: string;
}

/**
 * 模型详情视图对象（与后端 ModelDetailVO 对齐）。
 * 本期详情即模型全字段快照，以嵌套 `model` 字段承载，预留后续扩展（如调用计量）。
 */
export interface ModelDetailVO {
  /** 模型全字段快照（apiKey 脱敏；系统模型无 Key 字段） */
  model: ModelVO;
}

/**
 * Agent 可选模型视图对象（与后端 ModelSelectableVO 对齐，2026-06-17 新增）。
 * <p>用于 Agent 配置的模型下拉：系统启用模型 + 当前空间启用模型的合集。
 *    **不含任何 API Key 字段**（明文 / 密文 / 脱敏 / prefix 均无）。
 */
export interface ModelSelectableVO {
  /** 模型业务编号 */
  num: string;
  /** 归属范围：PLATFORM / SPACE */
  scope: ModelScope;
  /** 归属工作空间；系统模型为空 */
  workspaceNum?: string;
  /** 模型名称 */
  name: string;
  /** 用户填写的模型标识 */
  modelId: string;
  /** 模型服务端点 Base URL */
  baseUrl: string;
  /** 状态：ENABLED */
  status: ModelStatus;
}
