/**
 * 评测种子库 — `/skill/evaluation/seeds`
 * ⚠️ Figma 未画 Seeds 画板，套用扁平化外壳（与 Skill List/Evaluation List 同风格）
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { EvalApi, SkillApi } from '@/services';
import type { EvalSeedVO, SkillVO } from '@/types';
import { formatTime, prettyJson, safeJsonParse } from '@/utils/format';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  headerBg: '#F8FAFC',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  textError: '#DC2626',
} as const;

export default function EvaluationSeedsPage() {
  const navigate = useNavigate();
  const [skills, setSkills] = useState<SkillVO[]>([]);
  const [skillFilter, setSkillFilter] = useState<string | undefined>();
  const [seeds, setSeeds] = useState<EvalSeedVO[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const reqSeq = useRef(0);

  const reload = async () => {
    const seq = ++reqSeq.current;
    const list = await EvalApi.seedList(skillFilter);
    if (seq === reqSeq.current) setSeeds(list);
  };

  useEffect(() => {
    SkillApi.pageList({ pageNo: 1, pageSize: 200, status: 'PUBLISHED' }).then(
      (r) => setSkills(r.list),
    );
  }, []);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [skillFilter]);

  const handleAdd = async () => {
    const v = await form.validateFields();
    const input = safeJsonParse(v.input);
    const expectedOutput = v.expectedOutput
      ? safeJsonParse(v.expectedOutput)
      : undefined;
    if (!input) {
      message.error('input 必须为合法 JSON');
      return;
    }
    await EvalApi.saveSeed(v.skillNum, input, expectedOutput);
    message.success('已加入种子库');
    setOpen(false);
    form.resetFields();
    reload();
  };

  const columns: TableColumnsType<EvalSeedVO> = useMemo(
    () => [
      {
        title: 'NUM',
        dataIndex: 'num',
        key: 'num',
        width: 200,
        render: (n: string) => (
          <Text
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 12,
              color: COLOR.textPrimary,
            }}
          >
            {n}
          </Text>
        ),
      },
      {
        title: 'SKILL',
        dataIndex: 'skillNum',
        key: 'skillNum',
        width: 200,
        render: (s: string) => (
          <Text style={{ color: COLOR.textSecondary, fontSize: 12 }}>{s}</Text>
        ),
      },
      {
        title: 'INPUT',
        dataIndex: 'input',
        key: 'input',
        render: (v: unknown) => <CodeBlock value={v} />,
      },
      {
        title: 'EXPECTED OUTPUT',
        dataIndex: 'expectedOutput',
        key: 'expectedOutput',
        render: (v: unknown) =>
          v !== undefined ? (
            <CodeBlock value={v} />
          ) : (
            <Text style={{ color: COLOR.textMuted }}>-</Text>
          ),
      },
      {
        title: 'ORIGIN',
        dataIndex: 'origin',
        key: 'origin',
        width: 100,
        render: (o: string) => (
          <Text style={{ color: COLOR.textSecondary, fontSize: 12 }}>{o}</Text>
        ),
      },
      {
        title: '创建时间',
        dataIndex: 'gmtCreate',
        key: 'gmtCreate',
        width: 160,
        render: (t: string) => (
          <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>
            {formatTime(t)}
          </Text>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 80,
        render: (_, r) => (
          <a
            style={{ color: COLOR.textError }}
            onClick={() =>
              Modal.confirm({
                title: '删除种子？',
                okType: 'danger',
                onOk: async () => {
                  await EvalApi.deleteSeed(r.num);
                  message.success('已删除');
                  reload();
                },
              })
            }
          >
            删除
          </a>
        ),
      },
    ],
    [],
  );

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      {/* 面包屑 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 12,
          color: COLOR.textMuted,
          marginBottom: 16,
        }}
      >
        <a
          onClick={() => navigate('/skill/evaluation')}
          style={{ color: COLOR.textMuted }}
        >
          Skill 评测
        </a>
        <RightOutlined style={{ fontSize: 9 }} />
        <span style={{ color: COLOR.textPrimary }}>种子库</span>
      </div>

      {/* 标题区 */}
      <div style={{ marginBottom: 24 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
          }}
        >
          <div>
            <Title
              level={2}
              style={{
                margin: 0,
                color: COLOR.textPrimary,
                fontSize: 24,
                fontWeight: 700,
              }}
            >
              评测种子库
            </Title>
            <Text
              style={{
                color: COLOR.textSecondary,
                fontSize: 14,
                marginTop: 4,
                display: 'block',
              }}
            >
              手动维护的 case 种子，可在新建评测时作为数据源
            </Text>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setOpen(true)}
          >
            新增种子
          </Button>
        </div>
      </div>

      {/* 工具栏 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Space>
          <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>筛选 Skill</Text>
          <Select
            allowClear
            style={{ width: 280 }}
            value={skillFilter}
            onChange={setSkillFilter}
            options={skills.map((s) => ({ value: s.num, label: s.name }))}
            showSearch
            optionFilterProp="label"
            placeholder="不限"
          />
        </Space>
      </div>

      {/* 表格 */}
      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
          background: '#fff',
        }}
      >
        <Table<EvalSeedVO>
          rowKey="num"
          columns={columns}
          dataSource={seeds}
          pagination={false}
          size="middle"
          rowClassName={() => 'seeds-row'}
        />
      </div>

      <style>{`
        .seeds-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
          vertical-align: top;
        }
        .ant-table-thead > tr > th {
          background: ${COLOR.headerBg} !important;
          color: ${COLOR.textMuted} !important;
          font-size: 11px !important;
          font-weight: 700 !important;
          letter-spacing: 0.06em !important;
          text-transform: uppercase;
          padding: 10px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
        }
        .ant-table-thead > tr > th::before {
          display: none !important;
        }
      `}</style>

      <Modal
        open={open}
        title="新增种子"
        onOk={handleAdd}
        onCancel={() => setOpen(false)}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="Skill" name="skillNum" rules={[{ required: true }]}>
            <Select
              options={skills.map((s) => ({ value: s.num, label: s.name }))}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item
            label="input (JSON)"
            name="input"
            rules={[{ required: true }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item label="expectedOutput (JSON, 可选)" name="expectedOutput">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function CodeBlock({ value }: { value: unknown }) {
  return (
    <SyntaxHighlighter
      language="json"
      customStyle={{
        margin: 0,
        padding: 6,
        fontSize: 11,
        background: '#F8FAFC',
        maxHeight: 80,
        overflow: 'auto',
        borderRadius: 4,
      }}
    >
      {prettyJson(value)}
    </SyntaxHighlighter>
  );
}
