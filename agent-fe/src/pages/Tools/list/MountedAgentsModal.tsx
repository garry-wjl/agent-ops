/**
 * 复用数下钻 Modal（PRD §7.8.4）— 列出挂载该工具的已发布 Agent 简表。
 */
import { Modal, Table, Typography } from "antd";
import { useToolMountedAgentsQuery } from "@/services/tool";
import type { AgentBriefVO } from "@/types";

const { Text } = Typography;

interface MountedAgentsModalProps {
  /** 目标工具 num；为空表示关闭 */
  toolNum?: string;
  toolName?: string;
  open: boolean;
  onClose: () => void;
}

export default function MountedAgentsModal({
  toolNum,
  toolName,
  open,
  onClose,
}: MountedAgentsModalProps) {
  const { data: agents = [], isFetching } = useToolMountedAgentsQuery(
    open ? toolNum : undefined,
  );

  return (
    <Modal
      title={`挂载「${toolName ?? "该工具"}」的 Agent`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={520}
      destroyOnClose
    >
      <Table<AgentBriefVO>
        size="small"
        rowKey="num"
        loading={isFetching}
        dataSource={agents}
        pagination={false}
        locale={{ emptyText: "暂无已发布 Agent 挂载该工具" }}
        columns={[
          {
            title: "编号",
            dataIndex: "num",
            width: 180,
            render: (n: string) => (
              <Text
                style={{
                  fontFamily:
                    'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                  fontSize: 13,
                }}
              >
                {n}
              </Text>
            ),
          },
          { title: "名称", dataIndex: "name" },
          { title: "状态", dataIndex: "status", width: 100 },
        ]}
      />
    </Modal>
  );
}
