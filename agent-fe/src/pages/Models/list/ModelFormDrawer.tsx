/**
 * 新建 / 编辑模型 Drawer(支持空间/系统两种 scope,2026-06-17 scope 优化)
 *
 * - 空间模型(scope=SPACE,默认):全字段可填;编辑态 apiKey 留空保留原密文
 * - 系统模型(scope=PLATFORM):全字段可填;后端固定 PLATFORM + workspaceNum 置 null。
 *   <b>系统模型编辑时 API Key 输入框留空保留原值,且不提供显隐查看</b>(方案 §9.1)。
 * - 校验:name ≤128;modelId ≤128;baseUrl 须 http(s) 开头 ≤512;remark ≤500。
 */
import { useEffect } from 'react';
import { Button, Drawer, Form, Input, Space, message } from 'antd';
import {
  useModelCreateMutation,
  useModelUpdateMutation,
  useSystemModelCreateMutation,
  useSystemModelUpdateMutation,
} from '@/services/model';
import type { ModelCreateParam, ModelScope, ModelUpdateParam, ModelVO } from '@/types';
import { MODEL_LIMITS } from '../constants';

interface ModelFormDrawerProps {
  /** 归属范围;默认 SPACE */
  scope?: ModelScope;
  open: boolean;
  /** 编辑目标;为空表示新建 */
  target?: ModelVO;
  onClose: () => void;
  onSaved?: () => void;
}

/** modelId 建议字符集:英文 / 数字 / 中划线 / 点 / 下划线(仅告警提示,不强制拦截)。 */
const MODEL_ID_PATTERN = /^[A-Za-z0-9._-]+$/;

export default function ModelFormDrawer({
  scope = 'SPACE',
  open,
  target,
  onClose,
  onSaved,
}: ModelFormDrawerProps) {
  const isSystem = scope === 'PLATFORM';
  const [form] = Form.useForm();
  const createMut = isSystem ? useSystemModelCreateMutation() : useModelCreateMutation();
  const updateMut = isSystem ? useSystemModelUpdateMutation() : useModelUpdateMutation();

  const isEdit = !!target;
  const saving = createMut.isPending || updateMut.isPending;

  useEffect(() => {
    if (!open) return;
    if (target) {
      form.setFieldsValue({
        name: target.name,
        modelId: target.modelId,
        apiKey: '',
        baseUrl: target.baseUrl,
        remark: target.remark,
      });
    } else {
      form.resetFields();
    }
  }, [open, target, form]);

  const handleSave = async () => {
    const values = await form.validateFields();
    if (isEdit) {
      const apiKey = values.apiKey?.trim();
      const param: ModelUpdateParam = {
        num: target!.num,
        name: values.name.trim(),
        modelId: values.modelId.trim(),
        apiKey: apiKey || undefined,
        baseUrl: values.baseUrl.trim(),
        remark: values.remark?.trim() || undefined,
      };
      await updateMut.mutateAsync(param);
      message.success('保存成功');
    } else {
      const param: ModelCreateParam = {
        scope,
        // 系统模型 workspaceNum 留空(后端置 null);空间模型由 request 拦截器 X-Workspace-Num 头注入
        workspaceNum: isSystem ? undefined : undefined,
        name: values.name.trim(),
        modelId: values.modelId.trim(),
        apiKey: values.apiKey.trim(),
        baseUrl: values.baseUrl.trim(),
        remark: values.remark?.trim() || undefined,
      };
      await createMut.mutateAsync(param);
      message.success('模型已保存为草稿');
    }
    onClose();
    onSaved?.();
  };

  return (
    <Drawer
      title={isEdit ? '编辑模型' : '新建模型'}
      width={480}
      open={open}
      onClose={onClose}
      destroyOnClose
      maskClosable={false}
      footer={
        <Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button type='primary' loading={saving} onClick={handleSave}>
            {isEdit ? '保存' : '保存为草稿'}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout='vertical' requiredMark>
        <Form.Item
          name='name'
          label='名称'
          rules={[
            { required: true, message: '请输入模型名称(≤128 字符)' },
            { max: MODEL_LIMITS.NAME_MAX, message: '模型名称不超过 128 字符' },
          ]}
        >
          <Input placeholder='如:GPT-4o' maxLength={MODEL_LIMITS.NAME_MAX} showCount />
        </Form.Item>

        <Form.Item
          name='modelId'
          label='模型标识'
          tooltip='对外的模型标识(如 gpt-4o),空间内/平台内唯一;建议英文 / 数字 / 中划线 / 点 / 下划线'
          rules={[
            { required: true, message: '请输入模型标识(≤128 字符)' },
            { max: MODEL_LIMITS.MODEL_ID_MAX, message: '模型标识不超过 128 字符' },
            {
              validator: (_, v?: string) => {
                if (!v || MODEL_ID_PATTERN.test(v)) return Promise.resolve();
                return Promise.reject('模型标识建议仅含英文 / 数字 / 中划线 / 点 / 下划线');
              },
            },
          ]}
        >
          <Input placeholder='如:gpt-4o' maxLength={MODEL_LIMITS.MODEL_ID_MAX} showCount />
        </Form.Item>

        <Form.Item
          name='apiKey'
          label='API Key'
          tooltip={
            isEdit
              ? '留空保留原 Key,填写新值才会覆盖更新'
              : '模型访问密钥,提交后加密托管,不再明文展示'
          }
          rules={isEdit ? [] : [{ required: true, message: '请输入模型 API Key' }]}
        >
          <Input.Password placeholder={isEdit ? '留空保留原 Key' : '如:sk-...'} autoComplete='new-password' />
        </Form.Item>

        <Form.Item
          name='baseUrl'
          label='Base URL'
          rules={[
            { required: true, message: '请输入模型服务端点' },
            { max: MODEL_LIMITS.BASE_URL_MAX, message: 'Base URL 不超过 512 字符' },
            { pattern: /^https?:\/\/.+/, message: 'Base URL 须以 http:// 或 https:// 开头' },
          ]}
        >
          <Input placeholder='如:https://api.openai.com/v1' />
        </Form.Item>

        <Form.Item name='remark' label='备注' rules={[{ max: MODEL_LIMITS.REMARK_MAX, message: '备注不超过 500 字' }]}>
          <Input.TextArea placeholder='可选,≤500 字' maxLength={MODEL_LIMITS.REMARK_MAX} showCount rows={3} />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
