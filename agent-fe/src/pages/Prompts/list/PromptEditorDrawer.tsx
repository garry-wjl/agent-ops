/**
 * Prompt 新建 / 编辑抽屉（右侧 Drawer，新建与编辑共用，技术方案 §0 #10）。
 *
 * - 新建：num 为空 → promptApi.create，成功后回填生成的 num。
 * - 编辑：传入 num → 拉详情回填表单 → promptApi.update。
 * - 业务编码 num 系统生成、用户不可见不可填（§0 #3）：编辑态只读展示，新建态不展示。
 * - promptKey 失焦唯一性校验（§7.2.1 checkKey）：命中冲突时表单项报错，阻断保存。
 * - 模板内容原样存储，不解析 `{{变量}}`（§0 #5）；编辑器内仅做长度提示。
 */
import { useEffect, useRef, useState } from "react";
import {
  Button,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Typography,
  message,
} from "antd";
import {
  usePromptCreateMutation,
  usePromptDetailQuery,
  usePromptUpdateMutation,
} from "@/services/prompt";
import { promptApi } from "@/services/prompt";
import type { PromptCreateParam, PromptUpdateParam } from "@/types";
import { PROMPT_KEY_HINT, PROMPT_LIMITS, validateTags } from "../constants";

const { Text } = Typography;

interface PromptEditorDrawerProps {
  /** 编辑态传业务编码；新建态传 undefined */
  num?: string;
  open: boolean;
  onClose: () => void;
  /** 保存成功回调（列表已自动失效，这里仅用于关闭等额外动作） */
  onSaved?: () => void;
}

interface FormValues {
  promptKey: string;
  description?: string;
  templateContent: string;
  tags?: string[];
}

