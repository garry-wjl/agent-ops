/**
 * 任务创建：按评估器编辑变量 mapping（默认 response/reference，可自定义）。
 */
import { AutoComplete, Button, Input, Space, Table, Typography } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { EvalGraderVO } from '@/types';
import { COLOR } from '../constants';
import { LabelWithTip } from '../LabelWithTip';
import {
  MAPPING_SOURCE_OPTIONS,
  defaultMappingRows,
  newMappingRow,
  type MappingRow,
} from './graderMapping';

const { Text } = Typography;

export const MAPPING_FIELD_TIP = (
  <>
    <div style={{ fontWeight: 600, marginBottom: 6 }}>变量映射做什么？</div>
    <div style={{ marginBottom: 8 }}>
      把 Prompt / 规则里的变量名，接到本任务的数据源。默认已够用，一般无需改。
    </div>
    <div style={{ fontWeight: 600, marginBottom: 4 }}>默认约定</div>
    <div>• response ← $actual_output（Agent 实际输出）</div>
    <div style={{ marginBottom: 8 }}>
      • reference ← $row.reference（评测集行内 reference）
    </div>
    <div style={{ fontWeight: 600, marginBottom: 4 }}>自定义何时用</div>
    <div>• 评测集列名不是 reference（如 expected_answer）</div>
    <div>• Prompt 要用多个行字段（policy、category…）</div>
    <div style={{ marginBottom: 8 }}>• Prompt 变量名与默认不一致（如 answer）</div>
    <div style={{ fontWeight: 600, marginBottom: 4 }}>数据源写法</div>
    <div>• $actual_output / $trace</div>
    <div>• $row.字段名（对应评测集列）</div>
    <div style={{ marginTop: 6, opacity: 0.9 }}>
      变量名须与 LLM Prompt 的 {'{{变量名}}'} 或 Code 脚本中的变量一致。
    </div>
  </>
);

interface GraderMappingEditorProps {
  graderNums: string[];
  graders: EvalGraderVO[];
  value: Record<string, MappingRow[]>;
  onChange: (next: Record<string, MappingRow[]>) => void;
}

export function GraderMappingEditor({
  graderNums,
  graders,
  value,
  onChange,
}: GraderMappingEditorProps) {
  if (!graderNums.length) {
    return (
      <Text type="secondary" style={{ fontSize: 13 }}>
        请先选择评估器；默认将使用 response / reference 映射。
      </Text>
    );
  }

  const patchGrader = (num: string, rows: MappingRow[]) => {
    onChange({ ...value, [num]: rows });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
        <LabelWithTip label="变量映射" tip={MAPPING_FIELD_TIP} />
        <Text type="secondary" style={{ fontSize: 12 }}>
          默认已填好；仅自定义变量时改这里
        </Text>
      </div>
      {graderNums.map((num) => {
        const g = graders.find((x) => x.num === num);
        const rows = value[num] ?? defaultMappingRows();
        return (
          <div
            key={num}
            style={{
              border: `1px solid ${COLOR.border}`,
              borderRadius: 8,
              padding: 12,
              background: COLOR.headerBg,
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 8,
                gap: 8,
                flexWrap: 'wrap',
              }}
            >
              <Text strong style={{ fontSize: 13 }}>
                {g?.name ?? num}
                <Text
                  type="secondary"
                  style={{
                    fontWeight: 400,
                    marginLeft: 8,
                    fontFamily: 'ui-monospace, monospace',
                    fontSize: 12,
                  }}
                >
                  {num}
                  {g?.builtinCode || g?.kind
                    ? ` · ${g.builtinCode || g.kind}`
                    : ''}
                </Text>
              </Text>
              <Space size={4}>
                <Button
                  type="link"
                  size="small"
                  icon={<ReloadOutlined />}
                  onClick={() => patchGrader(num, defaultMappingRows())}
                >
                  恢复默认
                </Button>
                <Button
                  type="link"
                  size="small"
                  icon={<PlusOutlined />}
                  onClick={() => patchGrader(num, [...rows, newMappingRow()])}
                >
                  添加变量
                </Button>
              </Space>
            </div>
            <Table
              size="small"
              pagination={false}
              rowKey="id"
              dataSource={rows}
              style={{ background: '#fff' }}
              columns={[
                {
                  title: '变量名',
                  dataIndex: 'name',
                  width: '28%',
                  render: (_, row, index) => (
                    <Input
                      placeholder="如 response"
                      value={row.name}
                      onChange={(e) => {
                        const next = [...rows];
                        next[index] = { ...row, name: e.target.value };
                        patchGrader(num, next);
                      }}
                    />
                  ),
                },
                {
                  title: '数据源',
                  dataIndex: 'source',
                  render: (_, row, index) => (
                    <AutoComplete
                      options={MAPPING_SOURCE_OPTIONS.map((o) => ({
                        value: o.value,
                        label: o.label,
                      }))}
                      value={row.source}
                      placeholder="选择或输入，如 $row.expected_answer"
                      style={{ width: '100%' }}
                      onChange={(v) => {
                        const next = [...rows];
                        next[index] = { ...row, source: v };
                        patchGrader(num, next);
                      }}
                      filterOption={(input, option) =>
                        String(option?.value ?? '')
                          .toLowerCase()
                          .includes(input.toLowerCase()) ||
                        String(option?.label ?? '')
                          .toLowerCase()
                          .includes(input.toLowerCase())
                      }
                    />
                  ),
                },
                {
                  title: '',
                  width: 64,
                  render: (_, __, index) => (
                    <Button
                      type="link"
                      danger
                      size="small"
                      disabled={rows.length <= 1}
                      onClick={() =>
                        patchGrader(
                          num,
                          rows.filter((_, i) => i !== index),
                        )
                      }
                    >
                      删除
                    </Button>
                  ),
                },
              ]}
            />
          </div>
        );
      })}
    </div>
  );
}
