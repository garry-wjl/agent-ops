/**
 * 新建知识库 — 两步向导 `/kb/manage/editor/new`
 */
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Radio,
  Space,
  Steps,
  Typography,
  message,
} from 'antd';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import {
  useKbCreateMutation,
  useKbTypeSchemasQuery,
} from '@/services/knowledgeBase';
import { useModelSelectableQuery } from '@/services/model';
import type { KbIndexConfig, KbRetrievalDefaults, KbType } from '@/types';
import {
  DEFAULT_INDEX_CONFIG,
  DEFAULT_RETRIEVAL,
  KB_LIMITS,
  KB_TYPE_META,
} from '../constants';
import KbIndexConfigForm from '../components/KbIndexConfigForm';

const { Text } = Typography;

const COLOR = {
  textPrimary: '#0F172B',
  textMuted: '#90A1B9',
} as const;

export default function KnowledgeBaseCreatePage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [kbType, setKbType] = useState<KbType>('SIMPLE');
  const [indexConfig, setIndexConfig] = useState<KbIndexConfig>({
    ...DEFAULT_INDEX_CONFIG,
  });
  const [retrievalDefaults, setRetrievalDefaults] =
    useState<KbRetrievalDefaults>({ ...DEFAULT_RETRIEVAL });

  const { data: typeSchemas } = useKbTypeSchemasQuery();
  const { data: models } = useModelSelectableQuery();
  const createMut = useKbCreateMutation();

  const schemaTypes = useMemo(() => {
    const fromApi = typeSchemas?.map((s) => s.kbType) ?? [];
    return fromApi.length ? fromApi : (['SIMPLE', 'RAG_FLOW'] as KbType[]);
  }, [typeSchemas]);

  const validateStep1 = () => {
    if (!name.trim()) {
      message.error('请输入名称');
      return false;
    }
    if (name.trim().length > KB_LIMITS.NAME_MAX) {
      message.error(`名称不超过 ${KB_LIMITS.NAME_MAX} 字符`);
      return false;
    }
    return true;
  };

  const validateStep2 = () => {
    if (kbType === 'SIMPLE' && !indexConfig.embeddingModelId) {
      message.error('简易知识库需选择 Embedding 模型');
      return false;
    }
    return true;
  };

  const handleCreate = async () => {
    if (!validateStep2()) return;
    try {
      const vo = await createMut.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        kbType,
        indexConfig,
        retrievalDefaults,
      });
      message.success('知识库已创建');
      navigate(`/kb/manage/detail/${vo.kbNum}`);
    } catch {
      // 拦截器已 toast
    }
  };

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath="/kb/manage"
        moduleName="知识库管理"
        current="新建知识库"
        actions={
          <Space>
            <Button onClick={() => navigate('/kb/manage')}>取消</Button>
            {step === 1 && (
              <Button type="primary" loading={createMut.isPending} onClick={handleCreate}>
                创建
              </Button>
            )}
          </Space>
        }
      />

      <Steps
        current={step}
        style={{ maxWidth: 520, marginBottom: 32 }}
        items={[
          { title: '基本信息' },
          { title: '索引配置' },
        ]}
      />

      {step === 0 && (
        <Form layout="vertical" style={{ maxWidth: 640 }}>
          <Form.Item label="名称" required>
            <Input
              placeholder="知识库名称"
              maxLength={KB_LIMITS.NAME_MAX}
              showCount
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea
              placeholder="用途说明（可选）"
              maxLength={KB_LIMITS.DESC_MAX}
              showCount
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Form.Item>
          <Form.Item label="类型" required>
            <Radio.Group
              value={kbType}
              onChange={(e) => setKbType(e.target.value)}
              style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
            >
              {schemaTypes.map((t) => (
                <Radio key={t} value={t}>
                  <span style={{ fontWeight: 500 }}>{KB_TYPE_META[t].label}</span>
                  <Text style={{ color: COLOR.textMuted, fontSize: 12, marginLeft: 8 }}>
                    {t}
                  </Text>
                </Radio>
              ))}
            </Radio.Group>
          </Form.Item>
          <Button
            type="primary"
            onClick={() => {
              if (validateStep1()) setStep(1);
            }}
          >
            下一步
          </Button>
        </Form>
      )}

      {step === 1 && (
        <div style={{ maxWidth: 640 }}>
          <KbIndexConfigForm
            kbType={kbType}
            value={indexConfig}
            onChange={setIndexConfig}
            typeSchemas={typeSchemas}
            models={models ?? []}
          />
          <Form layout="vertical" style={{ marginTop: 24 }}>
            <Text
              style={{
                fontWeight: 600,
                color: COLOR.textPrimary,
                marginBottom: 12,
                display: 'block',
              }}
            >
              默认检索参数
            </Text>
            <Form.Item label="Top K">
              <InputNumber
                min={1}
                max={50}
                style={{ width: 200 }}
                value={retrievalDefaults.topK ?? DEFAULT_RETRIEVAL.topK}
                onChange={(v) =>
                  setRetrievalDefaults((r) => ({ ...r, topK: v ?? undefined }))
                }
              />
            </Form.Item>
            <Form.Item label="最低分数">
              <InputNumber
                min={0}
                max={1}
                step={0.05}
                style={{ width: 200 }}
                value={retrievalDefaults.minScore ?? DEFAULT_RETRIEVAL.minScore}
                onChange={(v) =>
                  setRetrievalDefaults((r) => ({
                    ...r,
                    minScore: v ?? undefined,
                  }))
                }
              />
            </Form.Item>
          </Form>
          <Space>
            <Button onClick={() => setStep(0)}>上一步</Button>
            <Button type="primary" loading={createMut.isPending} onClick={handleCreate}>
              创建
            </Button>
          </Space>
        </div>
      )}
    </div>
  );
}
