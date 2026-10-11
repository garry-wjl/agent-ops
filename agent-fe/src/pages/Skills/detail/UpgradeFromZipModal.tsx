/**
 * Skill 压缩包升级弹窗：拖入 .zip → 调用 upgradeFromZip（覆盖资源 + 自动版本 + 发布三检）。
 */
import { useState } from 'react';
import { Alert, Modal, Spin, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { SkillApi } from '@/services';
import { BizError } from '@/services/request';
import type { SkillPublishResultVO } from '@/types';
import { readFileAsBase64 } from '@/utils/skillResource';

const { Dragger } = Upload;

interface Props {
  open: boolean;
  skillNum: string;
  currentVersionNum?: string | null;
  onClose: () => void;
  onSuccess: (result: SkillPublishResultVO) => void;
}

export default function UpgradeFromZipModal(props: Props) {
  const { open, skillNum, currentVersionNum, onClose, onSuccess } = props;
  const [uploading, setUploading] = useState(false);
  const [failResult, setFailResult] = useState<SkillPublishResultVO | null>(null);

  const handleZip = async (file: File): Promise<boolean> => {
    if (!file.name.toLowerCase().endsWith('.zip')) {
      message.error('仅支持 .zip 压缩包');
      return false;
    }
    setUploading(true);
    setFailResult(null);
    try {
      const zipBase64 = await readFileAsBase64(file);
      const result = await SkillApi.upgradeFromZip({ skillNum, zipBase64 });
      message.success(`已升级并发布版本 ${result.version ?? ''}`);
      onSuccess(result);
      onClose();
    } catch (err) {
      if (err instanceof BizError && err.code === 3006) {
        const data = err.data as SkillPublishResultVO | undefined;
        setFailResult(data ?? null);
        message.error('发布检测未通过，请根据错误修复压缩包后重试');
      } else if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      setUploading(false);
    }
    return false;
  };

  return (
    <Modal
      title="上传压缩包升级"
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnHidden
      width={560}
    >
      <p style={{ margin: '0 0 12px', color: '#45556C', fontSize: 13 }}>
        拖入 .zip 将整树覆盖当前草稿资源；无需在压缩包中填写 version，版本号由系统自动生成
        {currentVersionNum
          ? `（当前 ${currentVersionNum} → 下一 patch）`
          : '（无在线版本时从 1.0.0 起）'}
        ，并立即触发发布检测。
      </p>
      <Dragger
        accept=".zip"
        maxCount={1}
        multiple={false}
        showUploadList={false}
        disabled={uploading}
        beforeUpload={(file) => {
          void handleZip(file);
          return Upload.LIST_IGNORE;
        }}
      >
        <p className="ant-upload-drag-icon">
          {uploading ? <Spin /> : <InboxOutlined />}
        </p>
        <p className="ant-upload-text">点击或拖拽上传 .zip</p>
        <p className="ant-upload-hint">
          根目录须含 SKILL.md；成功后自动生成新版本号并发布
        </p>
      </Dragger>
      {failResult?.errors && failResult.errors.length > 0 ? (
        <Alert
          style={{ marginTop: 12 }}
          type="error"
          showIcon
          message={`检测失败（拟发版本 ${failResult.version ?? '-'}）`}
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {failResult.errors.map((e, i) => (
                <li key={`${e.location}-${i}`}>
                  [{e.checkItem}] {e.location}: {e.message}
                </li>
              ))}
            </ul>
          }
        />
      ) : null}
    </Modal>
  );
}
