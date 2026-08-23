/**
 * 知识库详情 — `/kb/manage/detail/:kbNum`
 */
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import PermissionGate from '@/components/PermissionGate';
import {
  useKbConfigAlignmentQuery,
  useKbDeleteMutation,
  useKbDetailQuery,
  useKbTestRetrieveMutation,
  useKbTypeSchemasQuery,
  useKbUpdateBasicMutation,
  useKbUpdateIndexConfigMutation,
} from '@/services/knowledgeBase';
import { useModelSelectableQuery } from '@/services/model';
import type { KbChunkVO, KbIndexConfig } from '@/types';
import {
  KB_LIMITS,
  KB_STATUS_META,
  KB_TYPE_META,
  DEFAULT_RETRIEVAL,
} from '../constants';
import KbFileTable from '../components/KbFileTable';
import KbIndexConfigForm from '../components/KbIndexConfigForm';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
} as const;

export default function KnowledgeBaseDetailPage() {
  const { kbNum = '' } = useParams<{ kbNum: string }>();
  const navigate = useNavigate();
  const { data: detail, isLoading } = useKbDetailQuery(kbNum);
  useBreadcrumbName(detail?.name);

  const { data: typeSchemas } = useKbTypeSchemasQuery();
  const { data: models } = useModelSelectableQuery();
  const { data: alignment } = useKbConfigAlignmentQuery(kbNum);

  const updateBasicMut = useKbUpdateBasicMutation();
  const updateIndexMut = useKbUpdateIndexConfigMutation();
  const deleteMut = useKbDeleteMutation();
  const testMut = useKbTestRetrieveMutation();

  const [basicName, setBasicName] = useState('');
  const [basicDesc, setBasicDesc] = useState('');
  const [indexConfig, setIndexConfig] = useState<KbIndexConfig>({});
  const [question, setQuestion] = useState('');
  const [testTopK, setTestTopK] = useState(DEFAULT_RETRIEVAL.topK);
  const [testMinScore, setTestMinScore] = useState(DEFAULT_RETRIEVAL.minScore);
  const [chunks, setChunks] = useState<KbChunkVO[]>([]);

  useEffect(() => {
    if (!detail) return;
    setBasicName(detail.name);
    setBasicDesc(detail.description ?? '');
    setIndexConfig(detail.indexConfig ?? {});
    const rd = detail.retrievalDefaults;
    if (rd?.topK != null) setTestTopK(rd.topK);
    if (rd?.minScore != null) setTestMinScore(rd.minScore);
  }, [detail]);

  const handleSaveBasic = async () => {
    if (!basicName.trim()) {
      message.error('名称不能为空');
      return;
    }
    await updateBasicMut.mutateAsync({
      kbNum,
      name: basicName.trim(),
      description: basicDesc.trim() || undefined,
    });
    message.success('基本信息已保存');
  };

  const handleSaveIndex = async () => {
    await updateIndexMut.mutateAsync({ kbNum, indexConfig });
    message.success('索引配置已保存（将触发陈旧文件重建）');
  };

  const handleDelete = () => {
    Modal.confirm({
      title: '删除知识库',
      content: `确认删除「${detail?.name}」？文件与索引数据将不可恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteMut.mutateAsync({ kbNum });
        message.success('已删除');
        navigate('/kb/manage');
      },
    });
  };

  const handleTestRetrieve = async () => {
    if (!question.trim()) {
      message.error('请输入检索问题');
      return;
    }
    const result = await testMut.mutateAsync({
      kbNum,
      question: question.trim(),
      topK: testTopK,
      minScore: testMinScore,
    });
    setChunks(result);
  };

  if (isLoading && !detail) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin />
      </div>
    );
  }

  if (!detail) {
    return (
      <div style={{ padding: 32 }}>
        <Text>知识库不存在或已删除</Text>
      </div>
    );
  }

  const statusMeta = KB_STATUS_META[detail.status];
  const typeMeta = KB_TYPE_META[detail.kbType];

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath="/kb/manage"
        moduleName="知识库管理"
        current={detail.name}
        actions={
          <PermissionGate anyOf={['knowledge_base:delete']}>
            <Button danger onClick={handleDelete}>删除</Button>
          </PermissionGate>
        }
      />

      <div style={{ marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0, color: COLOR.textPrimary }}>
          {detail.name}
        </Title>
        <Space style={{ marginTop: 8 }} wrap>
          <Tag color={typeMeta.color} style={{ background: typeMeta.bg, border: 0 }}>
            {typeMeta.label}
          </Tag>
          <span style={{ color: statusMeta.color, fontSize: 13 }}>
            ● {statusMeta.label}
          </span>
          <Text style={{ color: COLOR.textMuted, fontSize: 13 }}>
            {detail.kbNum}
          </Text>
        </Space>
      </div>

      <Tabs
        items={[
          {
            key: 'files',
            label: `文件（${detail.fileCount ?? 0}）`,
            children: <KbFileTable kbNum={kbNum} />,
          },
          {
            key: 'index',
            label: '索引配置',
            children: (
              <div style={{ maxWidth: 720 }}>
                <KbIndexConfigForm
                  kbType={detail.kbType}
                  value={indexConfig}
                  onChange={setIndexConfig}
                  typeSchemas={typeSchemas}
                  models={models ?? []}
                />
                {alignment && alignment.length > 0 && (
                  <div style={{ marginTop: 24 }}>
                    <Text strong style={{ display: 'block', marginBottom: 8 }}>
                      配置版本对齐
                    </Text>
                    <Table
                      size="small"
                      pagination={false}
                      rowKey="configVersion"
                      dataSource={alignment}
                      columns={[
                        {
                          title: '配置版本',
                          dataIndex: 'configVersion',
                          render: (v: number, row) =>
                            row.current ? `${v}（当前）` : v,
                        },
                        { title: '文件数', dataIndex: 'fileCount' },
                      ]}
                    />
                  </div>
                )}
                <PermissionGate anyOf={['knowledge_base:update']}>
                  <Button
                    type="primary"
                    style={{ marginTop: 16 }}
                    loading={updateIndexMut.isPending}
                    onClick={handleSaveIndex}
                  >
                    保存索引配置
                  </Button>
                </PermissionGate>
              </div>
            ),
          },
          {
            key: 'retrieve',
            label: '检索测试',
            children: (
              <div style={{ maxWidth: 720 }}>
                <Form layout="vertical">
                  <Form.Item label="问题" required>
                    <Input.TextArea
                      rows={3}
                      placeholder="输入要检索的自然语言问题"
                      value={question}
                      onChange={(e) => setQuestion(e.target.value)}
                    />
                  </Form.Item>
                  <Space wrap>
                    <Form.Item label="Top K" style={{ marginBottom: 0 }}>
                      <InputNumber
                        min={1}
                        max={50}
                        value={testTopK}
                        onChange={(v) => setTestTopK(v ?? DEFAULT_RETRIEVAL.topK)}
                      />
                    </Form.Item>
                    <Form.Item label="最低分数" style={{ marginBottom: 0 }}>
                      <InputNumber
                        min={0}
                        max={1}
                        step={0.05}
                        value={testMinScore}
                        onChange={(v) =>
                          setTestMinScore(v ?? DEFAULT_RETRIEVAL.minScore)
                        }
                      />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0 }}>
                      <Button
                        type="primary"
                        loading={testMut.isPending}
                        onClick={handleTestRetrieve}
                      >
                        检索
                      </Button>
                    </Form.Item>
                  </Space>
                </Form>
                {chunks.length > 0 && (
                  <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {chunks.map((c, i) => (
                      <div
                        key={`${c.fileNum}-${i}`}
                        style={{
                          border: `1px solid ${COLOR.border}`,
                          borderRadius: 8,
                          padding: 12,
                        }}
                      >
                        <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                          {c.fileName ?? c.fileNum} · score {c.score?.toFixed(3) ?? '—'}
                        </div>
                        <div
                          style={{
                            marginTop: 8,
                            fontSize: 13,
                            color: COLOR.textSecondary,
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {c.content}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ),
          },
          {
            key: 'basic',
            label: '基本信息',
            children: (
              <div style={{ maxWidth: 640 }}>
                <Descriptions
                  bordered
                  size="small"
                  column={1}
                  style={{ marginBottom: 24 }}
                >
                  <Descriptions.Item label="编号">{detail.kbNum}</Descriptions.Item>
                  <Descriptions.Item label="分块总数">
                    {detail.chunkCount ?? 0}
                  </Descriptions.Item>
                  <Descriptions.Item label="绑定 Agent">
                    {detail.agentsBoundCount ?? 0}
                  </Descriptions.Item>
                  <Descriptions.Item label="配置版本">
                    {detail.indexConfig?.configVersion ?? '—'}
                  </Descriptions.Item>
                </Descriptions>
                <Form layout="vertical">
                  <Form.Item label="名称" required>
                    <Input
                      maxLength={KB_LIMITS.NAME_MAX}
                      value={basicName}
                      onChange={(e) => setBasicName(e.target.value)}
                    />
                  </Form.Item>
                  <Form.Item label="描述">
                    <Input.TextArea
                      maxLength={KB_LIMITS.DESC_MAX}
                      rows={3}
                      value={basicDesc}
                      onChange={(e) => setBasicDesc(e.target.value)}
                    />
                  </Form.Item>
                  <PermissionGate anyOf={['knowledge_base:update']}>
                    <Button
                      type="primary"
                      loading={updateBasicMut.isPending}
                      onClick={handleSaveBasic}
                    >
                      保存
                    </Button>
                  </PermissionGate>
                </Form>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
