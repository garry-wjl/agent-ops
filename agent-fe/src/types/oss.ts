/**
 * Common 横切类型 — 对齐 BE `CommonController` 实际返回 schema。
 *
 * 上传协议(预签名 URL 模式):
 *   1) FE → POST /api/v1/common/oss/sts-init  → OssStsCredentialVO { url, method, signedHeaders, ... }
 *   2) FE 直接 fetch(url, { method, headers: signedHeaders, body: file })
 *   3) FE 持久化 fileId(从 URL path 提取)到业务侧(例如 Skill.fileId)
 *
 * 注:Java 客户端 `OssStsCredentialVO.java` 仍是旧 STS 三元组形态,实际 BE
 * `/api/v1/common/oss/sts-init` 返回的是简化的预签名 URL,这里以 BE 实际响应为准。
 */

/** STS 凭证申请入参 */
export interface OssStsInitParam {
  /** 含路径的目标文件名,如 `skills/{xxx}/SKILL.zip` */
  fileName: string;
  /** MIME；聊天附件上传时与 sizeBytes 一起传，触发 chat_attachment 登记 */
  mimeType?: string;
  /** 文件字节大小；聊天附件登记用 */
  sizeBytes?: number;
  /** 关联 Agent；聊天附件可传 */
  agentNum?: string;
  /** 资源桶名;可空,留空走后端默认桶 */
  bucketName?: string;
  /** 文件 md5;内网上传时 Terra 会校验内容一致性 */
  fileMd5?: string;
  /** 是否允许覆盖同名文件;默认 false */
  overwrite?: boolean;
  /** 接入端点;默认 PUBLIC */
  endpointType?: 'PUBLIC' | 'INTERNAL' | 'PROXY';
}

/**
 * STS 凭证返回(预签名 URL 模式)。
 * <p>URL path 形如 `/{bucket}/{fileId}/{fileName}`,其中 fileId 通常为 `file{hex32}`。
 */
export interface OssStsCredentialVO {
  /** 业务侧持久化的文件标识;直接作为 skillFileKey 等字段回传给 BE(无需 FE 再解析 URL) */
  fileId: string;
  /** 预签名 PUT URL,含完整 query 签名;FE 直接 fetch 上传 */
  url: string;
  /** HTTP 方法(典型为 PUT) */
  method: string;
  /** URL 过期时间(ISO 8601) */
  expiration: string;
  /** 需要附带在请求中的 signed headers;空 object 表示无 */
  signedHeaders: Record<string, string>;
}

/** 文件访问 URL 申请入参 */
export interface OssFileUrlParam {
  fileId: string;
  bucketName?: string;
  /** URL 过期时间,毫秒;最大 7 天(604800000);私有桶必填 */
  urlExpire?: number;
  /** OSS 处理串,如 `image/resize,w_100/quality,q_80` */
  ossProcess?: string;
  /** WPS 预览水印 JSON;内网/混合云支持 WPS 预览时必填 */
  watermarkJson?: string;
}

/** 文件访问 URL 返回 — 与 BE `OssFileAccessUrlVO` 对齐(顶层 url 字段) */
export interface OssFileAccessUrlVO {
  /** 源文件下载 / 直链 */
  url?: string;
}

/** 文件 / 目录删除入参 */
export interface OssFileDeleteParam {
  /** Terra 文件 ID 或路径(按 isPath 判断) */
  dirOrFileId: string;
  bucketName?: string;
  /** true 按路径删,默认 false 按 ID 删 */
  isPath?: boolean;
}
