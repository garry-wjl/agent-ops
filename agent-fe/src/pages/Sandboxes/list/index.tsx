/**
 * 沙箱列表页 — `/sandbox/manage`（PRD §8.1 / §7.2 / §7.3）
 *
 * - antd Table（套用 Skill 列表工程风格：COLOR token / `● 状态` 胶囊 / monospace num / uppercase 表头 / bordered 容器）
 * - 顶部「+ 新建沙箱」；右侧关键字搜索（编号 / 名称 / 备注）；分页（默认 20，最大 100）
 * - 新建 / 编辑走右侧抽屉 SandboxFormDrawer；详情走 SandboxDetailDrawer
 * - 行操作按状态矩阵动态渲染（对齐后端 6 个命令接口，无手动「上线」）：
 *     草稿   → 详情 / 编辑 / 提交 / 删除
 *     初始化 → 详情 / 编辑(仅备注)（处理中态，无流转按钮）
 *     在线   → 详情 / 编辑(仅备注) / 下线(二次确认)；删除 disabled + tooltip「请先下线」
 *     下线   → 详情 / 编辑(仅备注) / 重新上线 / 删除
 *     失败   → 详情 / 编辑 / 重新提交 / 删除
 * - 上线由后端 SandboxRunner 监听 SANDBOX_SUBMITTED 异步建容器后自动转 ONLINE，前端无 online 按钮。
 */
import { useMemo, useState } from "react";
import {
  Button,
  Empty,
  Input,
  Modal,
  Space,
  Table,
  Tooltip,
  Typography,
  message,
} from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import {
  useSandboxPageQuery,
  useSandboxDeleteMutation,
  useSandboxSubmitMutation,
  useSandboxOfflineMutation,
  useSandboxReonlineMutation,
} from "@/services/sandbox";
import type {
  SandboxPageQueryParam,
  SandboxStatus,
  SandboxType,
  SandboxVO,
} from "@/types";
import {
  SANDBOX_STATUS_META,
  SANDBOX_TYPE_LABEL,
  isSpecEditable,
} from "../constants";
import SandboxFormDrawer from "./SandboxFormDrawer";
import SandboxDetailDrawer from "./SandboxDetailDrawer";
import PermissionGate from "@/components/PermissionGate";

const { Title, Text } = Typography;

const COLOR = {
  border: "#E2E8F0",
  headerBg: "#F8FAFC",
  textPrimary: "#0F172B",
  textSecondary: "#45556C",
  textMuted: "#90A1B9",
} as const;

