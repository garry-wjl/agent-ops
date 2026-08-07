/**
 * Vite 配置
 * - @ alias 指向 src/
 * - dev 默认开启 vite-plugin-mock；NO_MOCK=true 时关闭 mock
 * - proxy /api 始终启用，转发到 be（默认 :8081，可由 BE_PORT 覆盖）
 *   - mock 命中的请求由 mock 直接响应（mock 优先级高于 proxy）
 *   - mock 未拦截的请求（如调试台 SSE invoke）会自动 fallback 到 proxy → 真 be
 *   - 8080 被本地 docker nacos-standalone 占用，be 顺延到 8081
 * - antd Less 主题通过 ConfigProvider 在运行时注入，不需要 less 变量覆盖
 */
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { viteMockServe } from 'vite-plugin-mock';
import path from 'node:path';

export default defineConfig(({ mode }) => {
  const envDir = path.resolve(__dirname, 'env');
  const env = loadEnv(mode, envDir, '');
  const enableMock = env.NO_MOCK !== 'true';
  const bePort = env.BE_PORT || '8081';

  return {
    envDir,
    base: env.VITE_CDN_URL || '/',
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    plugins: [
      react(),
      viteMockServe({
        mockPath: 'mock',
        enable: enableMock,
        watchFiles: true,
        logger: true,
      }),
    ],
    server: {
      host: '0.0.0.0',
      port: 8001,
      proxy: {
        '/api': {
          target: `http://localhost:${bePort}`,
          changeOrigin: true,
        },
      },
    },
    build: {
      target: 'es2020',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            antd: ['antd', '@ant-design/icons', '@ant-design/pro-components'],
            antdx: ['@ant-design/x'],
            monaco: ['monaco-editor', '@monaco-editor/react'],
            echarts: ['echarts', 'echarts-for-react'],
          },
        },
      },
    },
    optimizeDeps: {
      include: [
        'react',
        'react-dom',
        'react-router-dom',
        'antd',
        '@ant-design/pro-components',
        '@ant-design/x',
      ],
    },
    define: {
      'process.env.APP_VERSION': JSON.stringify(env.APP_VERSION ?? 'dev'),
    },
  };
});
