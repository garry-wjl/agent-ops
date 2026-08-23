/**
 * 知识库索引配置表单 — 创建向导 / 详情编辑共用
 */
import { Form, InputNumber, Select } from 'antd';
import type { KbIndexConfig, KbType, KbTypeSchemaVO, ModelSelectableVO } from '@/types';
import { SPLIT_STRATEGY_LABEL } from '../constants';

export interface KbIndexConfigFormProps {
  kbType: KbType;
  value: KbIndexConfig;
  onChange: (next: KbIndexConfig) => void;
  typeSchemas?: KbTypeSchemaVO[];
  models?: ModelSelectableVO[];
}

export default function KbIndexConfigForm({
  kbType,
  value,
  onChange,
  typeSchemas,
  models = [],
}: KbIndexConfigFormProps) {
  const schema = typeSchemas?.find((s) => s.kbType === kbType);
  const patch = (p: Partial<KbIndexConfig>) => onChange({ ...value, ...p });

  const showEmbedding =
    schema?.fields?.some((f) => f.name === 'embeddingModelId') ?? kbType === 'SIMPLE';

  return (
    <Form layout="vertical" style={{ maxWidth: 560 }}>
      {showEmbedding && (
        <Form.Item
          label="Embedding 模型"
          required={kbType === 'SIMPLE'}
        >
          <Select
            showSearch
            placeholder="选择已启用模型"
            value={value.embeddingModelId}
            onChange={(v) => patch({ embeddingModelId: v })}
            optionFilterProp="label"
            options={models.map((m) => ({
              value: m.num,
              label:
                m.scope === 'PLATFORM'
                  ? `[系统] ${m.name}（${m.modelId}）`
                  : `[空间] ${m.name}（${m.modelId}）`,
            }))}
          />
        </Form.Item>
      )}
      {kbType === 'SIMPLE' && (
        <>
          <Form.Item label="分块策略">
            <Select
              value={value.splitStrategy ?? 'PARAGRAPH'}
              onChange={(v) => patch({ splitStrategy: v })}
              options={Object.entries(SPLIT_STRATEGY_LABEL).map(([k, label]) => ({
                value: k,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item label="分块大小（字符）">
            <InputNumber
              min={128}
              max={8192}
              style={{ width: '100%' }}
              value={value.chunkSize ?? 512}
              onChange={(v) => patch({ chunkSize: v ?? undefined })}
            />
          </Form.Item>
          <Form.Item label="分块重叠（字符）">
            <InputNumber
              min={0}
              max={2048}
              style={{ width: '100%' }}
              value={value.chunkOverlap ?? 64}
              onChange={(v) => patch({ chunkOverlap: v ?? undefined })}
            />
          </Form.Item>
        </>
      )}
      {kbType === 'RAG_FLOW' && (
        <Form.Item label="解析器 ID（可选）">
          <Select
            allowClear
            placeholder="使用空间默认解析器"
            value={value.parserId}
            onChange={(v) => patch({ parserId: v })}
            options={[
              { value: 'naive', label: '通用（naive）' },
              { value: 'paper', label: '论文（paper）' },
              { value: 'book', label: '书籍（book）' },
            ]}
          />
        </Form.Item>
      )}
    </Form>
  );
}
