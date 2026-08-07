import { Button, Result } from 'antd';
import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

/**
 * 全局错误边界组件
 *
 * 使用 React 类组件 API 实现，捕获子组件树中的 JavaScript 错误，
 * 防止整个应用崩溃，并展示友好的错误提示界面。
 *
 * 如果需要更丰富的功能（如 resetKeys、useErrorBoundary hook 等），
 * 可以考虑使用 react-error-boundary 库替代：
 * @see https://github.com/bvaughn/react-error-boundary
 */

interface Props {
  children: ReactNode;
  /** 自定义 fallback UI */
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // TODO: 上报错误到监控平台（如 Sentry）
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleReset = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Result
          status='error'
          title='页面出错了'
          subTitle={
            import.meta.env.DEV
              ? this.state.error?.message
              : '抱歉，页面发生了未知错误，请尝试刷新页面。'
          }
          extra={
            <Button type='primary' onClick={this.handleReset}>
              重试
            </Button>
          }
        />
      );
    }

    return this.props.children;
  }
}
