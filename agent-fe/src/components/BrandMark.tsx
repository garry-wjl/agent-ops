/**
 * AgentOps 品牌标识（几何「轨道 + 核心节点」）
 * 顶栏 / 登录页 / 其它品牌位复用。
 */
type BrandMarkProps = {
  size?: number;
  className?: string;
  title?: string;
};

export default function BrandMark({
  size = 28,
  className,
  title = 'AgentOps',
}: BrandMarkProps) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={title}
    >
      <title>{title}</title>
      <rect width="64" height="64" rx="14" fill="#0B1F3A" />
      <ellipse
        cx="32"
        cy="32"
        rx="20"
        ry="12"
        stroke="#5B8DEF"
        strokeWidth="2.2"
        opacity="0.9"
        transform="rotate(-28 32 32)"
      />
      <circle cx="32" cy="32" r="11" stroke="#2B52D9" strokeWidth="2.4" />
      <circle cx="32" cy="32" r="5" fill="#F8FAFC" />
      <circle cx="48.5" cy="23.5" r="3.2" fill="#5B8DEF" />
      <circle cx="48.5" cy="23.5" r="1.4" fill="#F8FAFC" />
    </svg>
  );
}
