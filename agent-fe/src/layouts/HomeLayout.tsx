/**
 * 一级布局 · 门户（HomeLayout）
 * - 用户登录后进入；菜单：「工作台」「工作空间」+ 权限管理（仅 platform_admin）
 * - 从「工作空间」点进某个空间后才进入二级工作区（WorkLayout）
 * - 布局模式：ProLayout mix（顶栏 + 侧栏），LOGO 与用户信息在顶栏，
 *   侧栏只放菜单
 *
 * 菜单可见规则（"无权限不显示"原则）：
 * - platform_admin：全量显示所有平台级菜单
 * - 非 admin 用户：按实际持有的权限码细粒度过滤
 *   · 系统设置/模型管理 → 任一 system:model_* 权限
 *   · 权限管理/角色管理 → 任一 role_manage:* 权限
 *   · 权限管理/用户角色 → 任一 user_role:* 权限
 *   · 父级分组仅在至少一个子项可见时显示
 */
import { ProLayout } from '@ant-design/pro-components';
import {
  AppstoreOutlined,
  DashboardOutlined,
  SafetyCertificateOutlined,
  CrownOutlined,
  TeamOutlined,
  SettingOutlined,
  ApiOutlined,
} from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/providers/AuthProvider';
import { usePermissions } from '@/providers/AuthProvider';
import { APP_BRAND, buildAvatarProps } from './avatar';
import HeaderLogo from '@/components/HeaderLogo';
import HomeRoutes from '@/router/home';
import '@/components/HeaderNav.less';

/** system 域：系统设置 → 模型管理 */
const SYSTEM_MODEL_CODES = [
  'system:model_create',
  'system:model_read',
  'system:model_update',
  'system:model_delete',
] as const;

/** role_manage 域：权限管理 → 角色管理 */
const ROLE_MANAGE_CODES = [
  'role_manage:create',
  'role_manage:edit',
  'role_manage:delete',
] as const;

/** user_role 域：权限管理 → 用户角色 */
const USER_ROLE_CODES = [
  'user_role:assign',
  'user_role:edit',
  'user_role:remove',
] as const;

function buildRoute(
  hasAny: (...codes: string[]) => boolean,
) {
  const base = [
    { path: '/workbench', name: '工作台', icon: <DashboardOutlined /> },
    { path: '/spaces', name: '工作空间', icon: <AppstoreOutlined /> },
  ];

  // 各子菜单可见性
  const showModelManage = hasAny(...SYSTEM_MODEL_CODES);
  const showRoleManage  = hasAny(...ROLE_MANAGE_CODES);
  const showUserRole    = hasAny(...USER_ROLE_CODES);

  // 父级分组：至少一个子项可见才展示
  const showSystem     = showModelManage;
  const showPermission = showRoleManage || showUserRole;

  if (!showSystem && !showPermission) {
    return { path: '/', routes: base };
  }

  const extra = [];

  if (showSystem) {
    const systemChildren = [];
    if (showModelManage) {
      systemChildren.push({
        path: '/system/model/manage',
        name: '模型管理',
        icon: <ApiOutlined />,
      });
    }
    extra.push({
      path: '/system',
      name: '系统设置',
      icon: <SettingOutlined />,
      routes: systemChildren,
    });
  }

  if (showPermission) {
    const permChildren = [];
    if (showRoleManage) {
      permChildren.push({
        path: '/permission/roles',
        name: '角色管理',
        icon: <CrownOutlined />,
      });
    }
    if (showUserRole) {
      permChildren.push({
        path: '/permission/user-roles',
        name: '用户角色',
        icon: <TeamOutlined />,
      });
    }
    extra.push({
      path: '/permission',
      name: '权限管理',
      icon: <SafetyCertificateOutlined />,
      routes: permChildren,
    });
  }

  return { path: '/', routes: [...base, ...extra] };
}

export default function HomeLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { currentUser, logout } = useAuth();
  const { hasAny } = usePermissions();

  const route = buildRoute(hasAny);

  return (
    <ProLayout
      title={APP_BRAND}
      logo={false}
      layout="mix"
      siderWidth={220}
      fixedHeader
      fixSiderbar
      navTheme="light"
      contentStyle={{ padding: 0, background: '#F5F7FA' }}
      route={route}
      location={{ pathname: location.pathname }}
      splitMenus={false}
      menuHeaderRender={false}
      headerTitleRender={() => <HeaderLogo />}
      headerContentRender={() => null}
      breadcrumbRender={false}
      menuItemRender={(item, dom) => <Link to={item.path ?? '/'}>{dom}</Link>}
      avatarProps={buildAvatarProps(currentUser, () => {
        logout();
        navigate('/login', { replace: true });
      })}
      footerRender={false}
    >
      <HomeRoutes />
    </ProLayout>
  );
}
