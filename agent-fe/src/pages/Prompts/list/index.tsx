/**
 * Prompt 中心列表页 — `/prompt/manage`（技术方案 §11 前端 / §7.2.1）
 *
 * - antd Table（套用 Tool/Skill 列表工程风格：COLOR token / monospace num /
 *   uppercase 表头 / bordered 容器）。
 * - 顶部「+ 新建 Prompt」→ 右侧 Drawer 新建（§0 #10，新建/编辑共用 Drawer）。
 * - 关键字搜索（num / promptKey / description）+ 标签筛选；按当前工作空间过滤（§0 #8）。
 * - 行操作：详情（抽屉）/ 编辑（抽屉）/ 删除（二次确认软删）。
 */
import { useMemo, useState } from "react";
import {
  Button,
  Empty,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { usePromptPageQuery, usePromptDeleteMutation } from "@/services/prompt";
import type { PromptPageQueryParam, PromptVo } from "@/types";
import PromptDetailDrawer from "./PromptDetailDrawer";
import PromptEditorDrawer from "./PromptEditorDrawer";
import PermissionGate from "@/components/PermissionGate";

const { Title, Text } = Typography;

const COLOR = {
  border: "#E2E8F0",
  headerBg: "#F8FAFC",
  textPrimary: "#0F172B",
  textSecondary: "#45556C",
  textMuted: "#90A1B9",
} as const;

export default function PromptListPage() {
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState("");
  const [keywordInput, setKeywordInput] = useState("");
  const [tag, setTag] = useState("");
  const [tagInput, setTagInput] = useState("");

  const [detailNum, setDetailNum] = useState<string | undefined>();
  // editor: { num?: string } —— num 为空表示新建；对象存在即打开
  const [editor, setEditor] = useState<{ num?: string } | undefined>();

  const query: PromptPageQueryParam = useMemo(
    () => ({
      pageNo,
      pageSize,
      keyword: keyword || undefined,
      tag: tag || undefined,
    }),
    [pageNo, pageSize, keyword, tag],
  );

  const { data: page, isFetching } = usePromptPageQuery(query);
  const list = page?.list ?? [];
  const total = page?.total ?? 0;

  const deleteMut = usePromptDeleteMutation();

  const doSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const doTagFilter = () => {
    setPageNo(1);
    setTag(tagInput.trim());
  };

  const handleDelete = (r: PromptVo) => {
    Modal.confirm({
      title: "删除 Prompt",
      content: `确认删除「${r.promptKey}」？删除后不可恢复。`,
      okText: "删除",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        await deleteMut.mutateAsync({ num: r.num });
        message.success("已删除");
      },
    });
  };

  const columns: TableColumnsType<PromptVo> = useMemo(
    () => [
      {
        title: "编号",
        dataIndex: "num",
        key: "num",
        width: 200,
        fixed: "left",
        render: (num: string, r: PromptVo) => (
          <a
            onClick={() => setDetailNum(r.num)}
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
              whiteSpace: "nowrap",
            }}
          >
            {num}
          </a>
        ),
      },
      {
        title: "Prompt Key",
        dataIndex: "promptKey",
        key: "promptKey",
        width: 180,
        render: (k: string) => (
          <Text code style={{ color: COLOR.textPrimary }}>
            {k}
          </Text>
        ),
      },
      {
        title: "描述",
        dataIndex: "description",
        key: "description",
        width: 180,
        render: (d: string) => (
          <Text
            style={{ color: COLOR.textSecondary, width: "12em" }}
            ellipsis={{ tooltip: d || "—" }}
          >
            {d || "—"}
          </Text>
        ),
      },
      {
        title: "模板预览",
        dataIndex: "templateContent",
        key: "templateContent",
        width: 220,
        render: (t: string) => (
          <Text
            style={{
              color: COLOR.textMuted,
              fontSize: 12,
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              width: "16em",
            }}
            ellipsis={{ tooltip: t }}
          >
            {t?.replace(/\s+/g, " ").trim() || "—"}
          </Text>
        ),
      },
      {
        title: "标签",
        dataIndex: "tags",
        key: "tags",
        width: 160,
        render: (tags: string[] | undefined) =>
          tags?.length ? (
            <Space size={4} wrap>
              {tags.slice(0, 4).map((t) => (
                <Tag
                  key={t}
                  color="blue"
                  style={{ marginInlineEnd: 0, cursor: "pointer" }}
                  onClick={() => {
                    setTagInput(t);
                    setTag(t);
                    setPageNo(1);
                  }}
                >
                  {t}
                </Tag>
              ))}
              {tags.length > 4 && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  +{tags.length - 4}
                </Text>
              )}
            </Space>
          ) : (
            <Text style={{ color: COLOR.textMuted }}>—</Text>
          ),
      },
      {
        title: "创建人",
        dataIndex: "createNo",
        key: "createNo",
        width: 100,
        render: (no: string) => (
          <Text style={{ color: COLOR.textSecondary, whiteSpace: "nowrap" }}>
            {no || "—"}
          </Text>
        ),
      },
      {
        title: "更新时间",
        dataIndex: "updateTime",
        key: "updateTime",
        width: 170,
        render: (t: string) => (
          <Text
            style={{
              color: COLOR.textMuted,
              fontSize: 12,
              whiteSpace: "nowrap",
            }}
          >
            {t}
          </Text>
        ),
      },
      {
        title: "操作",
        key: "action",
        width: 160,
        fixed: "right",
        render: (_: unknown, r: PromptVo) => (
          <Space size={12} wrap>
            <a onClick={() => setDetailNum(r.num)}>详情</a>
            <PermissionGate anyOf={['prompt:update']}>
              <a onClick={() => setEditor({ num: r.num })}>编辑</a>
            </PermissionGate>
            <PermissionGate anyOf={['prompt:delete']}>
              <a style={{ color: "#DC2626" }} onClick={() => handleDelete(r)}>
                删除
              </a>
            </PermissionGate>
          </Space>
        ),
      },
    ],
    // handler 由稳定 hook 提供，无需额外依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return (
    <div style={{ padding: 32, background: "#fff", minHeight: "100%" }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          marginBottom: 24,
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
            Prompt 中心
          </Title>
          <Text
            style={{
              color: COLOR.textSecondary,
              fontSize: 14,
              marginTop: 4,
              display: "block",
            }}
          >
            集中沉淀提示词资产；按 Key 稳定引用、版本可追溯，提示词调整无需研发发版
          </Text>
        </div>
        <PermissionGate anyOf={['prompt:create']}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setEditor({})}
          >
            新建 Prompt
          </Button>
        </PermissionGate>
      </div>

      {/* 筛选行 */}
      <div
        style={{
          display: "flex",
          justifyContent: "flex-end",
          alignItems: "center",
          marginBottom: 16,
          gap: 12,
          flexWrap: "wrap",
        }}
      >
        <Input
          allowClear
          placeholder="按标签筛选"
          value={tagInput}
          onChange={(e) => setTagInput(e.target.value)}
          onPressEnter={doTagFilter}
          onBlur={doTagFilter}
          onClear={() => {
            setTagInput("");
            setTag("");
            setPageNo(1);
          }}
          style={{ width: 180 }}
        />
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder="搜索 编号 / Key / 描述…"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onPressEnter={doSearch}
          onBlur={doSearch}
          style={{ width: 280 }}
        />
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: "hidden",
          background: "#fff",
        }}
      >
        <Table<PromptVo>
          rowKey="num"
          columns={columns}
          dataSource={list}
          loading={isFetching}
          size="middle"
          scroll={{ x: 1300 }}
          locale={{
            emptyText: (
              <Empty description="还没有 Prompt" style={{ padding: 32 }} />
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, ps) => {
              setPageNo(p);
              setPageSize(ps);
            },
          }}
          rowClassName={() => "prompt-list-row"}
        />
      </div>

      <PromptDetailDrawer
        num={detailNum}
        open={!!detailNum}
        onClose={() => setDetailNum(undefined)}
      />
      <PromptEditorDrawer
        num={editor?.num}
        open={!!editor}
        onClose={() => setEditor(undefined)}
      />

      <style>{`
        .prompt-list-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
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
        .ant-table-thead > tr > th::before { display: none !important; }
      `}</style>
    </div>
  );
}
