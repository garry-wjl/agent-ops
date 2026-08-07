/**
 * 工作空间类型 — 与 BE 工作空间管理技术方案 v1.0 对齐
 * 详见 doc：2026-06-03_工作空间管理-技术方案.md（§7.1 字段 / §5 接口）
 *
 * 关键约定：
 * - 聚合只有 save / delete；界面只有 创建 / 编辑 / 删除 三个写操作
 * - 成员关系是 adminList / memberList 两个工号字符串数组；编辑=整体覆盖提交
 * - 角色仅 ADMIN / MEMBER 两层
 */

/** 空间内角色 */
export type WorkspaceRole = 'ADMIN' | 'MEMBER';

/** 工作空间卡片 VO（列表 / 切换器用） */
export interface WorkspaceVO {
  /** 业务编号（前缀 WS-） */
  num: string;
  /** 空间名称 */
  name: string;
  /** 空间描述（≤200 字） */
  description?: string;
  /** 成员总数（管理员 + 普通成员） */
  memberCount: number;
  /** 管理员数 */
  adminCount: number;
  /** 当前登录用户在该空间的角色 */
  myRole: WorkspaceRole;
  /** 当前登录用户是否为创建人 */
  isCreator: boolean;
  /** 创建时间 ISO 串 */
  createTime?: string;
}

/** 空间成员行（详情 / 编辑抽屉用） */
export interface WorkspaceMemberVO {
  /** 员工工号（权威键） */
  empNo: string;
  /** 姓名（通讯录解析，仅展示态） */
  displayName?: string;
  /** 角色 */
  role: WorkspaceRole;
  /** 加入时间 ISO 串 */
  joinedAt?: string;
}

/** 空间详情 VO（编辑抽屉用，含成员列表） */
export interface WorkspaceDetailVO extends WorkspaceVO {
  /** 创建人工号 */
  createNo?: string;
  /** 全部成员（管理员 + 普通成员，按 role 区分两栏） */
  members: WorkspaceMemberVO[];
}

/** 创建空间入参（创建人自动入 adminList + RL-SPACE-ADMIN） */
export interface WorkspaceCreateParam {
  name: string;
  description?: string;
  /** 可选：额外初始管理员工号（v2 推荐用 memberRoles 替代） */
  initialAdminEmpNos?: string[];
  /** 可选：初始普通成员工号（v2 推荐用 memberRoles 替代） */
  initialMemberEmpNos?: string[];
  /**
   * 整空间用户-角色映射（roleNum → empNo 列表），创建时按角色批量绑人。
   * 创建人自动绑定 RL-SPACE-ADMIN（前端必在 map 中携带创建人）。
   */
  memberRoles?: Record<string, string[]>;
}

/** 编辑空间入参（整体覆盖：名称 + 描述 + 完整管理员 / 成员两栏 + 整空间用户-角色映射） */
export interface WorkspaceUpdateParam {
  num: string;
  name: string;
  description?: string;
  /** 完整管理员工号列表（至少 1 个） */
  adminEmpNos: string[];
  /** 完整普通成员工号列表 */
  memberEmpNos: string[];
  /**
   * 整空间用户-角色映射（empNo → roleNum 列表）。
   * 缺省时后端跳过 bindUserRoles，保持与旧接口兼容。
   */
  memberRoles?: Record<string, string[]>;
}

/** 删除空间入参 */
export interface WorkspaceDeleteParam {
  num: string;
}

/** 员工档案（通用通讯录搜索结果） */
export interface EmployeeProfileVO {
  empNo: string;
  displayName?: string;
  dept?: string;
}

/** 员工搜索入参 */
export interface EmployeeSearchParam {
  keyword: string;
  limit?: number;
}
