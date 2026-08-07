/**
 * 注入当前页面的「面包屑实体名」到全局 store
 *
 * 用法：
 *   useBreadcrumbName(agent?.name);   // 编辑/详情页
 *   useBreadcrumbName('新建');         // 固定标识
 *
 * 传入空串/undefined 表示当前页没有实体名（如列表页），将清空 store。
 * 组件卸载时自动清理，避免路由切换后旧值残留。
 */
import { useEffect } from 'react';
import { useBreadcrumbStore } from '@/stores/breadcrumb';

export function useBreadcrumbName(name?: string) {
  const setEntity = useBreadcrumbStore(s => s.setEntity);
  useEffect(() => {
    setEntity(name && name.trim() ? name : undefined);
    return () => setEntity(undefined);
  }, [name, setEntity]);
}
