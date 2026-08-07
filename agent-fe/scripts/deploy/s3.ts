/**
 * 圈内 S3 / MinIO 上传工具（基于公开包 @aws-sdk/client-s3）。
 */
import {
  CopyObjectCommand,
  PutObjectCommand,
  S3Client,
  type S3ClientConfig,
} from '@aws-sdk/client-s3';
import fs from 'fs';
import path from 'path';

export interface S3DeployConfig {
  accessKey: string;
  secretKey: string;
  /** 不含协议时自动补 https://；兼容 MinIO / 内网 S3 */
  endpoint: string;
  bucket: string;
  /** 可选对象 key 前缀，如 v123 */
  keyPrefix?: string;
  region?: string;
}

export function createS3Client(config: S3DeployConfig): S3Client {
  const endpoint = config.endpoint.startsWith('http')
    ? config.endpoint
    : `https://${config.endpoint}`;

  const clientConfig: S3ClientConfig = {
    region: config.region || 'us-east-1',
    endpoint,
    forcePathStyle: true,
    credentials: {
      accessKeyId: config.accessKey,
      secretAccessKey: config.secretKey,
    },
  };
  return new S3Client(clientConfig);
}

function listFilesRecursive(dir: string): string[] {
  const out: string[] = [];
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) {
      out.push(...listFilesRecursive(full));
    } else if (!full.endsWith('.map')) {
      out.push(full);
    }
  }
  return out;
}

function guessContentType(filePath: string): string {
  const ext = path.extname(filePath).toLowerCase();
  const map: Record<string, string> = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'application/javascript',
    '.css': 'text/css',
    '.json': 'application/json',
    '.svg': 'image/svg+xml',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.ico': 'image/x-icon',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.ttf': 'font/ttf',
    '.map': 'application/json',
  };
  return map[ext] || 'application/octet-stream';
}

/** 递归上传本地目录到 bucket（可选 key 前缀） */
export async function uploadDirToS3(
  client: S3Client,
  bucket: string,
  localDir: string,
  keyPrefix = ''
): Promise<void> {
  const root = path.resolve(localDir);
  const files = listFilesRecursive(root);
  const prefix = keyPrefix ? keyPrefix.replace(/\/+$/, '') + '/' : '';

  for (const file of files) {
    const relative = path.relative(root, file).split(path.sep).join('/');
    const key = `${prefix}${relative}`;
    const body = fs.readFileSync(file);
    await client.send(
      new PutObjectCommand({
        Bucket: bucket,
        Key: key,
        Body: body,
        ContentType: guessContentType(file),
      })
    );
    console.log(`s3://${bucket}/${key} upload success`);
  }
}

/** 写入 JSON 对象 */
export async function putS3Json(
  client: S3Client,
  bucket: string,
  key: string,
  data: unknown
): Promise<void> {
  const body = Buffer.from(JSON.stringify(data), 'utf-8');
  await client.send(
    new PutObjectCommand({
      Bucket: bucket,
      Key: key,
      Body: body,
      ContentType: 'application/json',
    })
  );
}

/** 桶内对象拷贝（用于激活版本：vX/index.html → index.html） */
export async function copyS3Object(
  client: S3Client,
  bucket: string,
  sourceKey: string,
  destKey: string
): Promise<void> {
  await client.send(
    new CopyObjectCommand({
      Bucket: bucket,
      CopySource: `${bucket}/${sourceKey}`,
      Key: destKey,
    })
  );
}
