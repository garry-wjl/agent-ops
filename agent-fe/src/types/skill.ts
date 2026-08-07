/**
 * Skill 领域类型 — 对齐 BE Skill 管理 技术方案 v2.11
 *
 * v2.11 变更要点：
 * - SkillStatus: `DRAFT_ONLY` → `DRAFT`
 * - SkillVersion: 删 changeLevel/changeNote/publishedBy/publishedAt/current，
 *   新增 name/description/tags/skillFileKey/status；`versionNum` → `version`
 * - 删除 SkillFileType / SkillSnapshot / SyncResultVO / SkillSnapshot
 * - SkillDetailVO 改为嵌套结构 `{skill, currentVersion, reuseCount}`
 * - SkillCreateParam: skillFileKey + version 平铺，不再 fileId/skillFileType/changeNote
 * - SkillUpdateParam: skillNum → num
 * - SkillPublishParam: 仅 skillNum + version
 * - VersionDiffVO: 仅字段级 diff（nameDiff/descriptionDiff/tagsDiff）；mdDiff 待 BE
 *   SkillFileStorage 接入后再补
 * - 新增 SkillRollbackParam / SkillDiscardDraftAction（仅路径，无独立类型）
 */

export type SkillSource = 'SELF' | 'COMPANY';

/**
 * Skill 生命周期状态。
 *
 * 2026-06-10 Skill 管理优化：在「草稿 → 发布」之间插入两个检测态
 * （对齐技术方案 §4.2.1 / PRD §4.3）：
 * - `CHECKING`：发布触发后、三检进行中的瞬时态（不可挂载 / 不对调用方可见）
 * - `CHECK_FAILED`：三检任一不通过，可回到草稿修复后重新发布
 */
export type SkillStatus =
  | 'DRAFT'
  | 'CHECKING'
  | 'CHECK_FAILED'
  | 'PUBLISHED'
  | 'DEPRECATED';

// ============================================================
// 2026-06-10 新增：资源文件树（入库存储，替代对象存储 skillFileKey）
// 对齐技术方案 §4.2.1 SkillResourceFile 值对象 + §8.1 skill_resource_file 表
// ============================================================

/** 资源节点类型：文件 / 文件夹。 */
export type SkillResourceFileType = 'FILE' | 'FOLDER';

/** 文件内容编码：文本原文 / Base64 串（二进制资源）。 */
export type SkillResourceEncoding = 'text' | 'base64';

/**
 * Skill 资源文件树节点（对应 BE SkillResourceFileVo）。
 *
 * - `path` 为相对路径，是树内主键（如 `references/guide.md`、`assets/logo.png`）；
 * - 文本文件 `encoding=text`，`content` 为原文 UTF-8；
 * - 图片等二进制 `encoding=base64`，`content` 为不含 `data:` 前缀的纯 Base64 串；
 * - `FOLDER` 节点 `encoding`/`mime`/`content` 均为空。
 */
export interface SkillResourceFileVO {
  path: string;
  type: SkillResourceFileType;
  /** 末段文件名（FE 可由 path 推导，BE 透传冗余存储） */
  name: string;
  /** 父节点 path；根节点为 null */
  parentPath?: string | null;
  encoding?: SkillResourceEncoding | null;
  mime?: string | null;
  /** 文本原文 或 Base64 串；FOLDER 为空 */
  content?: string | null;
}

/** 入参侧资源节点（与 VO 同构，FE 组装后回传 BE）。 */
export type SkillResourceFileParam = SkillResourceFileVO;

/**
 * 资源树查询 / zip 解析预览出参（对应 BE SkillResourceTreeVo）。
 * <p>一次返回整棵扁平文件列表（含内容，图片 Base64 随树），由 FE 自行组装成树。
 */
export interface SkillResourceTreeVO {
  skillNum?: string;
  version?: string | null;
  files: SkillResourceFileVO[];
}

