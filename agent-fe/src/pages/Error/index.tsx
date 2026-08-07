/**
 * 错误页 — `/403` `/404` `/500`
 * 像素级还原 Figma 节点 86:25 / 86:35 / 86:45（AgentSphere · Error States）
 * 视觉：白底，居中卡片（白底 + #E2E8F0 边框 + r14），顶部 96×96 圆形错误码 + 标题 + 说明 + 双按钮
 */
import { useNavigate, useParams } from 'react-router-dom';
import type { ReactNode } from 'react';

const COLOR = {
  pageBg: '#FFFFFF',
  cardBg: '#FFFFFF',
  cardBorder: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  // 403
  warnBg: '#FEF3C7',
  warnText: '#92400E',
  // 404
  infoBg: '#EFF6FF',
  infoText: '#2B52D9',
  // 5xx
  errorBg: '#FEE2E2',
  errorText: '#DC2626',
} as const;

type ErrorCode = '403' | '404' | '500';

const CFG: Record<
  ErrorCode,
  {
    code: string;
    bg: string;
    color: string;
    title: string;
    desc: ReactNode;
    primaryAction: { label: string; bg: string };
  }
> = {
  '403': {
    code: '403',
    bg: COLOR.warnBg,
    color: COLOR.warnText,
    title: '权限不足',
    desc: (
      <>
        当前角色（viewer）无法执行该操作。
        <br />
        如需写权限，请联系管理员将你提升为 editor。
      </>
    ),
    primaryAction: { label: '联系管理员', bg: COLOR.primary },
  },
  '404': {
    code: '404',
    bg: COLOR.infoBg,
    color: COLOR.infoText,
    title: '页面不存在',
    desc: (
      <>
        你访问的资源已被删除或从未存在。
        <br />
        请检查链接，或返回首页继续。
      </>
    ),
    primaryAction: { label: '返回首页', bg: COLOR.primary },
  },
  '500': {
    code: '5××',
    bg: COLOR.errorBg,
    color: COLOR.errorText,
    title: '服务异常',
    desc: (
      <>
        后端临时不可用。可能是 rd-agent-be 重启或下游 sphere 故障。
        <br />
        可重试或查看运维公告。
      </>
    ),
    primaryAction: { label: '重试', bg: COLOR.errorText },
  },
};

interface ErrorPageProps {
  /** 显式指定错误码（不依赖路由参数）；优先级高于 useParams */
  code?: ErrorCode;
}

export default function ErrorPage(props: ErrorPageProps) {
  const navigate = useNavigate();
  const params = useParams();
  const code: ErrorCode = props.code ?? (params.code as ErrorCode) ?? '404';
  const cfg = CFG[code] ?? CFG['404'];

  const handlePrimary = () => {
    if (code === '403') {
      window.alert('请联系：rd-agent-platform@garry.com');
    } else if (code === '500') {
      window.location.reload();
    } else {
      navigate('/agent/manage');
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: COLOR.pageBg,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      <div
        style={{
          width: 420,
          background: COLOR.cardBg,
          border: `1px solid ${COLOR.cardBorder}`,
          borderRadius: 14,
          padding: '40px 32px 32px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 20,
        }}
      >
        {/* 圆形错误码 */}
        <div
          style={{
            width: 96,
            height: 96,
            borderRadius: '50%',
            background: cfg.bg,
            color: cfg.color,
            fontSize: 36,
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {cfg.code}
        </div>

        {/* 标题 */}
        <div
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: COLOR.textPrimary,
          }}
        >
          {cfg.title}
        </div>

        {/* 说明 */}
        <div
          style={{
            fontSize: 13,
            fontWeight: 400,
            color: COLOR.textSecondary,
            textAlign: 'center',
            lineHeight: 1.6,
          }}
        >
          {cfg.desc}
        </div>

        {/* 主按钮 */}
        <button
          onClick={handlePrimary}
          style={{
            width: '100%',
            background: cfg.primaryAction.bg,
            border: 'none',
            borderRadius: 8,
            padding: '10px 16px',
            color: '#fff',
            fontSize: 14,
            fontWeight: 500,
            cursor: 'pointer',
            transition: 'opacity 0.15s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.9')}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
        >
          {cfg.primaryAction.label}
        </button>

        {/* 文字链 */}
        <a
          onClick={(e) => {
            e.preventDefault();
            navigate('/agent/manage');
          }}
          style={{
            fontSize: 13,
            color: COLOR.textMuted,
            cursor: 'pointer',
          }}
        >
          回到首页
        </a>
      </div>
    </div>
  );
}
