/**
 * 从评测任务人工标注蒸馏 LLM 评估器。
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Modal, Select, Typography, message } from 'antd';
import { evalApi } from '@/services/evaluation';
import { modelApi } from '@/services/model';
import type { ModelSelectableVO } from '@/types';
import { EVAL_BASE } from '../constants';

const { Text, Paragraph } = Typography;

export interface DistillGraderModalProps {
  open: boolean;
  taskNum: string;
  taskName: string;
  /** 已填写人工标注的 Case 数 */
  labeledCount: number;
  onClose: () => void;
}

export default function DistillGraderModal({
  open,
  taskNum,
  taskName,
  labeledCount,
  onClose,
}: DistillGraderModalProps) {
  const navigate = useNavigate();
  const [form] = Form.useForm<{
    name: string;
    modelNum: string;
    description?: string;
  }>();
  const [models, setModels] = useState<ModelSelectableVO[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    void modelApi
      .selectable()
      .then(setModels)
      .catch(() => undefined);
    form.setFieldsValue({
      name: `${taskName}-蒸馏评估器`.slice(0, 64),
      description: `从任务「${taskName}」的 ${labeledCount} 条人工标注蒸馏`,
      modelNum: undefined,
    });
  }, [open, taskName, labeledCount, form]);

  const handleOk = async () => {
    if (labeledCount <= 0) {
      message.warning('请先为至少一条 Case 填写并保存人工标注');
      return;
    }
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await evalApi.distillGraderFromTask({
        taskNum,
        name: values.name.trim(),
        modelNum: values.modelNum,
        description: values.description?.trim(),
      });
      message.success('已蒸馏创建 LLM 评估器');
      onClose();
      Modal.confirm({
        title: '蒸馏成功',
        content: '是否前往查看新评估器？可在后续评测任务中挂载使用。',
        okText: '查看评估器',
        cancelText: '留在本页',
        onOk: () => navigate(`${EVAL_BASE}/graders/${res.num}`),
      });
    } catch (e) {
      // validateFields 取消 / 业务错误由拦截器 toast
      if (e && typeof e === 'object' && 'errorFields' in e) {
        return;
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="蒸馏 LLM 评估器"
      open={open}
      onCancel={onClose}
      onOk={() => void handleOk()}
      okText="开始蒸馏"
      confirmLoading={submitting}
      okButtonProps={{ disabled: labeledCount <= 0 }}
      destroyOnHidden
      width={560}
    >
      <Paragraph type="secondary" style={{ marginTop: 0 }}>
        将本任务中已保存的人工标注作为 few-shot 样本，自动生成一个 LLM
        评估器（含 Prompt）。蒸馏不会改写本任务结果。
      </Paragraph>
      <Text
        style={{
          display: 'block',
          marginBottom: 16,
          color: labeledCount > 0 ? undefined : '#DC2626',
        }}
      >
        可用标注样本：{labeledCount} 条
        {labeledCount <= 0 ? '（请先填写并点击「保存标注」）' : ''}
      </Text>
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="评估器名称"
          rules={[{ required: true, message: '请输入名称' }]}
        >
          <Input maxLength={64} placeholder="如：客服语义蒸馏评估器" />
        </Form.Item>
        <Form.Item
          name="modelNum"
          label="评分模型"
          rules={[{ required: true, message: '请选择模型' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="选择用于 LLM 打分的模型"
            options={models.map((m) => ({
              value: m.num,
              label: `${m.name || m.num}${m.modelId ? ` (${m.modelId})` : ''}`,
            }))}
          />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} maxLength={256} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
