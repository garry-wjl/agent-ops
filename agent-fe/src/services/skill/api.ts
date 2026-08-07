/**
 * Skill 服务 — 对应 BE Skill 管理 技术方案 v2.11 + 2026-06-10 优化（双模式创建 + 文件入库 + 发布检测）
 *
 * 路径约定（adapter 层 command / query 两组）：
 * - 写：/api/v1/skill/command/{create|update|discardDraft|publish|rollback|unpublish|delete}
 * - 读：POST /api/v1/skill/query/list / versionCompare / parseZip
 *       GET  /api/v1/skill/query/detail / versionList / versionDetail
 *            /resourceTree / checkRecord/page / checkRecord/detail
 *
 * v3.0：版本生命周期收敛为「编辑草稿（update）→ 发布（publish 过三检 → 生成不可变版本）」+ 回滚；
 * 删除 v2.12 独立草稿版本端点（createVersion/updateVersion/activateVersion/deleteVersion）。
 *
 * 2026-06-10 优化新增 / 改造：
 * - create：双模式入参（mode=UPLOAD 带 zipBase64 / mode=DIRECT 带 resourceFiles），仅落 DRAFT
 * - update：支持回写整棵 resourceFiles
 * - publish：触发同步三检；PASS 返回 SkillPublishResultVO，FAIL 由拦截器抛 BizError(3006)（data 携带结果）
 * - parseZip：zip 解析预览（不落库）
 * - resourceTree：按 skillNum+version 返回整棵资源树（含内容）
 * - checkRecordPage / checkRecordDetail：检测记录列表 / 详情
 * - skillFileKey 链路废弃（对象存储下线）
 */
import { get, post } from '../request';
import type {
  PageVO,
  Result,
  SkillCheckRecordPageQuery,
  SkillCheckRecordVO,
  SkillCreateParam,
  SkillDetailVO,
  SkillPageQuery,
  SkillPublishParam,
  SkillPublishResultVO,
  SkillResourceTreeVO,
  SkillRollbackParam,
  SkillUpdateParam,
  SkillVO,
  SkillBindableVersionVO,
  SkillVersionDetailVO,
  SkillVersionDiffVO,
  SkillVersionVO,
  SkillZipParseParam,
} from '@/types';
import { request } from '../request';

/** 带 query 参数的 POST 包一层 Result<T>。 */
async function postWithParams<T>(
  url: string,
  params: Record<string, unknown>,
): Promise<T> {
  const res = await request.post<Result<T>>(url, undefined, { params });
  return res.data.data;
}

/** 带 query 参数的 GET 直接返回 data（与 get helper 行为一致，复用便于显式表达）。 */
async function getWithParams<T>(
  url: string,
  params: Record<string, unknown>,
): Promise<T> {
  return get<T>(url, params);
}

