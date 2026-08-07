/**
 * Common 横切服务 — 与 BE `CommonController` 一一对齐。
 *
 * BE 路径前缀:`/api/v1/common`,详见
 * `rd-agent-be-adapter/.../adapter/common/CommonController.java`。
 *
 * 业务上层不要直接调本模块;走 {@link ../uploader} 的 uploadFile 封装。
 */
import { get, post } from '../request';
import type {
  EmployeeProfileVO,
  OssFileAccessUrlVO,
  OssFileDeleteParam,
  OssFileUrlParam,
  OssStsCredentialVO,
  OssStsInitParam,
} from '@/types';

export const commonApi = {
  /** 申请上传凭证 — 前端按 cloudName/uploadType 选 SDK / 内网 token 直传 */
  stsInit: (param: OssStsInitParam) =>
    post<OssStsCredentialVO>('/api/v1/common/oss/sts-init', param),

  /** 取文件访问 URL(源文件 + 预览) */
  fileUrl: (param: OssFileUrlParam) =>
    post<OssFileAccessUrlVO>('/api/v1/common/oss/file-url', param),

  /** 删除文件 / 目录(按 fileId 或路径) */
  fileDelete: (param: OssFileDeleteParam) =>
    post<void>('/api/v1/common/oss/file-delete', param),

  /**
   * 通用员工搜索（工号 / 姓名）—— 工作空间成员管理复用。
   * 对应 BE CommonController：GET /api/v1/common/employee/search。
   */
  searchEmployees: (keyword: string, limit = 20) =>
    get<EmployeeProfileVO[]>('/api/v1/common/employee/search', {
      keyword,
      limit,
    }),
};
