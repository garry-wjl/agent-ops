/**
 * 顶栏中间区组合：仅展示空间切换器
 * - 仅 WorkLayout 使用
 * - 面包屑已下沉到页面内（顶栏只保留空间切换，避免上下文重复）
 */
import WorkspaceSwitcher from './WorkspaceSwitcher';
import './HeaderNav.less';

export default function HeaderNav() {
  return (
    <div className="header-nav">
      <WorkspaceSwitcher />
    </div>
  );
}
