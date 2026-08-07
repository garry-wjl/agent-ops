import {
  HeartOutlined,
  HomeOutlined,
  QuestionCircleOutlined,
  SettingOutlined,
  SmileOutlined,
  UserOutlined,
} from '@ant-design/icons';

/**
 * 图标注册中心
 * 使用显式命名导入确保 tree-shaking，只打包实际使用的图标
 * 新增图标时在此处添加导入和注册
 */
export const IconRegistry = {
  home: HomeOutlined,
  smile: SmileOutlined,
  heart: HeartOutlined,
  setting: SettingOutlined,
  user: UserOutlined,
} as const;

/** 已注册的图标名称类型 */
export type IconName = keyof typeof IconRegistry;

/** 未注册图标的 fallback */
export const FallbackIcon = QuestionCircleOutlined;
