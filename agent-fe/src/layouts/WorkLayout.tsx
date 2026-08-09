/**
 * 二级布局 · 工作区域（WorkLayout）
 * - 从某个工作空间进入后才到达；承载现有全部业务页面（智能体 / Skill / Prompt）
 * - 顶栏：LOGO（左） · 空间切换器+面包屑（中） · 用户信息（右）
 * - 侧栏：group 模式分组菜单（与之前一致）
 * - 未选中空间时回退到一级「工作空间」页（必须先选空间）
 */
import { ProLayout } from "@ant-design/pro-components";
import {
  RobotOutlined,
  AppstoreOutlined,
  BulbOutlined,
  BugOutlined,
  CodeSandboxOutlined,
  ToolOutlined,
  ApiOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import {
  Link,
  Navigate,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { useEffect, useState } from "react";
import { useAuth } from "@/providers/AuthProvider";
import { usePermissions } from "@/providers/AuthProvider";
import { useWorkspaceStore } from "@/stores/workspace";
import HeaderLogo from "@/components/HeaderLogo";
import HeaderNav from "@/components/HeaderNav";
import { buildAvatarProps } from "./avatar";
import AppRoutes from "@/router";

/** 各导航项对应的权限码（任一命中即显示） */
const NAV_PERMS = {
  agentManage:  ['agent:create','agent:read','agent:update','agent:delete','agent:publish','agent:invoke','agent:manage_apikey'],
  sandbox:      ['sandbox:create','sandbox:read','sandbox:update','sandbox:delete'],
  modelManage:  ['model:create','model:read','model:update','model:delete'],
  toolManage:   ['tool:create','tool:read','tool:update','tool:delete','tool:publish'],
  skillManage:  ['skill:create','skill:read','skill:update','skill:delete','skill:publish','skill:sync'],
  promptManage: ['prompt:create','prompt:read','prompt:update','prompt:delete','prompt:publish'],
  debugConsole: ['debug_console:access'],
  roleManage:   ['role_manage:create','role_manage:edit','role_manage:delete'],
} as const;

/**
 * 侧栏菜单 —— group 模式：一级为灰色分组标题，二级为扁平菜单项。
 * 与 HomeLayout 保持相同的"无权限不显示"原则：
 * 子项全部不可见时父分组也不显示。
 */
function buildRoute(hasAny: (...codes: string[]) => boolean) {
  const showAgent   = hasAny(...NAV_PERMS.agentManage);
  const showSandbox = hasAny(...NAV_PERMS.sandbox);
  const showModel   = hasAny(...NAV_PERMS.modelManage);
  const showTool    = hasAny(...NAV_PERMS.toolManage);
  const showSkill   = hasAny(...NAV_PERMS.skillManage);
  const showPrompt  = hasAny(...NAV_PERMS.promptManage);
  const showDebug   = hasAny(...NAV_PERMS.debugConsole);
  const showRole    = hasAny(...NAV_PERMS.roleManage);

  const routes = [];

  // 分组：Agent 与沙箱
  const runtimeChildren = [];
  if (showAgent)   runtimeChildren.push({ path: '/agent/manage',   name: 'Agent 管理',   icon: <RobotOutlined /> });
  if (showSandbox) runtimeChildren.push({ path: '/sandbox/manage', name: 'Sandbox 沙箱', icon: <CodeSandboxOutlined /> });
  if (runtimeChildren.length) routes.push({ path: '/group-runtime', name: 'Agent与沙箱', routes: runtimeChildren });

  // 分组：模型与工具
  const modelToolChildren = [];
  if (showModel)  modelToolChildren.push({ path: '/model/manage',  name: '模型管理',   icon: <ApiOutlined /> });
  if (showTool)   modelToolChildren.push({ path: '/tool/manage',   name: '工具管理',   icon: <ToolOutlined /> });
  if (showSkill)  modelToolChildren.push({ path: '/skill/manage',  name: 'Skill 管理', icon: <AppstoreOutlined /> });
  if (showPrompt) modelToolChildren.push({ path: '/prompt/manage', name: 'Prompt 中心', icon: <BulbOutlined /> });
  if (modelToolChildren.length) routes.push({ path: '/group-model-tool', name: '模型与工具', routes: modelToolChildren });

  // 分组：调试与评测
  if (showDebug) routes.push({ path: '/group-debug-eval', name: '调试与评测', routes: [
    { path: '/agent/debug', name: 'Agent 调试', icon: <BugOutlined /> },
  ]});

  // 分组：权限与设置
  const settingsChildren = [];
  if (showRole) settingsChildren.push({ path: '/role/manage', name: '角色管理', icon: <SafetyCertificateOutlined /> });
  if (settingsChildren.length) routes.push({ path: '/group-settings', name: '权限与设置', routes: settingsChildren });

  return { path: '/', routes };
}

export default function WorkLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [collapsed, setCollapsed] = useState(false);
  const { currentUser, logout, refresh } = useAuth();
  const { hasAny } = usePermissions();
  const currentWorkspaceNum = useWorkspaceStore((s) => s.currentWorkspaceNum);
  const setCurrentWorkspace = useWorkspaceStore((s) => s.setCurrentWorkspace);

  const urlWs = searchParams.get("ws") || undefined;

  // URL 的空间编号 ⇄ 当前空间 双向同步
  useEffect(() => {
    if (urlWs && urlWs !== currentWorkspaceNum) {
      setCurrentWorkspace(urlWs);
      return;
    }
    if (!urlWs && currentWorkspaceNum) {
      const sp = new URLSearchParams(location.search);
      sp.set("ws", currentWorkspaceNum);
      navigate(`${location.pathname}?${sp.toString()}`, { replace: true });
    }
  }, [
    urlWs,
    currentWorkspaceNum,
    location.pathname,
    location.search,
    navigate,
    setCurrentWorkspace,
  ]);

  // 空间切换后重新拉取 /api/v1/auth/me，获取该空间下的权限并集
  // （request.ts 已移除 /api/v1/auth 的跳过规则，/auth/me 会自动带上 X-Workspace-Num）
  useEffect(() => {
    if (currentWorkspaceNum) {
      refresh();
    }
  }, [currentWorkspaceNum, refresh]);

  // 既没选空间、URL 也没带 → 回一级「工作空间」选择页
  if (!currentWorkspaceNum && !urlWs) {
    return <Navigate to="/spaces" replace />;
  }

  const route = buildRoute(hasAny);

  return (
    <ProLayout
      title={false}
      logo={false}
      layout="mix"
      siderWidth={220}
      fixedHeader
      fixSiderbar
      navTheme="light"
      contentStyle={{ padding: 0, background: "#ffffff" }}
      route={route}
      menu={{ type: "group" }}
      splitMenus={false}
      collapsed={collapsed}
      onCollapse={setCollapsed}
      location={{ pathname: location.pathname }}
      menuHeaderRender={false}
      headerTitleRender={() => <HeaderLogo />}
      headerContentRender={() => (
        <HeaderNav key={currentWorkspaceNum ?? urlWs ?? "ws"} />
      )}
      breadcrumbRender={false}
      menuItemRender={(item, dom) => {
        if (item.disabled) return dom;
        return <Link to={item.path ?? "/"}>{dom}</Link>;
      }}
      avatarProps={buildAvatarProps(currentUser, logout)}
      footerRender={false}
    >
      {/* 按当前空间编号 key，切换空间时整块重挂载，触发当前页重新拉数据 */}
      <AppRoutes key={currentWorkspaceNum} />
    </ProLayout>
  );
}