export default function PromptEditorDrawer({
  num,
  open,
  onClose,
  onSaved,
}: PromptEditorDrawerProps) {
  const isEdit = !!num;
  const [form] = Form.useForm<FormValues>();

  const { data: detailVo, isLoading: detailLoading } = usePromptDetailQuery(
    open && isEdit ? num : undefined,
  );
  // 后端 PromptDetailVo 为嵌套结构 { prompt }，取出全字段快照复用
  const detail = detailVo?.prompt;

  const createMut = usePromptCreateMutation();
  const updateMut = usePromptUpdateMutation();
  const saving = createMut.isPending || updateMut.isPending;

  // 失焦唯一性校验态：避免提交时 promptKey 已知冲突
  const [keyConflict, setKeyConflict] = useState(false);
  const [keyChecking, setKeyChecking] = useState(false);
  // 记录最后一次校验的 key，避免过期请求覆盖结果
  const lastCheckedRef = useRef<string>("");

  // 打开 / 切换目标时回填表单
  useEffect(() => {
    if (!open) return;
    if (isEdit && detail) {
      form.setFieldsValue({
        promptKey: detail.promptKey,
        description: detail.description,
        templateContent: detail.templateContent,
        tags: detail.tags ?? [],
      });
    } else if (!isEdit) {
      form.resetFields();
    }
    setKeyConflict(false);
    lastCheckedRef.current = isEdit && detail ? detail.promptKey : "";
  }, [open, isEdit, detail, form]);

  /** promptKey 失焦：调 checkKey 预检（编辑态排除自身 num）。 */
  const handleKeyBlur = async () => {
    const key = (form.getFieldValue("promptKey") as string | undefined)?.trim();
    if (!key) {
      setKeyConflict(false);
      return;
    }
    // 编辑态且未改动 → 跳过
    if (isEdit && detail && key === detail.promptKey) {
      setKeyConflict(false);
      return;
    }
    lastCheckedRef.current = key;
    setKeyChecking(true);
    try {
      const exists = await promptApi.checkKey(key, isEdit ? num : undefined);
      // 过期响应丢弃
      if (lastCheckedRef.current !== key) return;
      setKeyConflict(exists);
    } catch {
      // 校验接口异常不阻断（保存时 DB 唯一索引兜底）
      setKeyConflict(false);
    } finally {
      setKeyChecking(false);
    }
  };

  const handleSubmit = async () => {
    // Key 唯一性预检命中冲突 → 阻断（错误已展示在字段下方）
    if (keyConflict) {
      message.error("Prompt Key 已存在，请更换");
      return;
    }

    let values: FormValues;
    try {
      // 必填 / 长度 / 标签校验统一走 antd rules，未过自动高亮对应字段
      values = await form.validateFields();
    } catch {
      return;
    }

    const tags = values.tags ?? [];

    if (isEdit) {
      const param: PromptUpdateParam = {
        num: num as string,
        promptKey: values.promptKey.trim(),
        description: values.description?.trim() || undefined,
        templateContent: values.templateContent,
        tags,
      };
      await updateMut.mutateAsync(param);
      message.success("已保存");
    } else {
      const param: PromptCreateParam = {
        promptKey: values.promptKey.trim(),
        description: values.description?.trim() || undefined,
        templateContent: values.templateContent,
        tags,
      };
      await createMut.mutateAsync(param);
      message.success("已创建");
    }
    onSaved?.();
    onClose();
  };

  return (
    <Drawer
      title={isEdit ? "编辑 Prompt" : "新建 Prompt"}
      width={640}
      open={open}
      onClose={onClose}
      destroyOnClose
      maskClosable={!saving}
      footer={
        <Space style={{ float: "right" }}>
          <Button onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button type="primary" loading={saving} onClick={handleSubmit}>
            保存
          </Button>
        </Space>
      }
    >
      {isEdit && detailLoading ? (
        <div style={{ textAlign: "center", padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <Form<FormValues>
          form={form}
          layout="vertical"
          requiredMark
          initialValues={{ tags: [] }}
        >
          {isEdit && (
            <Form.Item label="业务编码">
              <Text
                style={{
                  fontFamily:
                    'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                }}
              >
                {detail?.num ?? num}
              </Text>
              <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                系统生成，不可修改
              </Text>
            </Form.Item>
          )}

          <Form.Item
            label="Prompt Key"
            name="promptKey"
            required
            tooltip={PROMPT_KEY_HINT}
            extra={PROMPT_KEY_HINT}
            // 仅在唯一性校验命中冲突 / 校验中时接管校验态，避免覆盖 antd 必填/长度红字
            validateStatus={keyConflict ? "error" : keyChecking ? "validating" : undefined}
            help={
              keyConflict
                ? "该 Key 在当前工作空间已存在，请更换"
                : keyChecking
                  ? "校验中…"
                  : undefined
            }
            hasFeedback={keyChecking}
            rules={[
              { required: true, message: "请填写 Prompt Key" },
              {
                max: PROMPT_LIMITS.KEY_MAX,
                message: `不超过 ${PROMPT_LIMITS.KEY_MAX} 字符`,
              },
              { whitespace: true, message: "不能为纯空白" },
            ]}
          >
            <Input
              placeholder="如 greeting.intro"
              maxLength={PROMPT_LIMITS.KEY_MAX}
              onBlur={handleKeyBlur}
              onChange={() => keyConflict && setKeyConflict(false)}
            />
          </Form.Item>

          <Form.Item
            label="描述"
            name="description"
            rules={[
              {
                max: PROMPT_LIMITS.DESC_MAX,
                message: `不超过 ${PROMPT_LIMITS.DESC_MAX} 字符`,
              },
            ]}
          >
            <Input.TextArea
              placeholder="这个 Prompt 用在什么场景（选填）"
              autoSize={{ minRows: 2, maxRows: 4 }}
              maxLength={PROMPT_LIMITS.DESC_MAX}
              showCount
            />
          </Form.Item>

          <Form.Item
            label="模板内容"
            name="templateContent"
            required
            tooltip="原样存储，平台不解析 {{变量}}，由调用方运行时替换"
            rules={[
              { required: true, message: "请填写模板内容" },
              { whitespace: true, message: "不能为纯空白" },
              {
                max: PROMPT_LIMITS.TEMPLATE_MAX,
                message: `不超过 ${PROMPT_LIMITS.TEMPLATE_MAX} 字符`,
              },
            ]}
          >
            <Input.TextArea
              placeholder={"你好 {{userName}}，欢迎使用 {{productName}}。"}
              autoSize={{ minRows: 8, maxRows: 20 }}
              maxLength={PROMPT_LIMITS.TEMPLATE_MAX}
              showCount
              style={{
                fontFamily:
                  'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                fontSize: 13,
              }}
            />
          </Form.Item>

          <Form.Item
            label="标签"
            name="tags"
            tooltip="自由输入;回车追加;单 tag ≤32 字符;上限 20 个"
            rules={[
              {
                validator: (_, value: string[] | undefined) => {
                  const r = validateTags(value ?? []);
                  return r.ok
                    ? Promise.resolve()
                    : Promise.reject(new Error(r.error));
                },
              },
            ]}
          >
            <Select
              mode="tags"
              placeholder="e.g. 客服, 通用, greeting"
              tokenSeparators={[","]}
            />
          </Form.Item>
        </Form>
      )}
    </Drawer>
  );
}
