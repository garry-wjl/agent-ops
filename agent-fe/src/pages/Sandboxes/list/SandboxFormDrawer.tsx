/**
 * 新建 / 编辑沙箱 Drawer（PRD §8.2 / §7.5）
 *
 * - 新建：全字段可填，保存为草稿。
 * - 编辑：草稿 / 失败态可改全部规格字段；其余态（初始化 / 在线 / 下线）规格锁定，
 *   仅备注可改，顶部 Alert 提示「沙箱已初始化，规格不可修改」。
 * - 校验：名称 ≤64；CPU 0.5 步进且 [0.5,16]；内存 [128,65536]；存活 [1,1440]；备注 ≤100。
 *
 * 类型本期固定 CODE（Select 选项单一且 disabled）。workspaceNum 由后端经 X-Workspace-Num 头取，前端不传。
 */
import { useEffect } from "react";
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  message,
} from "antd";
import {
  useSandboxCreateMutation,
  useSandboxSubmitMutation,
  useSandboxUpdateMutation,
} from "@/services/sandbox";
import type {
  SandboxCreateParam,
  SandboxUpdateParam,
  SandboxVO,
} from "@/types";
import { SANDBOX_LIMITS, isSpecEditable } from "../constants";

interface SandboxFormDrawerProps {
  open: boolean;
  /** 编辑目标；为空表示新建 */
  target?: SandboxVO;
  onClose: () => void;
  onSaved?: () => void;
}

