/**
 * 登录页 — `/login`
 * 用户名 + 密码登录。
 */
import { ArrowRightOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/providers/AuthProvider';
import BrandMark from '@/components/BrandMark';
import { APP_BRAND } from '@/layouts/avatar';
import { useEffect, useState } from 'react';
import { userApi } from '@/services/user';
import { clearForceLogin, isForceLogin } from '@/services/auth/session';
import { useWorkspaceStore } from '@/stores/workspace';

const COLOR = {
  pageBg: '#F8FAFC',
  cardBg: '#FFFFFF',
  cardBorder: '#E2E8F0',
  textPrimary: '#0F172B',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
} as const;

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, refresh } = useAuth();
  const [entering, setEntering] = useState(false);
  const [error, setError] = useState<string>();
  const [form] = Form.useForm();

  useEffect(() => {
    if (!currentUser) return;
    const params = new URLSearchParams(location.search);
    // 退出登录 / 预览模式：停留在登录页
    if (params.get('stay') === '1' || isForceLogin()) return;
    navigate('/spaces', { replace: true });
  }, [currentUser, location.search, navigate]);

  const isPreview =
    new URLSearchParams(location.search).get('stay') === '1';

  const goAfterLogin = () => {
    clearForceLogin();
    // 登录成功先进门户选空间，避免 ?from= 旧空间页带着无效上下文
    useWorkspaceStore.getState().setCurrentWorkspace(undefined);
    navigate('/spaces', { replace: true });
  };

  const handleLogin = async () => {
    const values = await form.validateFields();
    setEntering(true);
    setError(undefined);
    try {
      await userApi.login(values.username.trim(), values.password);
      clearForceLogin();
      await refresh();
      goAfterLogin();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '登录失败';
      setError(msg);
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

        {error ? (
          <Alert
            type="error"
            showIcon
            message={error}
            style={{ width: '100%' }}
          />
        ) : null}

        <Form
          form={form}
          layout="vertical"
          style={{ width: '100%' }}
          onFinish={handleLogin}
          requiredMark={false}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input size="large" placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              size="large"
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            size="large"
            block
            loading={entering}
            icon={<ArrowRightOutlined />}
            style={{ background: COLOR.primary }}
          >
            登录
          </Button>
        </Form>
      </div>
    </div>
  );
}
