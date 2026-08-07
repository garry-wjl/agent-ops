import type { S3Client } from '@aws-sdk/client-s3';
import axios from 'axios';
import oss from 'ali-oss';
import { copyS3Object, putS3Json } from './s3';

export const VERSION_MAX_LENGTH = 10;

export interface VersionEntry {
  version: string;
  date: number;
  commit: string;
}

export interface VersionJson {
  current: string;
  versions: VersionEntry[];
}

/**
 * 将新版本插入 version.json，去重并按 VERSION_MAX_LENGTH 裁剪。
 * versions 列表保持最新在前排序。
 */
export function updateVersionJson(
  existing: VersionJson | null,
  newEntry: VersionEntry
): VersionJson {
  if (!existing) {
    return { current: newEntry.version, versions: [newEntry] };
  }
  const deduped = existing.versions.filter(v => v.version !== newEntry.version);
  const reversed = deduped.reverse();
  const toKeep =
    reversed.length >= VERSION_MAX_LENGTH ? reversed.slice(0, VERSION_MAX_LENGTH - 1) : reversed;
  const versions = [newEntry, ...toKeep];
  return { current: newEntry.version, versions };
}

/**
 * 在 versions 列表中找到当前版本相邻的版本。
 * prev = 更旧（index 更大），next = 更新（index 更小）。
 * 越界或 current 不存在时返回 null。
 */
export function getAdjacentVersion(data: VersionJson, direction: 'prev' | 'next'): string | null {
  const currentIndex = data.versions.findIndex(v => v.version === data.current);
  if (currentIndex === -1) return null;
  const newIndex = direction === 'prev' ? currentIndex + 1 : currentIndex - 1;
  if (newIndex < 0 || newIndex >= data.versions.length) return null;
  return data.versions[newIndex].version;
}

/** 从 URL 读取 version.json，不存在时返回 null */
export async function fetchVersionJson(url: string): Promise<VersionJson | null> {
  try {
    const { data } = await axios.get<VersionJson>(url);
    return data;
  } catch {
    return null;
  }
}

/** 将 version.json 写入 OSS */
export async function writeVersionJsonToOss(
  store: ReturnType<typeof oss>,
  uploadPath: string,
  data: VersionJson
): Promise<void> {
  await store.put(`${uploadPath}/version.json`, Buffer.from(JSON.stringify(data), 'utf-8'), {
    mime: 'application/json',
  });
}

/** 将目标版本的 index.html copy 到 OSS 根路径（原子激活） */
export async function activateVersionOss(
  store: ReturnType<typeof oss>,
  uploadPath: string,
  targetVersion: string
): Promise<void> {
  await store.copy(`${uploadPath}/index.html`, `${uploadPath}/${targetVersion}/index.html`);
}

/** 将目标版本的 index.html copy 到 S3 根路径（原子激活） */
export async function activateVersionS3(
  client: S3Client,
  bucket: string,
  targetVersion: string
): Promise<void> {
  await copyS3Object(client, bucket, `${targetVersion}/index.html`, 'index.html');
}

/** 将 version.json 写入 S3 */
export async function writeVersionJsonToS3(
  client: S3Client,
  bucket: string,
  data: VersionJson
): Promise<void> {
  await putS3Json(client, bucket, 'version.json', data);
}
