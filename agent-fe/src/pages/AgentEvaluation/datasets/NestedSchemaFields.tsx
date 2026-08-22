/**
 * 按 Schema 层级渲染新增行表单：展开 object 子字段，array 支持增删项。
 */
import { Button, Form, Input, Space, Typography } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import JsonEditor from '@/components/JsonEditor';
import {
  isExpandableArray,
  isExpandableObject,
  isJsonLeaf,
  type SchemaNode,
} from '@/utils/datasetSchema';

const { Text } = Typography;

export interface NestedSchemaFieldsProps {
  nodes: SchemaNode[];
  /** Form 字段名前缀，顶层为空 */
  namePrefix?: (string | number)[];
  depth?: number;
  /** 只读查看（隐藏增删，JSON/输入只读） */
  readOnly?: boolean;
}

export default function NestedSchemaFields({
  nodes,
  namePrefix = [],
  depth = 1,
  readOnly = false,
}: NestedSchemaFieldsProps) {
  return (
    <>
      {nodes.map((node) => (
        <SchemaField
          key={[...namePrefix, node.name].join('.')}
          node={node}
          namePath={[...namePrefix, node.name]}
          depth={depth}
          readOnly={readOnly}
        />
      ))}
    </>
  );
}

function SchemaField({
  node,
  namePath,
  depth,
  readOnly,
}: {
  node: SchemaNode;
  namePath: (string | number)[];
  depth: number;
  readOnly: boolean;
}) {
  const label = node.description
    ? `${node.name}（${node.description}）`
    : node.name;

  if (isExpandableObject(node, depth)) {
    return (
      <div
        style={{
          marginBottom: 16,
          padding: 12,
          border: '1px solid #E2E8F0',
          borderRadius: 8,
          background: '#F8FAFC',
        }}
      >
        <Text strong style={{ display: 'block', marginBottom: 8 }}>
          {label}
        </Text>
        <NestedSchemaFields
          nodes={node.properties || []}
          namePrefix={namePath}
          depth={depth + 1}
          readOnly={readOnly}
        />
      </div>
    );
  }

  if (isExpandableArray(node, depth)) {
    const item = node.items || { name: 'item', type: 'string' };
    const itemIsObject =
      item.type === 'object' && !!item.properties?.length && depth + 1 <= 3;

    return (
      <Form.List name={namePath}>
        {(fields, { add, remove }) => (
          <div
            style={{
              marginBottom: 16,
              padding: 12,
              border: '1px solid #E2E8F0',
              borderRadius: 8,
            }}
          >
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              {label}（数组）
            </Text>
            {fields.map((field) => (
              <div
                key={field.key}
                style={{
                  marginBottom: 12,
                  padding: 8,
                  background: '#fff',
                  border: '1px dashed #CBD5E1',
                  borderRadius: 6,
                }}
              >
                <Space
                  align="start"
                  style={{ width: '100%', justifyContent: 'space-between' }}
                >
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    [{field.name}]
                  </Text>
                  {!readOnly && (
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<MinusCircleOutlined />}
                      onClick={() => remove(field.name)}
                    />
                  )}
                </Space>
                {itemIsObject ? (
                  <NestedSchemaFields
                    nodes={item.properties || []}
                    namePrefix={[field.name]}
                    depth={depth + 1}
                    readOnly={readOnly}
                  />
                ) : isJsonLeaf(item, depth + 1) ? (
                  <Form.Item
                    name={[field.name]}
                    rules={[{ required: false }]}
                    style={{ marginBottom: 0 }}
                  >
                    <JsonEditor height={120} readOnly={readOnly} />
                  </Form.Item>
                ) : (
                  <Form.Item
                    name={[field.name]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input.TextArea
                      rows={2}
                      readOnly={readOnly}
                      placeholder={
                        readOnly ? undefined : `填写 ${node.name} 元素`
                      }
                    />
                  </Form.Item>
                )}
              </div>
            ))}
            {!readOnly && (
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={() =>
                  add(
                    itemIsObject
                      ? Object.fromEntries(
                          (item.properties || []).map((p) => [
                            p.name,
                            undefined,
                          ]),
                        )
                      : isJsonLeaf(item, depth + 1)
                        ? '{\n  \n}'
                        : '',
                  )
                }
              >
                添加 {node.name} 项
              </Button>
            )}
            {readOnly && fields.length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                （空数组）
              </Text>
            )}
          </div>
        )}
      </Form.List>
    );
  }

  if (isJsonLeaf(node, depth)) {
    return (
      <Form.Item
        name={namePath}
        label={label}
        extra={readOnly ? undefined : '自由 JSON（该层级无子字段定义）'}
      >
        <JsonEditor height={140} readOnly={readOnly} />
      </Form.Item>
    );
  }

  return (
    <Form.Item name={namePath} label={label}>
      <Input.TextArea
        rows={2}
        readOnly={readOnly}
        placeholder={readOnly ? undefined : `填写 ${node.name}`}
      />
    </Form.Item>
  );
}
