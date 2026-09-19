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
  Typography,
  message,
} from "antd";
import type { TableColumnsType } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import {
  useSandboxPageQuery,
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
} from "../constants";
import SandboxDetailDrawer from "./SandboxDetailDrawer";
import UserName from "@/components/UserName";

const { Title, Text } = Typography;

const COLOR = {
  border: "#E2E8F0",
  headerBg: "#ffffff",
  textPrimary: "#0F172B",
  textSecondary: "#45556C",
  textMuted: "#90A1B9",
} as const;

export default function SandboxListPage() {
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState<string>("");
  const [keywordInput, setKeywordInput] = useState<string>("");

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

  const doSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
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
        width: 120,
        render: (no: string) => (
          <UserName userNum={no} style={{ color: COLOR.textSecondary }} />
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
          </Space>
        ),
      },
    ],
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
            只读查看 Agent 独占的沙箱规格；请在 Agent 编辑页维护。真正容器在创建会话时启动。
          </Text>
        </div>
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
