/**
 * 工作空间切换器（顶栏版）
 * - 顶栏中间区，仅在 WorkLayout 使用（HomeLayout 不展示）
 * - 触发态：当前空间名 + 下拉箭头
 * - 下拉内容：当前空间列表（打勾标识当前）+ 底部「返回工作空间」入口
 * - 切换空间 → 写 store + 同步 URL `?ws=` + 失效相关查询
 * - 折叠态（侧栏收起）由父组件 HeaderNav 控制是否渲染
 */
import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Divider, Dropdown } from 'antd';
import { CheckOutlined, DownOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useWorkspaceListQuery } from '@/services/workspace';
import { useWorkspaceStore } from '@/stores/workspace';

/** 由名称生成稳定的柔和底色（与旧版保持一致） */
const PALETTE = [
  '#2B52D9',
  '#0E9F6E',
  '#7C3AED',
  '#D97706',
  '#DB2777',
  '#0891B2',
];
function colorOf(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

export default function WorkspaceSwitcher() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { data: list = [] } = useWorkspaceListQuery();
  const currentWorkspaceNum = useWorkspaceStore(s => s.currentWorkspaceNum);
  const setCurrentWorkspace = useWorkspaceStore(s => s.setCurrentWorkspace);

  const current = useMemo(
    () => list.find(w => w.num === currentWorkspaceNum),
    [list, currentWorkspaceNum],
  );

  const switchTo = (num: string) => {
    if (num === currentWorkspaceNum) return;
    setCurrentWorkspace(num);
    const sp = new URLSearchParams(location.search);
    sp.set('ws', num);
    navigate(`${location.pathname}?${sp.toString()}`, { replace: true });
    queryClient.invalidateQueries({ queryKey: ['agent'] });
    queryClient.invalidateQueries({ queryKey: ['skill'] });
  };

  const items: MenuProps['items'] = [
    {
      key: '__title__',
      type: 'group',
      label: <span style={{ fontSize: 12, color: '#94A3B8' }}>切换空间</span>,
      children: list.map(w => ({
        key: w.num,
        label: (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              minWidth: 208,
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Square name={w.name} size={20} />
              <span>{w.name}</span>
            </span>
            {w.num === currentWorkspaceNum && (
              <CheckOutlined style={{ color: '#2B52D9' }} />
            )}
          </div>
        ),
        onClick: () => switchTo(w.num),
      })),
    },
    { type: 'divider' as const },
    {
      key: '__back__',
      label: (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <ArrowLeftOutlined />
          返回工作空间
        </span>
      ),
      onClick: () => navigate('/spaces'),
    },
  ];
  // Divider 在某些 antd 版本下不再支持顶层 type:'divider'，保留兼容写法
  void Divider;

  return (
    <Dropdown menu={{ items }} trigger={['click']} placement="bottomLeft">
      <span className="header-ws-switcher">
        {current && <Square name={current.name} size={18} />}
        <span className="header-ws-name" title={current?.name}>
          {current?.name ?? '选择空间'}
        </span>
        <DownOutlined className="header-ws-caret" />
      </span>
    </Dropdown>
  );
}

/** 名称首字方块 */
function Square({ name, size }: { name: string; size: number }) {
  const ch = name.trim().charAt(0) || '空';
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: 5,
        flexShrink: 0,
        background: colorOf(name),
        color: '#fff',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: size <= 20 ? 11 : 14,
        fontWeight: 600,
      }}
    >
      {ch}
    </span>
  );
}
