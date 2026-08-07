/**
 * 入口文件
 * - antd ConfigProvider 注入主色 / 字体 / 扁平化主题
 * - antd App 提供 message/notification 静态方法上下文
 * - AuthProvider 拉当前用户
 * - HashRouter / BrowserRouter 选 Browser（独立子域 agent.garry.internal）
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN.js';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { AuthProvider } from './providers/AuthProvider';
import './global.less';

dayjs.locale('zh-cn');

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: antdTheme.defaultAlgorithm,
        token: {
          colorPrimary: '#2B52D9',
          colorInfo: '#2B52D9',
          colorTextBase: '#0F172B',
          borderRadius: 6,
          fontFamily:
            '"Noto Sans SC", -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif',
        },
        components: {
          Layout: {
            headerBg: '#ffffff',
            siderBg: '#FAFBFC',
            bodyBg: '#ffffff',
            headerHeight: 56,
          },
          Menu: {
            itemBg: '#FAFBFC',
            subMenuItemBg: '#FAFBFC',
            itemColor: '#0F172B',
            itemHoverBg: 'rgba(43, 82, 217, 0.04)',
            itemHoverColor: '#2B52D9',
            itemSelectedBg: 'rgba(43, 82, 217, 0.08)',
            itemSelectedColor: '#2B52D9',
            groupTitleColor: '#94A3B8',
            iconSize: 16,
            itemHeight: 40,
          },
          Card: {
            colorBorderSecondary: 'transparent',
          },
        },
      }}
    >
      <AntdApp>
        <BrowserRouter>
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <App />
            </AuthProvider>
          </QueryClientProvider>
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>,
);