/** zip 解析预览入参（POST，body 携带 Base64）。 */
export interface SkillZipParseParam {
  zipBase64: string;
}

// ============================================================
// 2026-06-10 新增：发布检测（三检）+ 检测记录
// 对齐技术方案 §4.3 SkillCheckRecord + §6.5 / §6.6 PRD
// ============================================================

/** 整体检测结果。 */
export type SkillCheckResult = 'PASS' | 'FAIL';

/** 单项检测结果（含未执行的 SKIPPED）。 */
export type SkillCheckItemResult = 'PASS' | 'FAIL' | 'SKIPPED';

/** 检测项标识。 */
export type SkillCheckItem = 'SIZE' | 'FORMAT' | 'AVAILABILITY';

/** 单条检测错误明细。 */
export interface SkillCheckError {
  /** 所属检测项 */
  checkItem: SkillCheckItem;
  /** 出错位置（文件相对路径） */
  location?: string | null;
  /** 错误原因 */
  message: string;
}

/**
 * 发布检测结果（对应 BE SkillPublishResultVo）。
 * <p>检测不通过时 Result.code=3006，data 仍为本结构（含逐项子结果 + errors）。
 */
export interface SkillPublishResultVO {
  result: SkillCheckResult;
  sizeResult: SkillCheckItemResult;
  formatResult: SkillCheckItemResult;
  availabilityResult: SkillCheckItemResult;
  errors?: SkillCheckError[];
  /** 检测记录编号（落库后回填，便于跳详情） */
  recordNum?: string | null;
  /** 总耗时毫秒 */
  costMs?: number;
}

/** 检测记录（对应 BE SkillCheckRecordVo）。 */
export interface SkillCheckRecordVO {
  num: string;
  skillNum: string;
  version: string;
  result: SkillCheckResult;
  sizeResult: SkillCheckItemResult;
  formatResult: SkillCheckItemResult;
  availabilityResult: SkillCheckItemResult;
  errors?: SkillCheckError[];
  /** 总耗时毫秒 */
  costMs?: number;
  /** 触发人（create_no） */
  createNo?: string;
  createTime?: string;
}

/** 检测记录分页查询入参。 */
export interface SkillCheckRecordPageQuery {
  skillNum: string;
  pageNo: number;
  pageSize: number;
}

/** Skill 列表 / 详情 公共出参（对应 BE SkillVo）。 */
export interface SkillVO {
  num: string;
  name: string;
  description?: string;
  tags?: string[];
  /**
   * @deprecated 2026-06-10 起对象存储下线，资源改由 {@link SkillResourceTreeVO} 承载。
   * 列表/详情不再返回该字段；保留仅为兼容旧公司库存量数据读取。
   */
  skillFileKey?: string;
  source: SkillSource;
  ownerUserId?: string;
  status: SkillStatus;
  /** 当前在线版本号；DRAFT 状态可为空 */
  currentVersionNum?: string;
  createTime?: string;
  updateTime?: string;
}

/** Skill 版本基础信息（对应 BE SkillVersionVo）。 */
export interface SkillVersionVO {
  num: string;
  skillNum: string;
  /** v2.2：`versionNum` 重命名为 `version`，字符串语义无校验 */
  version: string;
  /** v2.2：发布时的 Skill 名称快照 */
  name: string;
  description?: string;
  tags?: string[];
  skillFileKey?: string;
  /** v2.8：版本生命周期状态 */
  status: SkillStatus;
  createTime?: string;
}

/** v2.11：详情接口嵌套结构 {skill, currentVersion, reuseCount}。 */
export interface SkillDetailVO {
  skill: SkillVO;
  /** PUBLISHED/DEPRECATED 时有；DRAFT 或 currentVersionNum 为空时为 null */
  currentVersion?: SkillVersionVO | null;
  /** 被多少个 Agent 复用引用；M3 接入后非 null，当前 0 占位 */
  reuseCount?: number;
  /** 当前在线版本 SKILL.md 正文内容（v3.0 hotfix） */
  skillMdContent?: string;
}

