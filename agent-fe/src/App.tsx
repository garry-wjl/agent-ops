/**
 * 应用主壳 — 两级布局分发
 * - 全屏路径（登录 / 错误）→ 独立布局
 * - 一级门户（/spaces、/workbench、/system、/permission、/users）→ HomeLayout（工作空间 + 工作台 + 平台级功能）
 * - 其余业务路径 → WorkLayout（进入某工作空间后的工作区，含空间级业务页面）
 */
import { lazy, Suspense } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import HomeLayout from './layouts/HomeLayout';
import WorkLayout from './layouts/WorkLayout';

const LoginPage = lazy(() => import('@/pages/Login'));
const ErrorPage = lazy(() => import('@/pages/Error'));

/** 全屏路径（无侧栏 + 顶栏） */
const FULLSCREEN_PATHS = ['/login', '/403', '/404', '/500'];

/** 一级门户路径前缀（平台级：工作空间 / 工作台 / 系统设置 / 权限管理 / 用户管理） */
const LEVEL1_PREFIXES = ['/spaces', '/workbench', '/system', '/permission', '/users'];

export default function App() {
  const location = useLocation();
  const { pathname } = location;

  // 全屏路径走独立布局
  if (FULLSCREEN_PATHS.includes(pathname)) {
    return (
      <Suspense fallback={null}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/403" element={<ErrorPage code="403" />} />
          <Route path="/404" element={<ErrorPage code="404" />} />
          <Route path="/500" element={<ErrorPage code="500" />} />
        </Routes>
      </Suspense>
    );
  }

  // 一级门户：工作空间 / 工作台 / 权限管理（根路径 / 也走一级门户，由 HomeRoutes 跳到工作台）
  if (
    pathname === '/' ||
    LEVEL1_PREFIXES.some(p => pathname === p || pathname.startsWith(p + '/'))
  ) {
    return <HomeLayout />;
  }

  // 二级工作区：进入某空间后的全部业务页面
  return <WorkLayout />;
}
