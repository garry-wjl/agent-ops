/**
 * 一级路由（门户）—— 工作空间 / 工作台 / 系统设置（平台级）/ 权限管理（平台级）
 *
 * 平台级功能（系统模型 / 权限管理）放在一级门户，不依赖工作空间上下文：
 * 系统模型是全平台共享资产，后端走 /api/v1/system/model/*，请求拦截器对该前缀不注入 X-Workspace-Num。
 */
import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Skeleton } from 'antd';

const WorkspaceList = lazy(() => import('@/pages/Workspaces/list'));
const Workbench = lazy(() => import('@/pages/Home/Workbench'));
const SystemModelList = lazy(() => import('@/pages/Models/system'));
const PermissionRoles = lazy(() => import('@/pages/Permission/Roles'));
const PermissionUserRoles = lazy(() => import('@/pages/Permission/UserRoles'));
const UsersPage = lazy(() => import('@/pages/Users'));

const Fallback = () => (
  <div style={{ padding: 24 }}>
    <Skeleton active />
  </div>
);

export default function HomeRoutes() {
  return (
    <Suspense fallback={<Fallback />}>
      <Routes>
        <Route path="/" element={<Navigate to="/workbench" replace />} />
        <Route path="/spaces" element={<WorkspaceList />} />
        <Route path="/workbench" element={<Workbench />} />
        {/* 系统模型管理（平台级，仅 platform_admin；走 /api/v1/system/model/*） */}
        <Route
          path="/system/model"
          element={<Navigate to="/system/model/manage" replace />}
        />
        <Route path="/system/model/manage" element={<SystemModelList />} />
        <Route
          path="/permission"
          element={<Navigate to="/permission/roles" replace />}
        />
        <Route path="/permission/roles" element={<PermissionRoles />} />
        <Route path="/permission/user-roles" element={<PermissionUserRoles />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="*" element={<Navigate to="/spaces" replace />} />
      </Routes>
    </Suspense>
  );
}
