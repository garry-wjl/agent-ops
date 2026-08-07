/**
 * 全局认证上下文
 * - 启动时拉取当前用户（JWT Cookie，或本地 disable-auth 注入）
 * - 失败时 currentUser=undefined，由各页面权限兜底
 * - 暴露 logout/refresh
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { Spin } from 'antd';
import { fetchCurrentUser, type CurrentUser } from '@/services/auth';

interface AuthContextValue {
  currentUser?: CurrentUser;
  loading: boolean;
  refresh: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>({
  loading: true,
  refresh: async () => {},
  logout: () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | undefined>();
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const user = await fetchCurrentUser();
      setCurrentUser(user);
    } catch {
      setCurrentUser(undefined);
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    setCurrentUser(undefined);
    window.location.href = '/login';
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  if (loading && !currentUser) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
        }}
      >
        <Spin tip="加载中..." />
      </div>
    );
  }

  return (
    <AuthContext.Provider value={{ currentUser, loading, refresh, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  return useContext(AuthContext);
}

/** 派生权限 hook —— 见 docs/SECURITY.md */
export function useAccess() {
  const { currentUser } = useAuth();
  const role = currentUser?.role ?? 'viewer';
  return {
    role,
    canEdit: role === 'editor' || role === 'admin',
    canAdmin: role === 'admin',
  };
}

/**
 * 权限码判定 hook（RBAC v1.0）。
 * <p>权限源自 {@code GET /api/v1/auth/me} 的 permissions 数组；platform_admin 通过
 * {@code isPlatformAdmin} 短路放行。</p>
 */
export function usePermissions() {
  const { currentUser } = useAuth();
  const isPlatformAdmin = Boolean(currentUser?.isPlatformAdmin);
  const permissions = new Set(currentUser?.permissions ?? []);
  const currentWorkspaceRoles = currentUser?.currentWorkspaceRoles ?? [];

  /** 任一权限码命中即放行；platform_admin 全放行 */
  const hasAny = (...codes: string[]): boolean => {
    if (isPlatformAdmin) return true;
    return codes.some((c) => permissions.has(c));
  };

  /** 全部权限码命中才放行；platform_admin 全放行 */
  const hasAll = (...codes: string[]): boolean => {
    if (isPlatformAdmin) return true;
    return codes.every((c) => permissions.has(c));
  };

  /** 判断是否持有某角色 num */
  const hasRole = (roleNum: string): boolean =>
    currentWorkspaceRoles.includes(roleNum);

  return {
    isPlatformAdmin,
    permissions,
    currentWorkspaceRoles,
    hasAny,
    hasAll,
    hasRole,
  };
}
