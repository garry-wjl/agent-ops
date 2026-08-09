/**
 * Prompt 详情抽屉（右侧 Drawer，全字段只读，技术方案 §7.2.1 detail）。
 *
 * - 通用字段：业务编码 / Prompt Key / 描述 / 标签 / 审计字段。
 * - 模板内容：等宽代码块展示原文（含 {{变量}}，不解析）。
 */
import { Descriptions, Drawer, Space, Spin, Tag, Typography } from "antd";
import { usePromptDetailQuery } from "@/services/prompt";
import UserName from "@/components/UserName";

const { Text, Paragraph } = Typography;

interface PromptDetailDrawerProps {
  num?: string;
  open: boolean;
  onClose: () => void;
}

export default function PromptDetailDrawer({
  num,
  open,
  onClose,
}: PromptDetailDrawerProps) {
  const { data: detailVo, isLoading } = usePromptDetailQuery(
    open ? num : undefined,
  );
  // 后端 PromptDetailVo 为嵌套结构 { prompt }，取出全字段快照
  const prompt = detailVo?.prompt;

  return (
    <Drawer
      title="Prompt 详情"
      width={640}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      {isLoading || !prompt ? (
        <div style={{ textAlign: "center", padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <Descriptions column={1} bordered size="middle">
          <Descriptions.Item label="业务编码">
            <Text
              style={{
                fontFamily:
                  'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              }}
            >
              {prompt.num}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="Prompt Key">
            <Text code>{prompt.promptKey}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="描述">
            {prompt.description || "—"}
          </Descriptions.Item>
          <Descriptions.Item label="标签">
            {prompt.tags?.length ? (
              <Space size={[4, 4]} wrap>
                {prompt.tags.map((t) => (
                  <Tag key={t} color="blue">
                    {t}
                  </Tag>
                ))}
              </Space>
            ) : (
              "—"
            )}
          </Descriptions.Item>
          <Descriptions.Item label="模板内容">
            <Paragraph
              style={{
                margin: 0,
                maxHeight: 360,
                overflow: "auto",
                background: "#F8FAFC",
                padding: 8,
                borderRadius: 4,
                fontSize: 12,
                fontFamily:
                  'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                whiteSpace: "pre-wrap",
              }}
            >
              {prompt.templateContent}
            </Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label="创建人">
            <UserName userNum={prompt.createNo} />
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {prompt.createTime}
          </Descriptions.Item>
          <Descriptions.Item label="更新人">
            <UserName userNum={prompt.updateNo} />
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {prompt.updateTime}
          </Descriptions.Item>
        </Descriptions>
      )}
    </Drawer>
  );
}
