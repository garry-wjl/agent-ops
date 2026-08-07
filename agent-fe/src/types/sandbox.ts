/**
 * 沙箱管理类型 — 与后端 rd-agent-be 沙箱管理技术方案 v1.0 对齐
 *
 * 字段以后端 client.sandbox.vo / dto 的实际契约为准（注意命名）：
 * - 内存字段为 `memoryMb`（非 PRD §7.1 的 `memory`）
 * - 审计字段为 `createNo / updateNo / createTime / updateTime`（非 createdBy/createdAt）
 * - 状态为 5 态枚举，含 `FAILED`（供给失败态；PRD UI 表只画了 4 态，以后端枚举为准）
 *
 * 后端命令接口仅 create / update / delete / submit / offline / reonline，
 * 没有手动 online —— 上线由后端 SandboxRunner 监听 SANDBOX_SUBMITTED 异步建容器后自动转 ONLINE。
 */
import type { PageParam } from "./common";

/** 沙箱类型：本期仅代码沙箱 CODE（枚举预留扩展位）。 */
export type SandboxType = "CODE";

/**
 * 沙箱生命周期状态（5 态）。
 * - DRAFT 草稿 / INITIALIZED 初始化 / ONLINE 在线 / OFFLINE 下线 / FAILED 失败
 */
export type SandboxStatus =
  | "DRAFT"
  | "INITIALIZED"
  | "ONLINE"
  | "OFFLINE"
  | "FAILED";

/** 沙箱视图对象（列表项 / 命令返回，与后端 SandboxVO 一一对应）。 */
export interface SandboxVO {
  /** 业务编号 SBX+yyyyMMddHHmm+4位序号 */
  num: string;
  /** 归属工作空间业务编号 */
  workspaceNum: string;
  /** 沙箱名称 */
  name: string;
  /** 沙箱类型 */
  type: SandboxType;
  /** CPU 核数（0.5 步进） */
  cpu: number;
  /** 内存大小（MB） */
  memoryMb: number;
  /** 容器存活时间（分钟） */
  aliveMinutes: number;
  /** 状态 */
  status: SandboxStatus;
  /** 备注 */
  remark?: string;
  /** OpenSandbox 容器实例 id；草稿 / 失败态为空 */
  sandboxInstanceId?: string;
  /** 创建人工号 */
  createNo: string;
  /** 更新人工号 */
  updateNo: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/** 沙箱详情（后端 SandboxDetailVO 为嵌套结构，预留扩展）。 */
export interface SandboxDetailVO {
  /** 沙箱全字段快照 */
  sandbox: SandboxVO;
}

/** 沙箱列表分页查询入参（继承通用分页参数；筛选字段均可选）。 */
export interface SandboxPageQueryParam extends PageParam {
  /** 按类型筛选；为空表示不限 */
  type?: SandboxType;
  /** 按状态筛选；为空表示不限 */
  status?: SandboxStatus;
  /** 关键词（在 num / name / remark 内 LIKE 匹配） */
  keyword?: string;
}

/**
 * 新建沙箱入参（与后端 SandboxCreateParam 对齐）。
 * workspaceNum 由 request 拦截器经 X-Workspace-Num 头注入后端从上下文取，前端可不传。
 */
export interface SandboxCreateParam {
  /** 归属工作空间业务编号（可选，后端从空间上下文取） */
  workspaceNum?: string;
  /** 沙箱名称（必填，1~64 字符） */
  name: string;
  /** 沙箱类型（本期固定 CODE） */
  type: SandboxType;
  /** CPU 核数（必填，0.5 步进，区间 0.5~16） */
  cpu: number;
  /** 内存大小（MB，必填，区间 128~65536） */
  memoryMb: number;
  /** 容器存活时间（分钟，必填，区间 1~1440） */
  aliveMinutes: number;
  /** 备注（可空，≤100 字） */
  remark?: string;
}

/**
 * 编辑沙箱入参（与后端 SandboxUpdateParam 对齐）。
 * 草稿 / 失败态可改规格字段，其余态后端仅写入备注。
 */
export interface SandboxUpdateParam {
  /** 沙箱业务编号（必填） */
  num: string;
  /** 沙箱名称（草稿 / 失败态可改） */
  name?: string;
  /** CPU 核数（草稿 / 失败态可改） */
  cpu?: number;
  /** 内存大小（MB，草稿 / 失败态可改） */
  memoryMb?: number;
  /** 容器存活时间（分钟，草稿 / 失败态可改） */
  aliveMinutes?: number;
  /** 备注（任意非删除态可改，≤100 字） */
  remark?: string;
}

/** 单编号操作入参（delete / submit / offline / reonline 复用，与后端 SandboxOperateParam 对齐）。 */
export interface SandboxOperateParam {
  /** 沙箱业务编号 */
  num: string;
}
