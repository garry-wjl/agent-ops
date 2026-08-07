import type { IconName } from './registry';
import { FallbackIcon, IconRegistry } from './registry';

/**
 * 图标渲染组件
 * 根据图标名称渲染对应的图标
 */
interface IconRendererProps {
  /** 图标名称（需在 IconRegistry 中注册） */
  name: string;
}

export function IconRenderer({ name }: IconRendererProps) {
  const Icon = IconRegistry[name as IconName];

  if (!Icon) {
    if (import.meta.env.DEV) {
      console.warn(
        `[IconRenderer] 未注册的图标: "${name}"，请在 components/IconRenderer/registry.ts 中添加注册。`
      );
    }
    return <FallbackIcon />;
  }

  return <Icon />;
}
