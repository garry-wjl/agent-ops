/**
 * 用户编号 → 用户名展示组件。
 * 用于审计字段 createNo / updateNo 等；加载中短暂显示原值，避免闪空。
 */
import type { CSSProperties } from 'react';
import { Typography } from 'antd';
import {
  resolveUsername,
  useUserUsernameMap,
} from '@/hooks/useUserUsernameMap';

const { Text } = Typography;

interface UserNameProps {
  /** 用户业务编号（USR-…）或历史遗留的 username */
  userNum?: string | null;
  style?: CSSProperties;
  className?: string;
  strong?: boolean;
}

export default function UserName({
  userNum,
  style,
  className,
  strong,
}: UserNameProps) {
  const { data: map } = useUserUsernameMap();
  const label = resolveUsername(map, userNum);
  return (
    <Text strong={strong} className={className} style={style}>
      {label}
    </Text>
  );
}