export const skillApi = {
  // ============================================================
  // 写：/api/v1/skill/command/*
  // ============================================================

  /**
   * 2026-06-10：双模式创建（mode=UPLOAD/DIRECT），仅落 DRAFT 草稿，不再首发。
   * <p>资源全部入库（zip 解压 或 resourceFiles 树），对象存储已下线。
   */
  create: (param: SkillCreateParam) =>
    post<SkillVO>('/api/v1/skill/command/create', param),

  /** 2026-06-10：更新草稿 Skill 字段 + 整棵 resourceFiles；BE 自动置 status=DRAFT。 */
  update: (param: SkillUpdateParam) =>
    post<void>('/api/v1/skill/command/update', param),

  /** v2.11 新增：放弃草稿态修改，回滚为 currentVersion 字段并置 PUBLISHED。 */
  discardDraft: (num: string) =>
    postWithParams<void>('/api/v1/skill/command/discardDraft', { num }),

  /**
   * 2026-06-10：发布（触发同步三检，先检测后建版本）。
   * <p>PASS → 返回 {@link SkillPublishResultVO}（全 PASS）；
   * FAIL → BE 返回 code=3006，拦截器抛 BizError（其 data 即 SkillPublishResultVO，含 errors）。
   * 调用方应 catch BizError、读 err.data 渲染检测错误清单（见 Skill 发布检测态）。
   */
  publish: (param: SkillPublishParam) =>
    post<SkillPublishResultVO>('/api/v1/skill/command/publish', param),

  /** 2026-06-10：zip 解析预览（只解析不落库，POST 因携带大体积 Base64 body）。 */
  parseZip: (param: SkillZipParseParam) =>
    post<SkillResourceTreeVO>('/api/v1/skill/query/parseZip', param),

  /** v2.11：回滚到指定历史版本，仅切 currentVersionNum + status=PUBLISHED。 */
  rollback: (skillNum: string, targetVersion: string) =>
    post<void>('/api/v1/skill/command/rollback', {
      skillNum,
      targetVersion,
    } satisfies SkillRollbackParam),

  /** v2.11：下架（PUBLISHED → DEPRECATED；接替原 deprecate 接口）。 */
  unpublish: (num: string) =>
    postWithParams<void>('/api/v1/skill/command/unpublish', { num }),

  /** v2.11：逻辑删除（仅 status != PUBLISHED）。 */
  delete: (num: string) =>
    postWithParams<void>('/api/v1/skill/command/delete', { num }),

  // ============================================================
  // 读：/api/v1/skill/query/*
  // ============================================================

  pageList: (query: SkillPageQuery) =>
    post<PageVO<SkillVO>>('/api/v1/skill/query/list', query),

  detail: (num: string) =>
    get<SkillDetailVO>('/api/v1/skill/query/detail', { num }),

  /** v2.11：版本历史（路径从 /version/list 迁到 /query/versionList）。 */
  versionList: (skillNum: string) =>
    get<SkillVersionVO[]>('/api/v1/skill/query/versionList', { skillNum }),

  /** v2.11：单版本详情（路径从 /version/detail 迁到 /query/versionDetail）。 */
  versionDetail: (skillNum: string, version: string) =>
    get<SkillVersionDetailVO>('/api/v1/skill/query/versionDetail', {
      skillNum,
      version,
    }),

  /**
   * 2026-07-28：可绑定版本列表（供 Agent 绑定 Skill 时选择，后端仅返回已发布版本，latest 标最新在线版）。
   */
  bindableVersions: (skillNum: string) =>
    get<SkillBindableVersionVO[]>('/api/v1/skill/query/bindable-versions', {
      skillNum,
    }),

  /**
   * 2026-06-10：资源文件树查询。
   * <p>version 为空 → 取草稿（owner=SKILL）树；非空 → 取该版本快照（owner=VERSION）树。
   * 一次返回整棵扁平文件列表（含内容，图片 Base64 随树）。
   */
  resourceTree: (num: string, version?: string) =>
    get<SkillResourceTreeVO>('/api/v1/skill/query/resourceTree', {
      num,
      version,
    }),

  /** 2026-06-10：检测记录分页列表（按 skillNum，create_time 倒序）。 */
  checkRecordPage: (query: SkillCheckRecordPageQuery) =>
    get<PageVO<SkillCheckRecordVO>>('/api/v1/skill/query/checkRecord/page', {
      ...query,
    }),

  /** 2026-06-10：单条检测记录详情（三项子结果 + errors）。 */
  checkRecordDetail: (recordNum: string) =>
    get<SkillCheckRecordVO>('/api/v1/skill/query/checkRecord/detail', {
      recordNum,
    }),

  /**
   * v2.11：版本对比 —— v2.10 仅字段级 diff（无 SKILL.md 行级 diff，待 SkillFileStorage 接入）。
   * 入参从 body 改为 query（BE 改了签名）。
   */
  compareVersions: (skillNum: string, versionA: string, versionB: string) =>
    getWithParams<SkillVersionDiffVO>('/api/v1/skill/query/versionCompare', {
      skillNum,
      versionA,
      versionB,
    }),

  // ============================================================
  // TODO：SKILL.md 下载（BE v2.10 SkillFileStorage 暂不实现）
  // ============================================================

  /**
   * TODO（BE v2.10 SkillFileStorage 暂不实现）：构造下载 SKILL.md 的 URL。
   * BE 接上 `/api/v1/skill/query/skillFile` 之前，调用方应隐藏下载入口。
   * 临时实现返回空字符串，避免运行时 404；UI 应通过 disabled / 隐藏按钮防止用户点击。
   */
  skillFileUrl: (_skillNum: string, _version: string): string => {
    // 暂保留 helper 签名，等 BE 上线后改回：
    //   return `/api/v1/skill/query/skillFile?skillNum=${encodeURIComponent(_skillNum)}&version=${encodeURIComponent(_version)}`;
    return '';
  },

  // ============================================================
  // v2.5 起公司库同步暂不实现 —— syncFromCompany 接口下线
  // ============================================================
  // syncFromCompany: () => post<SyncResultVO>('/api/v1/skill/sync/runOnce', {}),
  //   ↑ BE v2.5 起整体下线，FE 同步移除调用；UI 上"同步公司库"按钮已隐藏。
};

// 防止 `request` 误判未使用（postWithParams 内部引用）
void request;
