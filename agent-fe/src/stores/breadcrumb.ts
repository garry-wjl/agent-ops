/**
 * 面包屑实体名注入 store
 * - moduleName 由 HeaderBreadcrumb 自己从路由 name 派生，不写入 store
 * - entityName 由具体页面（编辑/详情/对比等）通过 useBreadcrumbName 注入
 * - 路由切换时由 useBreadcrumbName 的 cleanup 自动清空，避免脏数据
 */
import { create } from 'zustand';

interface BreadcrumbState {
  /** 实体名（如 "客服助手 Agent"），仅编辑/详情/对比页注入 */
  entityName?: string;
  setEntity: (name?: string) => void;
}

export const useBreadcrumbStore = create<BreadcrumbState>(set => ({
  entityName: undefined,
  setEntity: name => set({ entityName: name }),
}));
