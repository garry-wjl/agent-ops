/**
 * 当前活动工作空间上下文
 * - currentWorkspaceNum 持久化到 localStorage（下次登录默认载入）
 * - 切换后所有资产列表（Agent / Skill / 工具）需 refetch —— 由切换器调用方
 *   配合 react-query invalidate 完成
 * - 后端通过请求头 `X-Workspace-Num` 区分空间；header 注入见 services/request.ts
 *
 * 详见技术方案 §4 / §7.6 顶栏空间切换器。
 */
import { create } from 'zustand';

/** localStorage key —— 与 request.ts 读取处保持一致 */
export const WORKSPACE_NUM_KEY = 'currentWorkspaceNum';

/** 请求头名 —— 与 BE WorkspaceContextInterceptor 约定一致 */
export const WORKSPACE_HEADER = 'X-Workspace-Num';

function getInitial(): string | undefined {
  return localStorage.getItem(WORKSPACE_NUM_KEY) ?? undefined;
}

/** 供非 React 上下文（如 axios 拦截器）读取当前空间 num */
export function getCurrentWorkspaceNum(): string | undefined {
  return localStorage.getItem(WORKSPACE_NUM_KEY) ?? undefined;
}

interface WorkspaceState {
  /** 当前活动空间业务编号；无空间时 undefined */
  currentWorkspaceNum?: string;
  /** 切换当前空间（写 store + localStorage） */
  setCurrentWorkspace: (num: string | undefined) => void;
}

export const useWorkspaceStore = create<WorkspaceState>()(set => ({
  currentWorkspaceNum: getInitial(),
  setCurrentWorkspace: num => {
    if (num) {
      localStorage.setItem(WORKSPACE_NUM_KEY, num);
    } else {
      localStorage.removeItem(WORKSPACE_NUM_KEY);
    }
    set({ currentWorkspaceNum: num });
  },
}));
