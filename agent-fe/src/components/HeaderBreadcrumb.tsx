/**
 * 顶栏面包屑
 * - 模块名：根据当前 pathname 在路由表中查最长前缀匹配，取对应路由的 name
 * - 二级片段：根据 pathname 段（editor / compare / detail）拼接，并允许页面通过
 *   useBreadcrumbName 注入实体名
 *
 * 数据来源：
 *   - 模块名表 `MODULE_ROUTES` 与 WorkLayout 侧栏 route 同源（保持顺序无关，按
 *     path 长度倒序匹配），新增模块时两处一起维护
 *   - 实体名走 `useBreadcrumbStore`，避免直接耦合页面数据
 */
import { useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useBreadcrumbStore } from '@/stores/breadcrumb';

interface ModuleRoute {
  /** 列表页路径前缀（与 WorkLayout/router 中的二级路由一一对应） */
  path: string;
  /** 模块名 */
  name: string;
}

/** 模块路径 → 名称。注意：新增二级菜单时此表需同步更新。 */
const MODULE_ROUTES: ModuleRoute[] = [
  { path: '/agent/manage', name: 'Agent 管理' },
  { path: '/agent/debug', name: 'Agent 调试' },
  { path: '/sandbox/manage', name: 'Sandbox 沙箱' },
  { path: '/model/manage', name: '模型管理' },
  { path: '/tool/manage', name: '工具管理' },
  { path: '/skill/manage', name: 'Skill 管理' },
  { path: '/skill/evaluation', name: 'Skill 评测' },
  { path: '/prompt/manage', name: 'Prompt 中心' },
  { path: '/workbench', name: '工作台' },
  { path: '/spaces', name: '工作空间' },
];

/** 路径段 → 子页面名 */
const SUB_PAGE_NAMES: Record<string, string> = {
  editor: '新建',
  compare: '版本对比',
  seeds: '种子集',
};

function matchModule(pathname: string): ModuleRoute | undefined {
  return MODULE_ROUTES.filter(m => pathname.startsWith(m.path)).sort(
    (a, b) => b.path.length - a.path.length,
  )[0];
}

/** 从 pathname 提取子页面类型（基于 `/<editor|compare|detail|seeds>/...` 段） */
function detectSubSegment(
  modulePath: string,
  pathname: string,
): 'editor' | 'detail' | 'compare' | 'seeds' | undefined {
  const tail = pathname.slice(modulePath.length).replace(/^\/+/, '');
  if (!tail) return undefined;
  const first = tail.split('/')[0];
  if (
    first === 'editor' ||
    first === 'detail' ||
    first === 'compare' ||
    first === 'seeds'
  ) {
    return first;
  }
  return undefined;
}

export default function HeaderBreadcrumb() {
  const location = useLocation();
  const entityName = useBreadcrumbStore(s => s.entityName);

  const { module, sub } = useMemo(() => {
    const m = matchModule(location.pathname);
    const s = m ? detectSubSegment(m.path, location.pathname) : undefined;
    return { module: m, sub: s };
  }, [location.pathname]);

  if (!module) return null;

  // 末项节点：实体名优先；新建态用 SUB_PAGE_NAMES['editor']='新建';
  // detail 无实体名时不展示末项；compare 类同
  let trailing: string | undefined;
  if (sub === 'editor') {
    trailing = entityName || SUB_PAGE_NAMES.editor;
  } else if (sub === 'compare') {
    trailing = entityName
      ? `${SUB_PAGE_NAMES.compare} · ${entityName}`
      : SUB_PAGE_NAMES.compare;
  } else if (sub === 'seeds') {
    trailing = SUB_PAGE_NAMES.seeds;
  } else if (sub === 'detail') {
    trailing = entityName;
  }

  return (
    <div className="header-breadcrumb">
      {trailing ? (
        <>
          <Link to={module.path} className="header-breadcrumb-link">
            {module.name}
          </Link>
          <span className="header-breadcrumb-sep">›</span>
          <span className="header-breadcrumb-current">{trailing}</span>
        </>
      ) : (
        <span className="header-breadcrumb-current">{module.name}</span>
      )}
    </div>
  );
}
