/* eslint-disable no-console */
import oss from 'ali-oss';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { deployConfig } from '../deployConfig';
import { createS3Client, uploadDirToS3 } from './s3';
import {
  updateVersionJson,
  fetchVersionJson,
  writeVersionJsonToOss,
  activateVersionOss,
  activateVersionS3,
  writeVersionJsonToS3,
} from './util';
import type { VersionEntry } from './util';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const getBuildDir = (subPath?: string) => path.resolve(__dirname, `../../dist/${subPath || ''}`);

const deployMode = process.env.DEPLOY_MODE || 'dev';
const isProd = deployMode === 'prod' || deployMode === 'sws-prod';
const isSWS = deployMode === 'sws-stag' || deployMode === 'sws-prod';

(async () => {
  try {
    const {
      OSS_CONFIG: { OSS_KEY, OSS_SECRET, OSS_BUCKET, OSS_REGION, OSS_UPLOAD_PATH, OSS_CDN_DOMAIN },
      S3_CONFIG: { S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET, S3_ENDPOINT },
    } = deployConfig || { OSS_CONFIG: {}, S3_CONFIG: {} };

    const hasOSSConfig = OSS_KEY && OSS_SECRET && OSS_BUCKET && OSS_REGION && OSS_UPLOAD_PATH;
    const hasSWSConfig = S3_ACCESS_KEY && S3_SECRET_KEY && S3_BUCKET && S3_ENDPOINT;

    if (!hasOSSConfig && !hasSWSConfig) {
      console.error('错误：OSS 和 S3 配置均不存在或为空，部署中止');
      process.exit(1);
    }

    // ========== 圈外 OSS 部署 ==========
    if (!isSWS && hasOSSConfig) {
      const store = new oss({
        accessKeyId: OSS_KEY,
        accessKeySecret: OSS_SECRET,
        bucket: OSS_BUCKET,
        region: OSS_REGION,
        secure: true,
      });

      const ossUploadFiles = async (src: string, dist: string): Promise<void> => {
        const files = fs.readdirSync(src);
        const promises: Promise<void>[] = [];

        for (const file of files) {
          const _src = `${src}/${file}`;
          const _dist = `${dist}/${file}`;
          const stat = fs.statSync(_src);

          if (stat.isFile() && !_src.endsWith('.map')) {
            promises.push(
              store.put(_dist, _src, {}).then((res: { name: string }) => {
                console.log(`${OSS_CDN_DOMAIN}/${res.name} upload success`);
              })
            );
          }
          if (stat.isDirectory()) {
            await ossUploadFiles(_src, _dist);
          }
        }

        await Promise.all(promises);
      };

      if (isProd) {
        const pipelineId = process.env.CI_PIPELINE_ID!;
        const commitSha = process.env.CI_COMMIT_SHORT_SHA!;
        const versionedPath = `${OSS_UPLOAD_PATH}/v${pipelineId}`;

        console.log('========== 上传static开始 ==========');
        await ossUploadFiles(getBuildDir(), versionedPath);
        console.log('========== 上传static结束 ==========');

        console.log('========== 更新 version.json 开始 ==========');
        const existingVersionData = await fetchVersionJson(
          `${OSS_CDN_DOMAIN}/${OSS_UPLOAD_PATH}/version.json`
        );
        if (!existingVersionData) {
          console.log('version.json 不存在，初始化');
        }

        const newEntry: VersionEntry = {
          version: `v${pipelineId}`,
          date: Date.now(),
          commit: commitSha,
        };
        const newVersionData = updateVersionJson(existingVersionData, newEntry);

        await writeVersionJsonToOss(store, OSS_UPLOAD_PATH, newVersionData);
        console.log('version.json:', JSON.stringify(newVersionData));
        console.log('========== 更新 version.json 结束 ==========');

        console.log('========== 激活版本开始 ==========');
        await activateVersionOss(store, OSS_UPLOAD_PATH, `v${pipelineId}`);
        console.log(`版本 v${pipelineId} 激活成功`);
        console.log('========== 激活版本结束 ==========');
      } else {
        console.log('========== 上传static开始 ==========');
        await ossUploadFiles(getBuildDir(), OSS_UPLOAD_PATH);
        console.log('========== 上传static结束 ==========');
      }
    } else if (!isSWS && !hasOSSConfig) {
      console.warn('警告：OSS 配置缺失或为空，跳过 OSS 部署');
    }

    // ========== 圈内 S3 / MinIO 部署（@aws-sdk/client-s3）==========
    if (isSWS && hasSWSConfig) {
      const pipelineId = process.env.CI_PIPELINE_ID!;
      const commitSha = process.env.CI_COMMIT_SHORT_SHA!;
      const keyPrefix = isProd ? `v${pipelineId}` : '';

      const s3 = createS3Client({
        accessKey: S3_ACCESS_KEY,
        secretKey: S3_SECRET_KEY,
        endpoint: S3_ENDPOINT,
        bucket: S3_BUCKET,
        keyPrefix,
      });

      if (isProd) {
        console.log('========== 上传S3开始 ==========');
        await uploadDirToS3(s3, S3_BUCKET, getBuildDir(), keyPrefix);
        console.log('========== 上传S3结束 ==========');

        console.log('========== 更新 version.json 开始 ==========');
        const existingVersionData = await fetchVersionJson(
          `https://${S3_ENDPOINT}/${S3_BUCKET}/version.json`
        );
        if (!existingVersionData) {
          console.log('version.json 不存在，初始化');
        }

        const newEntry: VersionEntry = {
          version: `v${pipelineId}`,
          date: Date.now(),
          commit: commitSha,
        };
        const newVersionData = updateVersionJson(existingVersionData, newEntry);
        console.log('version.json:', JSON.stringify(newVersionData));
        console.log('========== 更新 version.json 结束 ==========');

        console.log('========== 激活版本开始 ==========');
        await activateVersionS3(s3, S3_BUCKET, `v${pipelineId}`);
        await writeVersionJsonToS3(s3, S3_BUCKET, newVersionData);
        console.log(`版本 v${pipelineId} 激活成功`);
        console.log('========== 激活版本结束 ==========');
      } else {
        console.log('========== 上传S3开始 ==========');
        await uploadDirToS3(s3, S3_BUCKET, getBuildDir(), keyPrefix);
        console.log('========== 上传S3结束 ==========');
      }
    } else if (isSWS && !hasSWSConfig) {
      console.warn('警告：S3 配置缺失或为空，跳过 S3 部署');
    }
  } catch (error) {
    console.log('error: ', error);
    process.exit(1);
  }
})();
