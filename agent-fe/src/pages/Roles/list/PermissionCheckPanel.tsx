/**
 * 权限勾选面板（按资源域分组的可勾选 Tree）。
 * <p>支持只读模式（detail 抽屉）与可编辑模式（编辑/新建抽屉）。</p>
 */
import { useMemo } from 'react';
import { Tree, Typography, Empty, Spin } from 'antd';
import type { TreeProps } from 'antd';
import { usePermissionsQuery } from '@/services/authz';

const { Text } = Typography;

interface Props {
  /** 当前已勾选的权限码集合（受控） */
  value?: string[];
  /** 受控回调；只读模式不传 */
  onChange?: (codes: string[]) => void;
  /** 只读模式（detail 抽屉） */
  readOnly?: boolean;
  height?: number;
  /**
   * 是否允许请求（由父组件控制，仅在 Drawer 打开时拉取）。
   * 缺省时始终拉取（兼容旧调用方）。
   */
  enabled?: boolean;
  /**
   * 按角色 scope 过滤可选权限：
   * - PLATFORM：仅返回平台域（空间管理 + 权限管理）
   * - SPACE：仅返回空间业务资产域
   * - 缺省：全集（兼容旧调用方）
   */
  scope?: 'PLATFORM' | 'SPACE';
}

export default function PermissionCheckPanel({
  value,
  onChange,
  readOnly,
  height = 480,
  scope,
  enabled = true,
}: Props) {
  const { data: groups = [], isLoading } = usePermissionsQuery(enabled ? scope : undefined, enabled);

  const treeData: NonNullable<TreeProps['treeData']> = useMemo(() => {
    return groups.map((g) => ({
      key: `domain:${g.resourceDomain}`,
      title: (
        <span>
          <Text strong>{g.resourceDomainName}</Text>
          <Text type="secondary" style={{ marginLeft: 8 }}>
            {g.permissions.length}
          </Text>
        </span>
      ),
      selectable: false,
      children: g.permissions.map((p) => ({
        key: p.code,
        title: (
          <span>
            <Text>{p.name}</Text>
            <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
              {p.code}
            </Text>
            {p.description ? (
              <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                · {p.description}
              </Text>
            ) : null}
          </span>
        ),
      })),
    }));
  }, [groups]);

  const allLeafCodes = useMemo(
    () => groups.flatMap((g) => g.permissions.map((p) => p.code)),
    [groups],
  );

  if (isLoading) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (!groups.length) {
    return <Empty description="暂无可分配权限" />;
  }

  return (
    <div style={{ maxHeight: height, overflow: 'auto', padding: 4 }}>
      <Tree
        checkable
        disabled={readOnly}
        defaultExpandAll
        selectable={false}
        treeData={treeData}
        checkedKeys={value ?? []}
        onCheck={(checked) => {
          if (readOnly) return;
          const keys = Array.isArray(checked) ? checked : checked.checked;
          // 仅保留叶子权限码（去掉 domain:* 分组节点）
          const leaf = (keys as string[]).filter((k) => allLeafCodes.includes(k));
          onChange?.(leaf);
        }}
      />
    </div>
  );
}