export interface SkillVersionDetailVO {
  version: SkillVersionVO;
}

/**
 * 可绑定版本（供 Agent 绑定 Skill 时选择；后端仅返回已发布版本）。
 * 对齐后端 SkillBindableVersionVO（GET /api/v1/skill/query/bindable-versions）。
 */
export interface SkillBindableVersionVO {
  /** 发布版本号 */
  versionNum: string;
  /** 发布时间 */
  publishedTime?: string;
  /** 是否为当前最新在线版本 */
  latest: boolean;
}

/** 新建 Skill 创建方式：上传 zip / 在线直接创建。 */
export type SkillCreateMode = 'UPLOAD' | 'DIRECT';

/**
 * 创建 Skill 入参（2026-06-10 双模式改造）。
 *
 * <p>对齐技术方案 §7.2.1：单一 `POST /skill/create` 接口，按 `mode` 分支：
 * - `mode=UPLOAD`：携带 {@link #zipBase64}（原始 zip 的 Base64），BE 解压入库；
 * - `mode=DIRECT`：携带 {@link #resourceFiles}（前端组装的整棵资源树）。
 *
 * <p>创建只落 DRAFT 草稿，不再首发；发布另由 {@link SkillPublishParam} 触发检测。
 */
export interface SkillCreateParam {
  mode: SkillCreateMode;
  name: string;
  description: string;
  /** Semver 版本号 `x.y.z`，首建默认 `1.0.0` */
  version: string;
  tags?: string[];
  /** mode=UPLOAD：原始 zip 的 Base64（不含 data: 前缀） */
  zipBase64?: string;
  /** mode=DIRECT：整棵资源文件树（含 SKILL.md 根节点） */
  resourceFiles?: SkillResourceFileParam[];
}

/**
 * 更新草稿 Skill 入参（2026-06-10：支持回写资源树）。
 * <p>仅 SELF 来源可写；COMPANY 来源由 BE 抛 BizException(1003)。
 * <p>CHECK_FAILED / PUBLISHED 状态更新后 BE 自动回落 DRAFT。
 */
export interface SkillUpdateParam {
  /** 目标 Skill 业务编号 */
  num: string;
  name?: string;
  description?: string;
  tags?: string[];
  /** 整棵资源文件树；传入即整体覆盖草稿树 */
  resourceFiles?: SkillResourceFileParam[];
}

/**
 * 发布 Skill 入参（2026-06-10：触发同步三检）。
 * <p>BE 在事务内顺序执行大小/格式/可用性三检：通过 → 建版本快照 + PUBLISHED；
 * 不通过 → CHECK_FAILED，返回 {@link SkillPublishResultVO}（code=3006）。
 */
export interface SkillPublishParam {
  skillNum: string;
  version: string;
}

/** v2.11：回滚到指定历史版本入参。 */
export interface SkillRollbackParam {
  skillNum: string;
  targetVersion: string;
}

/**
 * v2.11：版本对比 — 仅字段级 diff。
 * <p>BE v2.10 SkillFileStorage 不实现，故无 mdDiff；接入后再补。
 */
export interface SkillVersionDiffVO {
  versionA: string;
  versionB: string;
  /** name 字段 diff；null 表示一致；非空时格式 "{vA.name} → {vB.name}" */
  nameDiff?: string | null;
  /** description 字段 diff；同上 */
  descriptionDiff?: string | null;
  /** tags 集合差集 */
  tagsDiff?: TagsDiff;
}

/** 标签集合差集（v2.11 新结构）。 */
export interface TagsDiff {
  onlyInA: string[];
  onlyInB: string[];
  common: string[];
}

export interface SkillPageQuery
  extends Partial<{
    source: SkillSource;
    status: SkillStatus;
    keyword: string;
    ownerUserId: string;
  }> {
  pageNo: number;
  pageSize: number;
}
