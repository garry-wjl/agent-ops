/**
 * 沙箱详情抽屉（PRD §8.3 简化版）
 *
 * 只读展示沙箱全字段 + 当前状态胶囊。状态时间线后端本期无数据，暂不展示（PRD 标 P1/M2）。
 * 数据经 useSandboxDetailQuery（GET /api/v1/sandbox/detail）拉取。
 */
import { Descriptions, Drawer, Spin } from "antd";
import { useSandboxDetailQuery } from "@/services/sandbox";
import { SANDBOX_STATUS_META, SANDBOX_TYPE_LABEL } from "../constants";

interface SandboxDetailDrawerProps {
  /** 目标沙箱业务编号；为空表示抽屉关闭 */
  num?: string;
  open: boolean;
  onClose: () => void;
}

export default function SandboxDetailDrawer({
  num,
  open,
  onClose,
}: SandboxDetailDrawerProps) {
  const { data, isLoading } = useSandboxDetailQuery(open ? num : undefined);
  const sandbox = data?.sandbox;

  return (
    <Drawer
      title="沙箱详情"
      width={520}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      {isLoading || !sandbox ? (
        <div style={{ textAlign: "center", padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <Descriptions column={1} bordered size="middle">
          <Descriptions.Item label="编号">{sandbox.num}</Descriptions.Item>
          <Descriptions.Item label="名称">{sandbox.name}</Descriptions.Item>
          <Descriptions.Item label="类型">
            {SANDBOX_TYPE_LABEL[sandbox.type] ?? sandbox.type}
          </Descriptions.Item>
          <Descriptions.Item label="CPU">{sandbox.cpu} 核</Descriptions.Item>
          <Descriptions.Item label="内存">
            {sandbox.memoryMb} MB
          </Descriptions.Item>
          <Descriptions.Item label="存活时间">
            {sandbox.aliveMinutes} 分钟
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <StatusPill status={sandbox.status} />
          </Descriptions.Item>
          <Descriptions.Item label="实例 ID">
            {sandbox.sandboxInstanceId || "—"}
          </Descriptions.Item>
          <Descriptions.Item label="备注">
            {sandbox.remark || "—"}
          </Descriptions.Item>
          <Descriptions.Item label="创建人">
            {sandbox.createNo}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {sandbox.createTime}
          </Descriptions.Item>
          <Descriptions.Item label="更新人">
            {sandbox.updateNo}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {sandbox.updateTime}
          </Descriptions.Item>
        </Descriptions>
      )}
    </Drawer>
  );
}

/** 状态胶囊（`● 标签`，色彩取自 SANDBOX_STATUS_META）。 */
function StatusPill({ status }: { status: keyof typeof SANDBOX_STATUS_META }) {
  const meta = SANDBOX_STATUS_META[status] ?? {
    label: status,
    color: "#90A1B9",
  };
  return (
    <span style={{ color: meta.color, fontWeight: 500 }}>● {meta.label}</span>
  );
}
