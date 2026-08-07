/**
 * 权限门（rbac-aware 子树渲染开关）。
 * <p>用法：</p>
 * <pre>{@code
 *   <PermissionGate anyOf={['agent:create']}>
 *     <Button>新建 Agent</Button>
 *   </PermissionGate>
 * }</pre>
 */
import type { ReactNode } from 'react';
import { usePermissions } from '@/providers/AuthProvider';

interface Props {
  /** 拥有任一权限即放行（与 allOf 二选一；同时传以 anyOf 为准） */
  anyOf?: string[];
  /** 拥有全部权限才放行 */
  allOf?: string[];
  /** 缺权限时渲染的回退节点（默认空） */
  fallback?: ReactNode;
  children: ReactNode;
}

export default function PermissionGate({ anyOf, allOf, fallback = null, children }: Props) {
  const { hasAny, hasAll } = usePermissions();
  const ok = anyOf?.length
    ? hasAny(...anyOf)
    : allOf?.length
      ? hasAll(...allOf)
      : true;
  return <>{ok ? children : fallback}</>;
}
