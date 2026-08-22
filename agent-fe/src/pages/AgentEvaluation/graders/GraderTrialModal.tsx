/**
 * 评估器试跑弹窗 — 详情 / 编辑页右上角共用。
 */
import { useState } from 'react';
import { Button, Form, Input, Modal, Typography, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import type { GraderTrialResultVO } from '@/types';

const { Text, Paragraph } = Typography;

type Props = {
  graderNum: string;
  open: boolean;
  onClose: () => void;
};

export default function GraderTrialModal({ graderNum, open, onClose }: Props) {
  const [form] = Form.useForm();
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<GraderTrialResultVO | null>(null);

  const handleClose = () => {
    setResult(null);
    form.resetFields();
    onClose();
  };

  const onTrial = async (values: {
    response?: string;
    reference?: string;
    keywords?: string;
  }) => {
    if (!graderNum) {
      message.warning('评估器编号无效');
      return;
    }
    setRunning(true);
    setResult(null);
    try {
      const variables: Record<string, unknown> = {
        response: values.response ?? '',
      };
      if (values.reference != null && values.reference !== '') {
        variables.reference = values.reference;
      }
      if (values.keywords != null && values.keywords.trim()) {
        try {
          variables.keywords = JSON.parse(values.keywords);
        } catch {
          variables.keywords = values.keywords
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean);
        }
      }
      const res = await evalApi.trialRunGrader({
        graderNum,
        variables,
      });
      setResult(res);
    } finally {
      setRunning(false);
    }
  };

  return (
    <Modal
      title="试跑评估器"
      open={open}
      onCancel={handleClose}
      footer={null}
      destroyOnHidden
      width={640}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ response: 'hello world', reference: 'hello world' }}
        onFinish={(v) => void onTrial(v)}
        style={{ marginTop: 8 }}
      >
        <Form.Item name="response" label="response（实际输出）">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item name="reference" label="reference（可选）">
          <Input />
        </Form.Item>
        <Form.Item
          name="keywords"
          label="keywords（CONTAINS，逗号分隔或 JSON 数组）"
        >
          <Input placeholder='如 hello,world 或 ["hello"]' />
        </Form.Item>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          htmlType="submit"
          loading={running}
        >
          试跑
        </Button>
      </Form>
      {result && (
        <div style={{ marginTop: 16 }}>
          <Paragraph style={{ marginBottom: 4 }}>
            <Text strong>得分：</Text>
            {result.score ?? '—'}
            {' · '}
            <Text strong>通过：</Text>
            {result.passed == null ? '—' : result.passed ? '是' : '否'}
          </Paragraph>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            {result.explanation || '无说明'}
          </Paragraph>
        </div>
      )}
    </Modal>
  );
}
