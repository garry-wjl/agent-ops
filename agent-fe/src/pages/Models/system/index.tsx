/**
 * 系统模型管理页 — 包装 ModelListPage,固定 scope=PLATFORM
 * (2026-06-17 scope 优化;平台管理员入口,走 /api/v1/system/model/*)
 */
import ModelListPage from '../list';

export default function SystemModelListPage() {
  return <ModelListPage scope='PLATFORM' />;
}
