/**
 * 登录页 — `/login`
 * SSO/GCAC 已移除。本地 disable-auth 时点「进入系统」会 refresh 当前用户并跳转业务页。
 */
import { ArrowRightOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/providers/AuthProvider';
import BrandMark from '@/components/BrandMark';
import { APP_BRAND } from '@/layouts/avatar';
import { useEffect, useState } from 'react';

const COLOR = {
  pageBg: '#F8FAFC',
  cardBg: '#FFFFFF',
  cardBorder: '#E2E8F0',
  divider: '#F1F5F9',
  textPrimary: '#0F172B',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
} as const;

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, refresh } = useAuth();
  const [entering, setEntering] = useState(false);

  // 已登录则按 ?from 参数跳回，否则跳首页
  // 开发预览：加 ?stay=1 强制停留在登录页
  useEffect(() => {
    if (!currentUser) return;
    const params = new URLSearchParams(location.search);
    if (params.get('stay') === '1') return;
    const from = params.get('from');
    navigate(from && from.startsWith('/') ? from : '/spaces', {
      replace: true,
    });
  }, [currentUser, location.search, navigate]);

  const isPreview =
    new URLSearchParams(location.search).get('stay') === '1';

  const handleEnter = async () => {
    setEntering(true);
    try {
      await refresh();
      const params = new URLSearchParams(location.search);
      const from = params.get('from');
      navigate(from && from.startsWith('/') ? from : '/spaces', {
        replace: true,
      });
    } finally {
      setEntering(false);
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
          width: 480,
          background: COLOR.cardBg,
          border: `1px solid ${COLOR.cardBorder}`,
          borderRadius: 14,
          padding: '48px 56px 40px',
          boxShadow: '0 4px 16px rgba(0, 0, 0, 0.06)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 24,
        }}
      >
        {isPreview ? (
          <div
            style={{
              fontSize: 11,
              color: COLOR.textMuted,
              background: '#FEF3C7',
              border: '1px solid #FDE68A',
              borderRadius: 4,
              padding: '4px 10px',
              alignSelf: 'stretch',
              textAlign: 'center',
            }}
          >
            预览模式（?stay=1）— 已登录用户正常访问会自动 redirect
          </div>
        ) : null}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <BrandMark size={56} />
          <div
            style={{
              fontSize: 24,
              fontWeight: 700,
              color: COLOR.textPrimary,
            }}
          >
            {APP_BRAND}
          </div>
          <div
            style={{
              fontSize: 14,
              fontWeight: 400,
              color: COLOR.textMuted,
            }}
          >
            Agent 管理后台
          </div>
        </div>

        <button
          onClick={handleEnter}
          disabled={entering}
          style={{
            width: '100%',
            background: COLOR.primary,
            border: 'none',
            borderRadius: 8,
            padding: '12px 16px',
            color: '#fff',
            fontSize: 14,
            fontWeight: 500,
            cursor: entering ? 'wait' : 'pointer',
            opacity: entering ? 0.75 : 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            transition: 'opacity 0.15s',
          }}
          onMouseEnter={(e) => {
            if (!entering) e.currentTarget.style.opacity = '0.9';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.opacity = entering ? '0.75' : '1';
          }}
        >
          <span>{entering ? '进入中…' : '进入系统'}</span>
          <ArrowRightOutlined />
        </button>

        <div
          style={{
            fontSize: 12,
            fontWeight: 400,
            color: COLOR.textMuted,
            textAlign: 'center',
            lineHeight: 1.6,
          }}
        >
          SSO 登录已移除。本地开发请开启后端
          <br />
          <code style={{ fontSize: 11 }}>app.auth.disable-auth=true</code>
        </div>

        <div
          style={{
            width: '100%',
            height: 1,
            background: COLOR.divider,
            margin: '4px 0',
          }}
        />

        <div
          style={{
            fontSize: 12,
            color: COLOR.textMuted,
            textAlign: 'center',
          }}
        >
          没有访问权限？
          <a
            style={{ color: COLOR.primary, marginLeft: 4 }}
            onClick={(e) => {
              e.preventDefault();
              window.alert('请联系：rd-agent-platform@garry.com');
            }}
          >
            联系运维
          </a>
        </div>
      </div>
    </div>
  );
}