export default function SandboxListPage() {
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState<string>("");
  const [keywordInput, setKeywordInput] = useState<string>("");

  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<SandboxVO | undefined>();
  const [detailNum, setDetailNum] = useState<string | undefined>();

  const query: SandboxPageQueryParam = useMemo(
    () => ({
      pageNo,
      pageSize,
      keyword: keyword || undefined,
    }),
    [pageNo, pageSize, keyword],
  );

  const { data: page, isFetching } = useSandboxPageQuery(query);
  const list = page?.list ?? [];
  const total = page?.total ?? 0;

  const submitMut = useSandboxSubmitMutation();
  const offlineMut = useSandboxOfflineMutation();
  const reonlineMut = useSandboxReonlineMutation();
  const deleteMut = useSandboxDeleteMutation();

  const openCreate = () => {
    setEditTarget(undefined);
    setFormOpen(true);
  };
  const openEdit = (r: SandboxVO) => {
    setEditTarget(r);
    setFormOpen(true);
  };

  const doSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const handleSubmit = async (r: SandboxVO) => {
    await submitMut.mutateAsync({ num: r.num });
    message.success("已提交，正在初始化容器…");
  };

  const handleReonline = async (r: SandboxVO) => {
    await reonlineMut.mutateAsync({ num: r.num });
    message.success("已触发重新上线，正在初始化容器…");
  };

  const handleOffline = (r: SandboxVO) => {
    Modal.confirm({
      title: "下线沙箱",
      content: `确认下线「${r.name}」？将停止并释放底层 OpenSandbox 容器。`,
      okText: "下线",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        await offlineMut.mutateAsync({ num: r.num });
        message.success("已下线");
      },
    });
  };

  const handleDelete = (r: SandboxVO) => {
    // 初始化 / 下线态删除会联动释放底层实例，文案提示
    const willReleaseInstance =
      r.status === "OFFLINE" || r.status === "INITIALIZED";
    Modal.confirm({
      title: "删除沙箱",
      content: willReleaseInstance
        ? `确认删除「${r.name}」？将同时释放底层 OpenSandbox 实例。`
        : `确认删除「${r.name}」？`,
      okText: "删除",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        await deleteMut.mutateAsync({ num: r.num });
        message.success("已删除");
      },
    });
  };

  const columns: TableColumnsType<SandboxVO> = useMemo(
    () => [
      {
        title: "编号",
        dataIndex: "num",
        key: "num",
        width: 180,
        fixed: "left",
        render: (num: string, r: SandboxVO) => (
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
        width: 180,
        render: (name: string) => (
          <Text style={{ color: COLOR.textPrimary, fontWeight: 500 }}>
            {name}
          </Text>
        ),
      },
      {
        title: "类型",
        dataIndex: "type",
        key: "type",
        width: 100,
        render: (t: SandboxType) => SANDBOX_TYPE_LABEL[t] ?? t,
      },
      {
        title: "CPU",
        dataIndex: "cpu",
        key: "cpu",
        width: 80,
        render: (c: number) => `${c} 核`,
      },
      {
        title: "内存",
        dataIndex: "memoryMb",
        key: "memoryMb",
        width: 100,
        render: (m: number) => `${m} MB`,
      },
      {
        title: "存活时间",
        dataIndex: "aliveMinutes",
        key: "aliveMinutes",
        width: 100,
        render: (a: number) => `${a} 分钟`,
      },
      {
        title: "状态",
        dataIndex: "status",
        key: "status",
        width: 100,
        render: (st: SandboxStatus) => {
          const meta = SANDBOX_STATUS_META[st] ?? {
            color: COLOR.textMuted,
            label: st ?? "-",
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
        title: "备注",
        dataIndex: "remark",
        key: "remark",
        ellipsis: true,
        render: (remark?: string) => (
          <Text style={{ color: COLOR.textSecondary }}>{remark || "—"}</Text>
        ),
      },
      {
        title: "创建人",
        dataIndex: "createNo",
        key: "createNo",
        width: 110,
        render: (no: string) => (
          <Text style={{ color: COLOR.textSecondary }}>{no || "—"}</Text>
        ),
      },
      {
        title: "更新时间",
        dataIndex: "updateTime",
        key: "updateTime",
        width: 170,
        render: (t: string) => (
          <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>{t}</Text>
        ),
      },
      {
        title: "操作",
        key: "action",
        width: 220,
        fixed: "right",
        render: (_: unknown, r: SandboxVO) => (
          <Space size={12} wrap>
            <a onClick={() => setDetailNum(r.num)}>详情</a>
            <PermissionGate anyOf={['sandbox:update']}>
              <a onClick={() => openEdit(r)}>编辑</a>
            </PermissionGate>
            {isSpecEditable(r.status) && (
              <PermissionGate anyOf={['sandbox:update']}>
                <a onClick={() => handleSubmit(r)}>
                  {r.status === "FAILED" ? "重新提交" : "提交"}
                </a>
              </PermissionGate>
            )}
            {r.status === "ONLINE" && (
              <PermissionGate anyOf={['sandbox:update']}>
                <a onClick={() => handleOffline(r)}>下线</a>
              </PermissionGate>
            )}
            {r.status === "OFFLINE" && (
              <PermissionGate anyOf={['sandbox:update']}>
                <a onClick={() => handleReonline(r)}>重新上线</a>
              </PermissionGate>
            )}
            {r.status === "ONLINE" ? (
              <Tooltip title="请先下线后再删除">
                <span style={{ color: COLOR.textMuted, cursor: "not-allowed" }}>
                  删除
                </span>
              </Tooltip>
            ) : (
              r.status !== "INITIALIZED" && (
                <PermissionGate anyOf={['sandbox:delete']}>
                  <a style={{ color: "#DC2626" }} onClick={() => handleDelete(r)}>
                    删除
                  </a>
                </PermissionGate>
              )
            )}
          </Space>
        ),
      },
    ],
    // 依赖闭包内 handler，handler 由稳定 hook 提供，无需额外依赖
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
            沙箱管理
          </Title>
          <Text
            style={{
              color: COLOR.textSecondary,
              fontSize: 14,
              marginTop: 4,
              display: "block",
            }}
          >
            基于 OpenSandbox 的代码执行沙箱；草稿 → 初始化 → 在线 → 下线 生命周期管理，供 Agent 安全运行代码
          </Text>
        </div>
        <PermissionGate anyOf={['sandbox:create']}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建沙箱
          </Button>
        </PermissionGate>
      </div>

      {/* 筛选行：仅关键字搜索，靠右 */}
      <div
        style={{
          display: "flex",
          justifyContent: "flex-end",
          alignItems: "center",
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder="搜索 编号 / 名称 / 备注…"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onPressEnter={doSearch}
          onBlur={doSearch}
          style={{ width: 300 }}
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
        <Table<SandboxVO>
          rowKey="num"
          columns={columns}
          dataSource={list}
          loading={isFetching}
          size="middle"
          scroll={{ x: 1200 }}
          locale={{
            emptyText: (
              <Empty description="还没有沙箱" style={{ padding: 32 }} />
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
          rowClassName={() => "sandbox-list-row"}
        />
      </div>

      <SandboxFormDrawer
        open={formOpen}
        target={editTarget}
        onClose={() => setFormOpen(false)}
        onSaved={() => setFormOpen(false)}
      />
      <SandboxDetailDrawer
        num={detailNum}
        open={!!detailNum}
        onClose={() => setDetailNum(undefined)}
      />

      <style>{`
        .sandbox-list-row > td {
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
        .ant-table-thead > tr > th::before {
          display: none !important;
        }
      `}</style>
    </div>
  );
}
