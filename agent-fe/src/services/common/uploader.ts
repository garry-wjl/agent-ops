/**
 * OSS 上传封装 — 与 BE `CommonController` 预签名 URL 协议对齐。
 *
 * 流程:
 *   1) 调 `commonApi.stsInit` 拿预签名 URL + fileId(BE 直接返回)
 *   2) `fetch(url, { method, headers: signedHeaders, body: file })` 直传
 *   3) 直接返回 BE 给的 fileId,业务侧用它作 skillFileKey 等字段
 */
import { commonApi } from './api';
import type { OssStsInitParam } from '@/types';

/** uploadFile 选项 — 在 sts-init 入参基础上加 file 字段 */
export interface UploadFileOptions extends Omit<OssStsInitParam, 'fileName'> {
  /** 待上传的文件 */
  file: File;
  /** 含路径的目标文件名;不传则用 file.name */
  fileName?: string;
}

/** uploadFile 返回 */
export interface UploadFileResult {
  /** BE 返回的业务文件标识;直接作为 skillFileKey 等字段回传 */
  fileId: string;
  /** 实际发起上传时使用的 fileName */
  fileName: string;
  /** 预签名 URL(便于调用方排查) */
  url: string;
}

/**
 * 上传一个文件:申请预签名 URL → PUT 直传 → 返回 BE 给的 fileId。
 *
 * 异常按 BE 协议向上抛(`/api/v1/common/oss/sts-init` 失败,或上传 HTTP 非 2xx)。
 */
export async function uploadFile(opts: UploadFileOptions): Promise<UploadFileResult> {
  const { file, fileName, ...rest } = opts;
  const targetName = fileName ?? file.name;
  const sts = await commonApi.stsInit({ fileName: targetName, ...rest });

  if (!sts.url || !sts.method) {
    throw new Error('sts-init 返回缺少 url / method');
  }
  if (!sts.fileId) {
    throw new Error('sts-init 返回缺少 fileId');
  }

  // OSS V4 (OSS4-HMAC-SHA256) 签名说明:
  // - 签名嵌在 URL query(x-oss-signature),签名算法只算了 host(BE 返 signedHeaders={} 即可)
  // - 浏览器 fetch 自动添 Content-Type / Content-Length;OSS V4 默认不把它们纳入 signed-headers,
  //   所以可以让 fetch 自由加,不会签名不匹配
  // - body 直接放 File 对象;不要包 multipart / FormData
  if (import.meta.env.DEV) {
    console.debug('[uploader] PUT', {
      url: sts.url,
      method: sts.method,
      fileId: sts.fileId,
      expiration: sts.expiration,
      signedHeaders: sts.signedHeaders,
      fileName: targetName,
      fileSize: file.size,
      fileType: file.type,
    });
  }

  const resp = await fetch(sts.url, {
    method: sts.method,
    headers: sts.signedHeaders ?? {},
    body: file,
  });

  if (!resp.ok) {
    const text = await resp.text().catch(() => '');
    if (import.meta.env.DEV) {
      console.error('[uploader] PUT failed', {
        status: resp.status,
        statusText: resp.statusText,
        respHeaders: Array.from(resp.headers.entries()),
        body: text,
      });
    }
    throw new Error(`OSS 直传失败 HTTP ${resp.status}: ${text || resp.statusText}`);
  }

  if (import.meta.env.DEV) {
    console.debug('[uploader] PUT ok', {
      status: resp.status,
      fileId: sts.fileId,
      etag: resp.headers.get('etag'),
      requestId: resp.headers.get('x-oss-request-id'),
    });
  }

  return {
    fileId: sts.fileId,
    fileName: targetName,
    url: sts.url,
  };
}
