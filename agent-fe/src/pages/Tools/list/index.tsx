/**
 * 工具列表页 — `/tool/manage`（PRD §8.1 / §7.2）
 *
 * - antd Table（套用 Sandbox/Skill 列表工程风格：COLOR token / `● 状态` 胶囊 /
 *   monospace num / uppercase 表头 / bordered 容器）
 * - 顶部「+ 新建工具」→ 直接进入新建编辑器（在页面内选类型 + 创建方式）
 * - 类型 tab（全部 / MCP / FunctionCall）+ 状态 / 创建方式下拉 + 关键字搜索
 * - 行操作按状态矩阵：
 *     草稿   → 详情 / 编辑 / 发布 / 删除
 *     已发布 → 详情 / 编辑 / 弃用（二次确认，提示占用 Agent 数）
 *     已废弃 → 详情 / 重新发布（行灰显）
 * - 编辑跳 /tool/manage/editor/:num；详情走抽屉；复用数可点下钻挂载 Agent 列表
 */
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Empty,
  Input,
  Modal,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import {
  useToolPageQuery,
  useToolDeleteDraftMutation,
  useToolPublishMutation,
  useToolUnpublishMutation,
  useToolRepublishMutation,
} from "@/services/tool";
import { toolApi } from "@/services/tool";
import type {
  ToolCreationMode,
  ToolPageQueryParam,
  ToolStatus,
  ToolType,
  ToolVO,
} from "@/types";
import {
  CREATION_MODE_LABEL,
  TOOL_STATUS_META,
  TOOL_TYPE_META,
} from "../constants";
import ToolDetailDrawer from "./ToolDetailDrawer";
import MountedAgentsModal from "./MountedAgentsModal";
import PermissionGate from "@/components/PermissionGate";
import UserName from "@/components/UserName";

const { Title, Text } = Typography;

const COLOR = {
  border: "#E2E8F0",
  headerBg: "#ffffff",
  textPrimary: "#0F172B",
  textSecondary: "#45556C",
  textMuted: "#90A1B9",
} as const;

