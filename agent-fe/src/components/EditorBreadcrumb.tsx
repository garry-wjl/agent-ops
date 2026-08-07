/**
 * 创建/编辑/详情页的页头：面包屑（左）+ 操作按钮（右）同行水平对齐
 * - 取代原各页面顶部的「← 返回 + 标题」操作栏
 * - 面包屑：`<模块名>` › `<当前页>`，模块名可点击跳列表
 * - 操作按钮区：可放任意 ReactNode（保存草稿 / 发布 / portal 容器 等）
 */
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { RightOutlined } from '@ant-design/icons';

interface EditorBreadcrumbProps {
  /** 列表页路径，如 `/agent/manage`；点击模块名跳转此地址 */
  listPath: string;
  /** 模块名，如 `Agent 管理` */
  moduleName: string;
  /** 当前页文案，如 `新建 Agent · 配置模式` / `编辑 Skill · 示例 Skill` */
  current: string;
  /** 右侧操作区（按钮组、A2A portal 容器等） */
  actions?: ReactNode;
}

const TXT_MUTED = '#90A1B9';
const TXT_SECONDARY = '#45556C';

export default function EditorBreadcrumb({
  listPath,
  moduleName,
  current,
  actions,
}: EditorBreadcrumbProps) {
  const navigate = useNavigate();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 12,
          color: TXT_MUTED,
        }}
      >
        <a
          onClick={() => navigate(listPath)}
          style={{ color: TXT_MUTED, cursor: 'pointer' }}
        >
          {moduleName}
        </a>
        <RightOutlined style={{ fontSize: 9 }} />
        <span style={{ color: TXT_SECONDARY }}>{current}</span>
      </div>
      {actions ? <div style={{ display: 'flex', gap: 8 }}>{actions}</div> : null}
    </div>
  );
}