export default function SandboxFormDrawer({
  open,
  target,
  onClose,
  onSaved,
}: SandboxFormDrawerProps) {
  const [form] = Form.useForm();
  const createMut = useSandboxCreateMutation();
  const updateMut = useSandboxUpdateMutation();
  const submitMut = useSandboxSubmitMutation();

  const isEdit = !!target;
  // 编辑非草稿 / 失败态：规格锁定，仅备注可改
  const specLocked = isEdit && !isSpecEditable(target!.status);
  // 提交按钮：新建，或编辑可提交态（草稿 / 失败）时显示；规格锁定态不显示
  const showSubmit = !isEdit || isSpecEditable(target!.status);
  const saving =
    createMut.isPending || updateMut.isPending || submitMut.isPending;

  useEffect(() => {
    if (!open) return;
    if (target) {
      form.setFieldsValue({
        name: target.name,
        type: target.type,
        cpu: target.cpu,
        memoryMb: target.memoryMb,
        aliveMinutes: target.aliveMinutes,
        remark: target.remark,
        poolEnabled: target.poolEnabled ?? false,
        poolSize: target.poolSize ?? 1,
        maxConcurrent: target.maxConcurrent ?? 8,
        sessionIdleTtlMinutes: target.sessionIdleTtlMinutes ?? 10,
      });
    } else {
      form.resetFields();
      // 新建默认值
      form.setFieldsValue({
        type: "CODE",
        cpu: 1,
        memoryMb: 2048,
        aliveMinutes: 60,
        poolEnabled: false,
        poolSize: 1,
        maxConcurrent: 8,
        sessionIdleTtlMinutes: 10,
      });
    }
  }, [open, target, form]);

  /** 落库（新建 create / 编辑 update），返回沙箱业务编号。 */
  const persist = async (): Promise<string> => {
    const values = await form.validateFields();
    if (isEdit) {
      const poolFields = {
        poolEnabled: !!values.poolEnabled,
        poolSize: values.poolSize ?? 1,
        maxConcurrent: values.maxConcurrent ?? 8,
        sessionIdleTtlMinutes: values.sessionIdleTtlMinutes ?? 10,
      };
      const param: SandboxUpdateParam = specLocked
        ? { num: target!.num, remark: values.remark?.trim() || undefined, ...poolFields }
        : {
            num: target!.num,
            name: values.name.trim(),
            cpu: values.cpu,
            memoryMb: values.memoryMb,
            aliveMinutes: values.aliveMinutes,
            remark: values.remark?.trim() || undefined,
            ...poolFields,
          };
      await updateMut.mutateAsync(param);
      return target!.num;
    }
    const param: SandboxCreateParam = {
      name: values.name.trim(),
      type: values.type,
      cpu: values.cpu,
      memoryMb: values.memoryMb,
      aliveMinutes: values.aliveMinutes,
      remark: values.remark?.trim() || undefined,
      poolEnabled: !!values.poolEnabled,
      poolSize: values.poolSize ?? 1,
      maxConcurrent: values.maxConcurrent ?? 8,
      sessionIdleTtlMinutes: values.sessionIdleTtlMinutes ?? 10,
    };
    const created = await createMut.mutateAsync(param);
    return created.num;
  };

  /** 仅保存（新建存草稿 / 编辑保存）。 */
  const handleSave = async () => {
    await persist();
    message.success(isEdit ? "保存成功" : "沙箱已保存为草稿");
    onClose();
    onSaved?.();
  };

  /** 保存后立即提交（草稿 → 初始化，触发异步建容器）。 */
  const handleSaveAndSubmit = async () => {
    const num = await persist();
    await submitMut.mutateAsync({ num });
    message.success("已提交，正在初始化容器…");
    onClose();
    onSaved?.();
  };

  return (
    <Drawer
      title={isEdit ? "编辑沙箱" : "新建沙箱"}
      width={480}
      open={open}
      onClose={onClose}
      destroyOnClose
      maskClosable={false}
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button
            loading={createMut.isPending || updateMut.isPending}
            onClick={handleSave}
          >
            {isEdit ? "保存" : "保存为草稿"}
          </Button>
          {showSubmit && (
            <Button
              type="primary"
              loading={saving}
              onClick={handleSaveAndSubmit}
            >
              提交
            </Button>
          )}
        </Space>
      }
    >
      {specLocked && (
        <Alert
          type="info"
          showIcon
          message="沙箱已初始化，规格不可修改，仅可编辑备注。"
          style={{ marginBottom: 16 }}
        />
      )}
      <Form form={form} layout="vertical" requiredMark>
        <Form.Item
          name="name"
          label="名称"
          rules={[
            { required: true, message: "请输入沙箱名称（≤64 字符）" },
            { max: SANDBOX_LIMITS.NAME_MAX, message: "沙箱名称不超过 64 字符" },
          ]}
        >
          <Input
            placeholder="如：Python 代码执行环境"
            maxLength={SANDBOX_LIMITS.NAME_MAX}
            showCount
            disabled={specLocked}
          />
        </Form.Item>

        <Form.Item name="type" label="类型" rules={[{ required: true }]}>
          {/* 本期仅代码沙箱，选项单一且禁改 */}
          <Select disabled options={[{ value: "CODE", label: "代码沙箱" }]} />
        </Form.Item>

        <Form.Item
          name="cpu"
          label="CPU（核）"
          rules={[
            { required: true, message: "请输入 CPU 核数" },
            {
              validator: (_, v: number) => {
                if (v == null) return Promise.resolve();
                if (v < SANDBOX_LIMITS.CPU_MIN || v > SANDBOX_LIMITS.CPU_MAX) {
                  return Promise.reject("CPU 不能超过 16 核，且不少于 0.5 核");
                }
                // 0.5 的整数倍：cpu * 2 必须为整数
                if (Math.round(v * 2) !== v * 2) {
                  return Promise.reject("CPU 需为 0.5 核的整数倍");
                }
                return Promise.resolve();
              },
            },
          ]}
        >
          <InputNumber
            min={SANDBOX_LIMITS.CPU_MIN}
            max={SANDBOX_LIMITS.CPU_MAX}
            step={SANDBOX_LIMITS.CPU_STEP}
            style={{ width: "100%" }}
            addonAfter="核"
            disabled={specLocked}
          />
        </Form.Item>

        <Form.Item
          name="memoryMb"
          label="内存（MB）"
          rules={[
            { required: true, message: "请输入内存大小" },
            {
              type: "integer",
              min: SANDBOX_LIMITS.MEMORY_MIN,
              max: SANDBOX_LIMITS.MEMORY_MAX,
              message: "内存需在 128 ~ 65536 MB 之间",
            },
          ]}
        >
          <InputNumber
            min={SANDBOX_LIMITS.MEMORY_MIN}
            max={SANDBOX_LIMITS.MEMORY_MAX}
            step={128}
            style={{ width: "100%" }}
            addonAfter="MB"
            disabled={specLocked}
          />
        </Form.Item>

        <Form.Item
          name="aliveMinutes"
          label="存活时间（分钟）"
          rules={[
            { required: true, message: "请输入容器存活时间" },
            {
              type: "integer",
              min: SANDBOX_LIMITS.ALIVE_MIN,
              max: SANDBOX_LIMITS.ALIVE_MAX,
              message: "容器存活时间需在 1 ~ 1440 分钟之间",
            },
          ]}
        >
          <InputNumber
            min={SANDBOX_LIMITS.ALIVE_MIN}
            max={SANDBOX_LIMITS.ALIVE_MAX}
            style={{ width: "100%" }}
            addonAfter="分钟"
            disabled={specLocked}
          />
        </Form.Item>

        <Form.Item
          name="poolEnabled"
          label="启用沙箱池"
          valuePropName="checked"
          extra="开启后预创建常驻空闲实例，加速会话启动；关闭则按会话现开容器"
        >
          <Switch />
        </Form.Item>

        <Form.Item noStyle shouldUpdate={(prev, cur) => prev.poolEnabled !== cur.poolEnabled}>
          {() =>
            form.getFieldValue("poolEnabled") ? (
              <Form.Item
                name="poolSize"
                label="常驻实例数"
                rules={[
                  { required: true, message: "请输入常驻实例数" },
                  { type: "integer", min: 1, max: 64, message: "常驻数需在 1~64" },
                ]}
                extra="默认 1，为热池目标空闲水位"
              >
                <InputNumber min={1} max={64} style={{ width: "100%" }} />
              </Form.Item>
            ) : null
          }
        </Form.Item>

        <Form.Item
          name="maxConcurrent"
          label="最大并发实例"
          rules={[
            { required: true, message: "请输入最大并发" },
            { type: "integer", min: 1, max: 128, message: "最大并发需在 1~128" },
          ]}
          extra="该资产下 IDLE+BOUND 上限（关池同样生效）"
        >
          <InputNumber min={1} max={128} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item
          name="sessionIdleTtlMinutes"
          label="会话空闲回收（分钟）"
          rules={[
            { required: true, message: "请输入空闲 TTL" },
            { type: "integer", min: 1, max: 1440, message: "TTL 需在 1~1440" },
          ]}
          extra="会话无活动超过该时间后回收容器"
        >
          <InputNumber min={1} max={1440} style={{ width: "100%" }} addonAfter="分钟" />
        </Form.Item>

        <Form.Item
          name="remark"
          label="备注"
          rules={[
            { max: SANDBOX_LIMITS.REMARK_MAX, message: "备注不超过 100 字" },
          ]}
        >
          <Input.TextArea
            placeholder="可选，≤100 字"
            maxLength={SANDBOX_LIMITS.REMARK_MAX}
            showCount
            rows={3}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
