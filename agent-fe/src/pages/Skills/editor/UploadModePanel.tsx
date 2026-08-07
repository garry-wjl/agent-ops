/**
 * 上传模式面板（新建 Skill · 上传模式）— PRD §6.1 / §7.1。
 *
 * 拖入 / 点击上传单个 .zip → 转 Base64 → 调 parseZip 解析预览资源树（只解析不落库）→
 * 只读展示解析出的文件树。真正入库在外层点「保存草稿 / 发布」时随 create(mode=UPLOAD) 提交。
 */
import { useState } from 'react';
import { Empty, Spin, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { SkillApi } from '@/services';
import type { SkillResourceFileVO } from '@/types';
import {
  buildResourceTree,
  formatBytes,
  hasRootSkillMd,
  readFileAsBase64,
  totalDecodedBytes,
} from '@/utils/skillResource';
import { COLOR } from './constants';

const { Dragger } = Upload;

interface Props {
  files: SkillResourceFileVO[] | null;
  onParsed: (files: SkillResourceFileVO[], zipBase64: string) => void;
  onClear: () => void;
}

export default function UploadModePanel(props: Props) {
  const { files } = props;
  const [parsing, setParsing] = useState(false);

  const handleZip = async (file: File): Promise<boolean> => {
    if (!file.name.toLowerCase().endsWith('.zip')) {
      message.error('仅支持 .zip 压缩包');
      return false;
    }
    setParsing(true);
    try {
      const b64 = await readFileAsBase64(file);
      const tree = await SkillApi.parseZip({ zipBase64: b64 });
      const list = tree.files ?? [];
      if (!hasRootSkillMd(list)) {
        message.error('压缩包根目录缺少 SKILL.md');
        return false;
      }
      props.onParsed(list, b64);
      message.success('解析成功');
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    } finally {
      setParsing(false);
    }
    return false;
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Dragger
        accept=".zip"
        maxCount={1}
        multiple={false}
        showUploadList={false}
        disabled={parsing}
        beforeUpload={(file) => {
          void handleZip(file);
          return Upload.LIST_IGNORE;
        }}
      >
        <p className="ant-upload-drag-icon">
          {parsing ? <Spin /> : <InboxOutlined />}
        </p>
        <p className="ant-upload-text">点击或拖拽上传 .zip 压缩包</p>
        <p className="ant-upload-hint">
          仅支持 .zip，解压后根目录需含 SKILL.md；解析后预览文件树，发布时随表单入库
        </p>
      </Dragger>

      {files && files.length > 0 ? (
        <div
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            padding: 12,
          }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 8,
            }}
          >
            <span style={{ fontSize: 12, fontWeight: 700, color: COLOR.textMuted }}>
              解析预览（{files.length} 个节点 · {formatBytes(totalDecodedBytes(files))}）
            </span>
            <a onClick={props.onClear} style={{ color: COLOR.danger, fontSize: 13 }}>
              清除
            </a>
          </div>
          <ZipTreePreview files={files} />
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="上传 .zip 后在此预览文件树"
        />
      )}
    </div>
  );
}

/** 只读资源树预览（缩进列表，无操作）。 */
function ZipTreePreview({ files }: { files: SkillResourceFileVO[] }) {
  const tree = buildResourceTree(files);
  const render = (
    nodes: ReturnType<typeof buildResourceTree>,
    depth: number,
  ): React.ReactNode =>
    nodes.map((n) => (
      <div key={n.path}>
        <div
          style={{
            paddingLeft: depth * 16 + 4,
            fontSize: 13,
            color: COLOR.textBody,
            lineHeight: '24px',
            fontFamily:
              n.type === 'FILE'
                ? 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
                : undefined,
          }}
        >
          {n.type === 'FOLDER' ? '📁' : '📄'} {n.name}
        </div>
        {render(n.children, depth + 1)}
      </div>
    ));
  return <div style={{ maxHeight: 320, overflow: 'auto' }}>{render(tree, 0)}</div>;
}
