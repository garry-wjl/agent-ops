/**
 * A2A 模式接入表单 — v2.6 新增
 *
 * 字段：Nacos Agent Name (必填) / 显示名 / 描述 / 备注
 * 双按钮：
 *   - [保存草稿]  —— 调 saveA2aDraft：仅落库，不触发远端校验
 *   - [校验并接入] —— 调 createA2a：拉一次远端 AgentCard，成功则进入 PENDING_SYNC
 *
 * 错误处理：
 *   - BizCode 2011（远端不可达 / agentName 不存在）→ 字段下方红字
 *   - BizCode 2012（已订阅过同名 nacosAgent）→ 顶部 Alert，附跳转链接
 *   - 其他 → 由 axios 拦截器统一 message.error
 *
 * 重复接入：后端检测到 nacosAgentName 已订阅，会直接返回原 detail，
 * 此时弹 Toast 提示并跳转到已存在的详情页。
 */
import { useState } from 'react';
import { createPortal } from 'react-dom';
import { Alert, Button, Form, Input, Space, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  useA2aCreateMutation,
  useA2aSaveDraftMutation,
} from '@/services/agent';
import { BizError } from '@/services/request';
import { BizCode } from '@/types/common';
import type { A2aCreateParam, A2aDraftParam, AgentDetailVO } from '@/types';

const { Text } = Typography;

const COLOR = {
  textMuted: '#90A1B9',
  errorText: '#DC2626',
} as const;

export interface A2aCreateFormProps {
  /** 接入或保存草稿成功后回调；父组件可关闭 Drawer + 刷新列表 */
  onDone?: (detail?: AgentDetailVO) => void;
  /** 草稿 → 转正流程时传入 */
  draftAgentNum?: string;
  /** 已存在草稿的初始值（继续接入草稿时使用） */
  initialValues?: Partial<A2aCreateParam>;
  /**
   * 2026-06-11：操作按钮渲染目标容器（整页编辑器把按钮放到顶部右侧时传入）。
   * 传了则按钮 portal 到该容器，组件自身底部不再渲染按钮；不传则保持原底部按钮（Drawer 场景）。
   */
  actionsContainer?: HTMLElement | null;
}

export default function A2aCreateForm({
  onDone,
  draftAgentNum,
  initialValues,
  actionsContainer,
}: A2aCreateFormProps) {
  const navigate = useNavigate();
  const [form] = Form.useForm<A2aCreateParam>();
  const [topAlert, setTopAlert] = useState<{
    message: string;
    existedNum?: string;
  } | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);

  const createMutation = useA2aCreateMutation();
  const saveDraftMutation = useA2aSaveDraftMutation();

  const handleSaveDraft = async () => {
    try {
      const values = await form.validateFields(['nacosAgentName']).catch(() => null);
      // 草稿允许 nacosAgentName 为空（用户先填一半）
      const raw = form.getFieldsValue();
      const param: A2aDraftParam = {
        nacosAgentName: raw.nacosAgentName,
        displayName: raw.displayName,
        description: raw.description,
        remark: raw.remark,
        agentNum: draftAgentNum,
      };
      void values;
      const vo = await saveDraftMutation.mutateAsync(param);
      message.success('草稿已保存');
      onDone?.();
      void vo;
    } catch {
      // axios 拦截器已 toast，不再处理
    }
  };

  const handleCreate = async () => {
    setTopAlert(null);
    setFieldError(null);
    let values: A2aCreateParam;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    try {
      const detail = await createMutation.mutateAsync({
        ...values,
        draftAgentNum,
      });
      // 后端检测到同 nacosAgentName 已订阅时，直接返回原 detail（不报错）
      message.success(`Agent ${detail.num} 接入成功，等待 Nacos 推送 AgentCard`);
      onDone?.(detail);
    } catch (e) {
      if (e instanceof BizError) {
        if (e.code === BizCode.A2A_REMOTE_UNREACHABLE) {
          setFieldError(
            e.message ||
              '远端 Nacos 注册中心未找到该 Agent，请确认 Agent Name 拼写',
          );
          return;
        }
        if (e.code === BizCode.A2A_AGENT_ALREADY_SUBSCRIBED) {
          // 后端 message 形如 "Agent already subscribed: AGT-xxxx"
          const existedNum = parseExistedNumFromMessage(e.message);
          setTopAlert({
            message:
              e.message || '该 Nacos Agent 已被订阅，请前往已存在的详情页查看',
            existedNum,
          });
          return;
        }
      }
      // 其他错误：axios 拦截器已 toast
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {topAlert && (
        <Alert
          type="warning"
          showIcon
          message={topAlert.message}
          action={
            topAlert.existedNum ? (
              <Button
                size="small"
                type="link"
                onClick={() =>
                  navigate(`/agent/manage/detail/${topAlert.existedNum}`)
                }
              >
                跳转详情
              </Button>
            ) : null
          }
          closable
          onClose={() => setTopAlert(null)}
        />
      )}
      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
        requiredMark
      >
        <Form.Item
          name="nacosAgentName"
          label="Nacos Agent Name"
          rules={[
            { required: true, message: '请填写 Nacos 上注册的 Agent Name' },
            { max: 128 },
          ]}
          extra={
            <Text style={{ fontSize: 12, color: COLOR.textMuted }}>
              即 Nacos 服务命名，例如 garry-research-agent；接入后将作为远端 AgentCard 拉取的标识
            </Text>
          }
          validateStatus={fieldError ? 'error' : undefined}
          help={
            fieldError ? (
              <span style={{ color: COLOR.errorText }}>{fieldError}</span>
            ) : undefined
          }
        >
          <Input placeholder="例如：garry-research-agent" allowClear />
        </Form.Item>

        <Form.Item
          name="displayName"
          label="显示名（可选）"
          extra={
            <Text style={{ fontSize: 12, color: COLOR.textMuted }}>
              留空则使用远端 AgentCard 的 name；首次推送后可在详情页编辑
            </Text>
          }
        >
          <Input placeholder="平台内展示用名称" allowClear maxLength={64} />
        </Form.Item>

        <Form.Item name="description" label="描述（可选）">
          <Input.TextArea
            rows={2}
            placeholder="可选：覆盖远端 AgentCard 的描述"
            maxLength={500}
            showCount
          />
        </Form.Item>

        <Form.Item name="remark" label="接入备注（可选）">
          <Input.TextArea
            rows={2}
            placeholder="本次接入的内部备注，仅本平台可见"
            maxLength={200}
            showCount
          />
        </Form.Item>
      </Form>

      {(() => {
        const actions = (
          <Space>
            <Button
              onClick={handleSaveDraft}
              loading={saveDraftMutation.isPending}
            >
              保存草稿
            </Button>
            <Button
              type="primary"
              onClick={handleCreate}
              loading={createMutation.isPending}
            >
              校验并接入
            </Button>
          </Space>
        );
        // 整页编辑器：按钮 portal 到顶部右侧容器；Drawer 场景：保留底部按钮条
        if (actionsContainer) return createPortal(actions, actionsContainer);
        return (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
              paddingTop: 8,
              borderTop: '1px solid #F1F5F9',
            }}
          >
            {actions}
          </div>
        );
      })()}
    </div>
  );
}

/** 后端 BizError.message 兼容："Agent already subscribed: AGT-xxxx" / "AGT-xxxx 已订阅" */
function parseExistedNumFromMessage(msg?: string): string | undefined {
  if (!msg) return undefined;
  const m = /AGT-[A-Za-z0-9]+/.exec(msg);
  return m?.[0];
}
