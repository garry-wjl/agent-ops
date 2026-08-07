/**
 * 顶栏左侧品牌 LOGO 区
 * - 一律点击跳转 `/workbench`（空间内点击等同退出当前空间到一级门户）
 * - HomeLayout / WorkLayout 共用，统一视觉
 */
import { useNavigate } from 'react-router-dom';
import { APP_BRAND } from '@/layouts/avatar';

export default function HeaderLogo() {
  const navigate = useNavigate();
  return (
    <div className="header-logo" onClick={() => navigate('/workbench')}>
      <span className="header-logo-icon">🛰️</span>
      <span className="header-logo-text">{APP_BRAND}</span>
    </div>
  );
}
