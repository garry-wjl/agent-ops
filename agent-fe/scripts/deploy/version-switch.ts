/* eslint-disable no-console */
import oss from 'ali-oss';
import { deployConfig } from '../deployConfig';
import { createS3Client } from './s3';
import { getAdjacentVersion } from './util';
import {
  fetchVersionJson,
  writeVersionJsonToOss,
  activateVersionOss,
  activateVersionS3,
  writeVersionJsonToS3,
} from './util';
import type { VersionJson } from './util';

const direction = process.argv[2] as 'prev' | 'next';
const isSWS = process.env.DEPLOY_MODE === 'sws-prod';

(async () => {
  if (direction !== 'prev' && direction !== 'next') {
    console.error('Usage: pnpm run switch prev|next');
    process.exit(1);
  }

  try {
    const {
      OSS_CONFIG: { OSS_KEY, OSS_SECRET, OSS_BUCKET, OSS_REGION, OSS_UPLOAD_PATH, OSS_CDN_DOMAIN },
      S3_CONFIG: { S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET, S3_ENDPOINT },
    } = deployConfig || { OSS_CONFIG: {}, S3_CONFIG: {} };

    console.log('========== 读取 version.json 开始 ==========');
    let versionData: VersionJson | null;

    if (isSWS) {
      versionData = await fetchVersionJson(`https://${S3_ENDPOINT}/${S3_BUCKET}/version.json`);
    } else {
      versionData = await fetchVersionJson(`${OSS_CDN_DOMAIN}/${OSS_UPLOAD_PATH}/version.json`);
    }

    if (!versionData) {
      console.error('读取 version.json 失败');
      process.exit(1);
    }

    console.log('当前版本:', versionData.current);
    console.log('========== 读取 version.json 结束 ==========');

    const targetVersion = getAdjacentVersion(versionData, direction);
    if (!targetVersion) {
      console.error(
        `当前已是${direction === 'prev' ? '最旧' : '最新'}版本，无法继续${direction === 'prev' ? '回退' : '前进'}`
      );
      process.exit(1);
    }

    console.log(`========== 切换版本 ${versionData.current} → ${targetVersion} 开始 ==========`);

    versionData.current = targetVersion;

    if (!isSWS) {
      const store = new oss({
        accessKeyId: OSS_KEY,
        accessKeySecret: OSS_SECRET,
        bucket: OSS_BUCKET,
        region: OSS_REGION,
        secure: true,
      });

      await writeVersionJsonToOss(store, OSS_UPLOAD_PATH, versionData);
      await activateVersionOss(store, OSS_UPLOAD_PATH, targetVersion);

      console.log(`OSS 版本已切换至 ${targetVersion}`);
    }

    if (isSWS) {
      const s3 = createS3Client({
        accessKey: S3_ACCESS_KEY,
        secretKey: S3_SECRET_KEY,
        endpoint: S3_ENDPOINT,
        bucket: S3_BUCKET,
      });

      console.log('========== S3版本切换开始 ==========');

      await activateVersionS3(s3, S3_BUCKET, targetVersion);
      await writeVersionJsonToS3(s3, S3_BUCKET, versionData);

      console.log(`S3 版本已切换至 ${targetVersion}`);
      console.log('========== S3版本切换结束 ==========');
    }

    console.log('========== 切换版本结束 ==========');
  } catch (error) {
    console.error('error:', error);
    process.exit(1);
  }
})();
