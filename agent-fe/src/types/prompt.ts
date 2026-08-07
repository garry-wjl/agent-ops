/**
 * Prompt 中心类型 — 与后端 rd-agent-be《Prompt 中心 技术方案 v1.0》对齐。
 *
 * 字段以技术方案 §4.2.1 领域模型 / §7 接口契约 / §8.1 表结构为准：
 * - 单聚合 `Prompt`，无状态机、无版本、无子实体（§0 #6）。
 * - 业务编码 `num` 系统生成（前缀 PRM），用户不可见、不可填（§0 #3）。
 * - 模板内容原样存储，前后端均不解析 `{{变量}}`（§0 #5）。
 * - 工作空间内两个唯一键：`(workspaceNum, num)`、`(workspaceNum, promptKey)`（§0 #1）。
 *   `workspaceNum` 由后端经 `X-Workspace-Num` 头注入，前端读写均不传。
 * - 审计字段为 `createNo / updateNo / createTime / updateTime`（非 createdBy/createdAt）。
 */
import type { PageParam } from "./common";

/**
 * Prompt 视图对象（列表项 / 命令返回 / 与后端 PromptVo 一一对应）。
 */
export interface PromptVo {
  /** 业务编码：系统生成，前缀 PRM + yyyyMMddHHmm + 序号 */
  num: string;
  /** 归属工作空间业务编号（后端注入） */
  workspaceNum: string;
  /** 用户填写的稳定引用键（工作空间内唯一，≤128） */
  promptKey: string;
  /** 描述（选填，≤500） */
  description?: string;
  /** 模板原文（含 {{变量}}，原样存储不解析；必填，≤20000） */
  templateContent: string;
  /** 标签（≤20 个，单 tag ≤32） */
  tags?: string[];
  /** 创建人工号 */
  createNo: string;
  /** 更新人工号 */
  updateNo: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/** Prompt 详情（后端 PromptDetailVo 为嵌套结构，预留扩展，与 ToolDetailVO 同款）。 */
export interface PromptDetailVo {
  /** Prompt 全字段快照 */
  prompt: PromptVo;
}

/**
 * 新建 Prompt 入参（与后端 PromptCreateParam 对齐）。
 * num / workspaceNum / operatorId 均不在入参中（系统生成 / 后端注入）。
 */
export interface PromptCreateParam {
  /** 引用键（必填，≤128，工作空间内唯一） */
  promptKey: string;
  /** 描述（选填，≤500） */
  description?: string;
  /** 模板原文（必填，≤20000） */
  templateContent: string;
  /** 标签（可选，≤20 个） */
  tags?: string[];
}

/**
 * 编辑 Prompt 入参（与后端 PromptUpdateParam 对齐）。
 * 按 num 加载后覆盖可填字段；promptKey 变更时后端做唯一性预检（排除自身）。
 */
export interface PromptUpdateParam {
  /** 业务编码（必填，定位待编辑 Prompt） */
  num: string;
  /** 引用键（≤128，工作空间内唯一） */
  promptKey?: string;
  /** 描述（≤500） */
  description?: string;
  /** 模板原文（≤20000） */
  templateContent?: string;
  /** 标签（≤20 个） */
  tags?: string[];
}

/** 单编号操作入参（delete 用，对齐后端 PromptNumParam）。 */
export interface PromptNumParam {
  /** 业务编码 */
  num: string;
}

/** Prompt 列表分页查询入参（继承通用分页参数；筛选字段均可选）。 */
export interface PromptPageQueryParam extends PageParam {
  /** 关键词（在 num / promptKey / description 内 LIKE 匹配） */
  keyword?: string;
  /** 按标签筛选 */
  tag?: string;
}

/** promptKey 唯一性校验结果（GET /api/v1/prompt/query/checkKey 返回 boolean：true=已存在）。 */
export type PromptKeyExists = boolean;
