/**
 * 顶栏右侧用户头像 + 下拉退出
 * - 两级布局（HomeLayout / WorkLayout）共用
 * - 在 ProLayout `layout="mix"` 模式下作为 `avatarProps.render` 返回内容
 */
import { Dropdown } from 'antd';
import { LogoutOutlined } from '@ant-design/icons';
import type { CurrentUser } from '@/services/auth';

export function buildAvatarProps(
  currentUser: CurrentUser | undefined,
  onLogout: () => void,
) {
  const name = currentUser?.userName ?? '未登录';
  const initial = name.charAt(0);
  const displayInitial = /[a-zA-Z]/.test(initial)
    ? initial.toLowerCase()
    : initial;

  return {
    src: currentUser?.avatar,
    size: 'small' as const,
    title: name,
    render: () => (
      <Dropdown
        menu={{
          items: [
            {
              key: 'logout',
              icon: <LogoutOutlined />,
              label: '退出登录',
              onClick: onLogout,
            },
          ],
        }}
        placement="bottomRight"
      >
        <div className="header-user">
          <div className="header-user-avatar">{displayInitial}</div>
          <span className="header-user-name">{name}</span>
        </div>
      </Dropdown>
    ),
  };
}

/** 品牌名 —— 统一在此维护 */
export const APP_BRAND = 'AgentOps';
