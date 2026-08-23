/**
 * 知识库文件表 — 上传 / 索引状态 / 重建 / 删除
 */
import { useRef, useState } from 'react';
import {
  Button,
  Empty,
  Modal,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import {
  useKbDeleteFileMutation,
  useKbFilesQuery,
  useKbReindexAllMutation,
  useKbReindexFileMutation,
  useKbReindexStaleMutation,
  useKbRegisterFileMutation,
} from '@/services/knowledgeBase';
import { uploadFile } from '@/services/common';
import type { KbFileVO, KbIndexStatus } from '@/types';
import { KB_INDEX_STATUS_META } from '../constants';
import PermissionGate from '@/components/PermissionGate';

const { Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textMuted: '#90A1B9',
  textSecondary: '#45556C',
} as const;

function formatSize(bytes?: number) {
  if (!bytes) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function KbFileTable({ kbNum }: { kbNum: string }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const { data: files = [], isFetching, refetch } = useKbFilesQuery(kbNum);
  const registerMut = useKbRegisterFileMutation();
  const deleteMut = useKbDeleteFileMutation();
  const reindexFileMut = useKbReindexFileMutation();
  const reindexStaleMut = useKbReindexStaleMutation();
  const reindexAllMut = useKbReindexAllMutation();

  const handleUpload = async (fileList: FileList | null) => {
    if (!fileList?.length) return;
    setUploading(true);
    try {
      for (const file of Array.from(fileList)) {
        const uploaded = await uploadFile({
          file,
          fileName: `knowledge-base/${kbNum}/${file.name}`,
          mimeType: file.type || undefined,
          sizeBytes: file.size,
        });
        await registerMut.mutateAsync({
          kbNum,
          ossFileId: uploaded.fileId,
          fileName: file.name,
          mimeType: file.type || undefined,
          fileSize: file.size,
        });
      }
      message.success('文件已上传并开始索引');
      await refetch();
    } catch {
      // 拦截器已 toast
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const handleDelete = (row: KbFileVO) => {
    Modal.confirm({
      title: '删除文件',
      content: `确认删除「${row.fileName}」？索引数据将一并移除。`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteMut.mutateAsync({ kbNum, fileNum: row.fileNum });
        message.success('已删除');
        await refetch();
      },
    });
  };

  const columns: TableColumnsType<KbFileVO> = [
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      render: (name: string) => (
        <Text style={{ color: COLOR.textPrimary }}>{name}</Text>
      ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 100,
      render: (s: number) => formatSize(s),
    },
    {
      title: '索引状态',
      dataIndex: 'indexStatus',
      key: 'indexStatus',
      width: 120,
      render: (st: KbIndexStatus, row) => {
        const meta = KB_INDEX_STATUS_META[st] ?? {
          label: st,
          color: COLOR.textMuted,
        };
        return (
          <span style={{ color: meta.color, fontSize: 12, fontWeight: 500 }}>
            ● {meta.label}
            {row.errorMessage && st === 'FAILED' && (
              <Text
                type="danger"
                style={{ fontSize: 11, display: 'block' }}
                ellipsis={{ tooltip: row.errorMessage }}
              >
                {row.errorMessage}
              </Text>
            )}
          </span>
        );
      },
    },
    {
      title: '分块数',
      dataIndex: 'chunkCount',
      key: 'chunkCount',
      width: 80,
      render: (c?: number) => c ?? '—',
    },
    {
      title: '配置版本',
      dataIndex: 'indexConfigVersion',
      key: 'indexConfigVersion',
      width: 90,
      render: (v?: number) => v ?? '—',
    },
    {
      title: '索引完成',
      dataIndex: 'indexedAt',
      key: 'indexedAt',
      width: 170,
      render: (t?: string) => (
        <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>{t ?? '—'}</Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: unknown, row) => (
        <Space size={12}>
          <PermissionGate anyOf={['knowledge_base:update']}>
            <a
              onClick={async () => {
                await reindexFileMut.mutateAsync({
                  kbNum,
                  fileNum: row.fileNum,
                });
                message.success('已提交重建索引');
                await refetch();
              }}
            >
              重建
            </a>
            <a
              style={{ color: '#DC2626' }}
              onClick={() => handleDelete(row)}
            >
              删除
            </a>
          </PermissionGate>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <Space wrap>
          <PermissionGate anyOf={['knowledge_base:update']}>
            <Button
              icon={<UploadOutlined />}
              loading={uploading}
              onClick={() => inputRef.current?.click()}
            >
              上传文件
            </Button>
            <input
              ref={inputRef}
              type="file"
              multiple
              style={{ display: 'none' }}
              onChange={(e) => handleUpload(e.target.files)}
            />
            <Button
              onClick={async () => {
                await reindexStaleMut.mutateAsync({ kbNum });
                message.success('已提交陈旧文件重建');
                await refetch();
              }}
            >
              重建陈旧
            </Button>
            <Button
              onClick={async () => {
                await reindexAllMut.mutateAsync({ kbNum });
                message.success('已提交全量重建');
                await refetch();
              }}
            >
              全量重建
            </Button>
          </PermissionGate>
        </Space>
        <Button size="small" onClick={() => refetch()}>刷新</Button>
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table<KbFileVO>
          rowKey="fileNum"
          size="middle"
          columns={columns}
          dataSource={files}
          loading={isFetching || uploading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description="暂无文件，请上传文档开始索引" />
            ),
          }}
        />
      </div>
    </div>
  );
}