export default function ToolListPage() {
  const navigate = useNavigate();

  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [typeTab, setTypeTab] = useState<"ALL" | ToolType>("ALL");
  const [keyword, setKeyword] = useState("");
  const [keywordInput, setKeywordInput] = useState("");

  const [detailNum, setDetailNum] = useState<string | undefined>();
  const [mounted, setMounted] = useState<
    { num: string; name: string } | undefined
  >();

  const query: ToolPageQueryParam = useMemo(
    () => ({
      pageNo,
      pageSize,
      type: typeTab === "ALL" ? undefined : typeTab,
      keyword: keyword || undefined,
    }),
    [pageNo, pageSize, typeTab, keyword],
  );

  const { data: page, isFetching } = useToolPageQuery(query);
  const list = page?.list ?? [];
  const total = page?.total ?? 0;

  const publishMut = useToolPublishMutation();
  const unpublishMut = useToolUnpublishMutation();
  const republishMut = useToolRepublishMutation();
  const deleteMut = useToolDeleteDraftMutation();

  const doSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const handlePublish = async (r: ToolVO) => {
    await publishMut.mutateAsync({ num: r.num });
    message.success("已发布");
  };

  const handleRepublish = async (r: ToolVO) => {
    await republishMut.mutateAsync({ num: r.num });
    message.success("已重新发布");
  };

  const handleUnpublish = async (r: ToolVO) => {
    // 仅 MCP 可被 Agent 挂载；弃用前查实时复用数并在确认文案提示占用 Agent 数（PRD §7.7.7）
    let count = 0;
    if (r.type === "MCP") {
      count = r.reuseCount;
      try {
        count = await toolApi.reuseCount(r.num);
      } catch {
        /* 查询失败用列表值兜底 */
      }
    }
    Modal.confirm({
      title: "弃用工具",
      content:
        count > 0
          ? `${count} 个 Agent 正在使用「${r.name}」，弃用后已挂载的 Agent 仍可调用，但挂载下拉中不再可选。确认弃用？`
          : `确认弃用「${r.name}」？弃用后挂载下拉中不再可选。`,
      okText: "弃用",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        await unpublishMut.mutateAsync({ num: r.num });
        message.success("已弃用");
      },
    });
  };

  const handleDelete = (r: ToolVO) => {
    Modal.confirm({
      title: "删除草稿",
      content: `确认删除草稿「${r.name}」？删除后不可恢复。`,
      okText: "删除",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        await deleteMut.mutateAsync({ num: r.num });
        message.success("已删除");
      },
    });
  };

  const columns: TableColumnsType<ToolVO> = useMemo(
    () => [
      {
        title: "编号",
        dataIndex: "num",
        key: "num",
        width: 200,
        fixed: "left",
        render: (num: string, r: ToolVO) => (
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
        title: "名称",
        dataIndex: "name",
        key: "name",
        width: 160,
        render: (name: string) => (
          <Text style={{ color: COLOR.textPrimary, fontWeight: 500 }}>
            {name}
          </Text>
        ),
      },
      {
        title: "描述",
        dataIndex: "description",
        key: "description",
        width: 140,
        render: (d: string) => (
          <Text
            style={{ color: COLOR.textSecondary, width: "10em" }}
            ellipsis={{ tooltip: d || "—" }}
          >
            {d || "—"}
          </Text>
        ),
      },
      {
        title: "类型",
        dataIndex: "type",
        key: "type",
        width: 130,
        render: (t: ToolType) => {
          const meta = TOOL_TYPE_META[t];
          return (
            <Tag color={meta.color} style={{ background: meta.bg, border: 0 }}>
              {meta.label}
            </Tag>
          );
        },
      },
      {
        title: "创建方式",
        dataIndex: "creationMode",
        key: "creationMode",
        width: 110,
        render: (m: ToolCreationMode) => (
          <span style={{ whiteSpace: "nowrap" }}>
            {CREATION_MODE_LABEL[m] ?? m}
          </span>
        ),
      },
      {
        title: "状态",
        dataIndex: "status",
        key: "status",
        width: 100,
        render: (st: ToolStatus) => {
          const meta = TOOL_STATUS_META[st] ?? {
            color: COLOR.textMuted,
            label: st,
          };
          return (
            <span
              style={{
                color: meta.color,
                fontSize: 12,
                fontWeight: 500,
                whiteSpace: "nowrap",
              }}
            >
              ● {meta.label}
            </span>
          );
        },
      },
      {
        title: "复用",
        dataIndex: "reuseCount",
        key: "reuseCount",
        width: 70,
        render: (c: number, r: ToolVO) => {
          // 仅 MCP 可被 Agent 挂载；FC 工具不展示复用数
          if (r.type !== "MCP") {
            return <Text style={{ color: COLOR.textMuted }}>—</Text>;
          }
          return c > 0 ? (
            <a onClick={() => setMounted({ num: r.num, name: r.name })}>{c}</a>
          ) : (
            <Text style={{ color: COLOR.textMuted }}>0</Text>
          );
        },
      },
      {
        title: "创建人",
        dataIndex: "createNo",
        key: "createNo",
        width: 120,
        render: (no: string) => (
          <UserName
            userNum={no}
            style={{ color: COLOR.textSecondary, whiteSpace: "nowrap" }}
          />
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
        width: 200,
        fixed: "right",
        render: (_: unknown, r: ToolVO) => (
          <Space size={12} wrap>
            <a onClick={() => setDetailNum(r.num)}>详情</a>
            {r.status !== "DEPRECATED" && (
              <PermissionGate anyOf={['tool:update']}>
                <a onClick={() => navigate(`/tool/manage/editor/${r.num}`)}>
                  编辑
                </a>
              </PermissionGate>
            )}
            {r.status === "DRAFT" && (
              <PermissionGate anyOf={['tool:publish']}>
                <a onClick={() => handlePublish(r)}>发布</a>
              </PermissionGate>
            )}
            {r.status === "PUBLISHED" && (
              <PermissionGate anyOf={['tool:publish']}>
                <a
                  style={{ color: "#D97706" }}
                  onClick={() => handleUnpublish(r)}
                >
                  弃用
                </a>
              </PermissionGate>
            )}
            {r.status === "DEPRECATED" && (
              <PermissionGate anyOf={['tool:publish']}>
                <a onClick={() => handleRepublish(r)}>重新发布</a>
              </PermissionGate>
            )}
            {r.status === "DRAFT" && (
              <PermissionGate anyOf={['tool:delete']}>
                <a style={{ color: "#DC2626" }} onClick={() => handleDelete(r)}>
                  删除
                </a>
              </PermissionGate>
            )}
          </Space>
        ),
      },
    ],
    // handler 由稳定 hook 提供，无需额外依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [navigate],
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
            工具管理
          </Title>
          <Text
            style={{
              color: COLOR.textSecondary,
              fontSize: 14,
              marginTop: 4,
              display: "block",
            }}
          >
            纳管 MCP 与 FunctionCall 两类工具；草稿 → 发布 → 弃用 生命周期，MCP 可被 Agent 挂载复用
          </Text>
        </div>
        <PermissionGate anyOf={['tool:create']}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate("/tool/manage/editor/new")}
          >
            新建工具
          </Button>
        </PermissionGate>
      </div>

      {/* 筛选行 */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 16,
          gap: 12,
          flexWrap: "wrap",
        }}
      >
        <Space wrap>
          <Segmented
            value={typeTab}
            onChange={(v) => {
              setTypeTab(v as "ALL" | ToolType);
              setPageNo(1);
            }}
            options={[
              { value: "ALL", label: "全部" },
              { value: "MCP", label: "MCP" },
              { value: "FUNCTION_CALL", label: "FunctionCall" },
            ]}
          />
        </Space>
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder="搜索 编号 / 名称 / 描述…"
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
        <Table<ToolVO>
          rowKey="num"
          columns={columns}
          dataSource={list}
          loading={isFetching}
          size="middle"
          scroll={{ x: 1300 }}
          locale={{
            emptyText: (
              <Empty description="还没有工具" style={{ padding: 32 }} />
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
          rowClassName={(r) =>
            r.status === "DEPRECATED"
              ? "tool-list-row tool-row-deprecated"
              : "tool-list-row"
          }
        />
      </div>

      <ToolDetailDrawer
        num={detailNum}
        open={!!detailNum}
        onClose={() => setDetailNum(undefined)}
      />
      <MountedAgentsModal
        open={!!mounted}
        toolNum={mounted?.num}
        toolName={mounted?.name}
        onClose={() => setMounted(undefined)}
      />

      <style>{`
        .tool-list-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
        }
        .tool-row-deprecated > td { opacity: 0.55; }
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
