/**
 * 登录页 — `/login`
 * 视觉参考火山引擎控制台：左品牌区 + 右表单；连续 3 次密码错误后强制滑块人机校验。
 */
import { ArrowRightOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/providers/AuthProvider';
import BrandMark from '@/components/BrandMark';
import SliderCaptcha, { type SliderCaptchaValue } from '@/components/SliderCaptcha';
import { APP_BRAND } from '@/layouts/avatar';
import { useEffect, useState } from 'react';
import { userApi } from '@/services/user';
import { BizError } from '@/services/request';
import { clearForceLogin, isForceLogin } from '@/services/auth/session';
import { useWorkspaceStore } from '@/stores/workspace';

/** 与后端 BizCode.LOGIN_CAPTCHA_REQUIRED / LOGIN_CAPTCHA_INVALID 对齐 */
const CODE_CAPTCHA_REQUIRED = 1106;
const CODE_CAPTCHA_INVALID = 1107;
const FAIL_THRESHOLD = 3;

const COLOR = {
  primary: '#2B52D9',
  textPrimary: '#0F172B',
  textMuted: '#64748B',
  textOnDark: 'rgba(255,255,255,0.78)',
} as const;

function useNarrowScreen(breakpoint = 900) {
  const [narrow, setNarrow] = useState(
    () => typeof window !== 'undefined' && window.innerWidth < breakpoint,
  );
  useEffect(() => {
    const onResize = () => setNarrow(window.innerWidth < breakpoint);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [breakpoint]);
  return narrow;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, refresh } = useAuth();
  const narrow = useNarrowScreen();
  const [entering, setEntering] = useState(false);
  const [error, setError] = useState<string>();
  const [, setFailCount] = useState(0);
  const [captchaRequired, setCaptchaRequired] = useState(false);
  const [captcha, setCaptcha] = useState<SliderCaptchaValue | null>(null);
  const [captchaRefreshKey, setCaptchaRefreshKey] = useState(0);
  const [form] = Form.useForm();

  useEffect(() => {
    if (!currentUser) return;
    const params = new URLSearchParams(location.search);
    if (params.get('stay') === '1' || isForceLogin()) return;
    navigate('/spaces', { replace: true });
  }, [currentUser, location.search, navigate]);

  const isPreview =
    new URLSearchParams(location.search).get('stay') === '1';

  const goAfterLogin = () => {
    clearForceLogin();
    useWorkspaceStore.getState().setCurrentWorkspace(undefined);
    navigate('/spaces', { replace: true });
  };

  const handleLogin = async () => {
    const values = await form.validateFields();
    if (captchaRequired && !captcha) {
      setError('请先完成滑块验证');
      return;
    }
    setEntering(true);
    setError(undefined);
    try {
      await userApi.login(values.username.trim(), values.password, captcha ?? undefined);
      clearForceLogin();
      setFailCount(0);
      setCaptchaRequired(false);
      await refresh();
      goAfterLogin();
    } catch (e: unknown) {
      const biz = e instanceof BizError ? e : null;
      const msg = e instanceof Error ? e.message : '登录失败';
      setError(msg);
      if (biz?.code === CODE_CAPTCHA_REQUIRED || biz?.code === CODE_CAPTCHA_INVALID) {
        setCaptchaRequired(true);
        setCaptcha(null);
        setCaptchaRefreshKey((k) => k + 1);
        setFailCount((c) => Math.max(c, FAIL_THRESHOLD));
      } else if (biz?.code === 1103) {
        setFailCount((c) => {
          const next = c + 1;
          if (next >= FAIL_THRESHOLD) setCaptchaRequired(true);
          return next;
        });
        setCaptcha(null);
        setCaptchaRefreshKey((k) => k + 1);
      }
    } finally {
      setEntering(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        background: '#0B1F3A',
      }}
    >
      {/* 左侧品牌区 — 对齐火山引擎控制台左右分栏 */}
      {!narrow ? (
      <div
        style={{
          flex: '1 1 54%',
          minWidth: 0,
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          padding: '64px 72px',
          background:
            'radial-gradient(ellipse 80% 60% at 20% 30%, rgba(43,82,217,0.55) 0%, transparent 55%),' +
            'linear-gradient(145deg, #071428 0%, #0B1F3A 42%, #122A52 100%)',
          overflow: 'hidden',
        }}
      >
        <div
          aria-hidden
          style={{
            position: 'absolute',
            inset: 0,
            backgroundImage:
              'radial-gradient(rgba(255,255,255,0.06) 1px, transparent 1px)',
            backgroundSize: '28px 28px',
            opacity: 0.5,
          }}
        />
        <div style={{ position: 'relative', maxWidth: 480 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 28 }}>
            <BrandMark size={48} />
            <Typography.Title
              level={2}
              style={{ margin: 0, color: '#F8FAFC', fontWeight: 700, letterSpacing: 0.5 }}
            >
              {APP_BRAND}
            </Typography.Title>
          </div>
          <Typography.Title
            level={3}
            style={{
              margin: '0 0 12px',
              color: '#FFFFFF',
              fontWeight: 600,
              fontSize: 28,
              lineHeight: 1.35,
            }}
          >
            统一管理 Agent 资产与调试能力
          </Typography.Title>
          <Typography.Paragraph
            style={{ margin: 0, color: COLOR.textOnDark, fontSize: 15, lineHeight: 1.7 }}
          >
            工作空间、Agent / Skill / Prompt / 工具与评测一站式控制面。
            <br />
            安全登录后进入你的空间门户。
          </Typography.Paragraph>
        </div>
        <div
          style={{
            position: 'absolute',
            left: 72,
            bottom: 40,
            color: 'rgba(255,255,255,0.35)',
            fontSize: 12,
          }}
        >
          © {new Date().getFullYear()} {APP_BRAND}
        </div>
      </div>
      ) : null}

      {/* 右侧登录表单 */}
      <div
        style={{
          flex: narrow ? '1 1 auto' : '0 0 min(480px, 100%)',
          width: narrow ? '100%' : 'min(480px, 100%)',
          background: '#FFFFFF',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '48px 40px',
          minHeight: '100vh',
        }}
      >
        <div style={{ width: '100%', maxWidth: 360 }}>
          {isPreview ? (
            <div
              style={{
                fontSize: 11,
                color: COLOR.textMuted,
                background: '#FEF3C7',
                border: '1px solid #FDE68A',
                borderRadius: 4,
                padding: '4px 10px',
                marginBottom: 16,
                textAlign: 'center',
              }}
            >
              预览模式（?stay=1）— 已登录用户正常访问会自动 redirect
            </div>
          ) : null}

          <Typography.Title
            level={3}
            style={{ margin: '0 0 8px', color: COLOR.textPrimary, fontWeight: 650 }}
          >
            账号登录
          </Typography.Title>
          {narrow ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
              <BrandMark size={28} />
              <span style={{ fontWeight: 600, color: COLOR.textPrimary }}>{APP_BRAND}</span>
            </div>
          ) : null}
          <Typography.Paragraph style={{ margin: '0 0 28px', color: COLOR.textMuted }}>
            使用平台用户名与密码登录
          </Typography.Paragraph>

          {error ? (
            <Alert
              type="error"
              showIcon
              message={error}
              style={{ marginBottom: 16 }}
            />
          ) : null}

          <Form
            form={form}
            layout="vertical"
            onFinish={handleLogin}
            requiredMark={false}
            size="large"
          >
            <Form.Item
              name="username"
              label="用户名"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input placeholder="请输入用户名" autoComplete="username" />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                placeholder="请输入密码"
                autoComplete="current-password"
              />
            </Form.Item>

            {captchaRequired ? (
              <Form.Item label="安全验证" required style={{ marginBottom: 20 }}>
                <SliderCaptcha
                  refreshKey={captchaRefreshKey}
                  onChange={setCaptcha}
                />
              </Form.Item>
            ) : null}

            <Button
              type="primary"
              htmlType="submit"
              block
              loading={entering}
              icon={<ArrowRightOutlined />}
              style={{
                background: COLOR.primary,
                height: 44,
                fontWeight: 600,
                marginTop: 4,
              }}
            >
              登录
            </Button>
          </Form>
        </div>
      </div>
    </div>
  );
}
