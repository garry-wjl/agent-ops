/**
 * Axios 实例 — 统一 Result<T> 解析
 * - withCredentials: true，浏览器自动带 session_token cookie
 * - HTTP 401 或 Result.code === 1002（UNAUTHORIZED） → 跳转 /login
 * - HTTP 403 + 结构化 Result 体（code=40301/1003）→ 展示响应体 message（"缺少 xxx 权限，请联系空间管理员"）
 * - 业务错误（HTTP 200 + code !== 0）→ 抛 BizError，统一 toast
 * - HTTP 错误（5xx / 网络）→ 统一 toast
 *
 * 注意：不再手动注入 X-User-Id header；身份由 session_token cookie + 后端 JWT 解析，
 * 或本地 disable-auth 时由后端 UserContextFilter 注入。
 */
import axios, { AxiosError } from 'axios';
import type { AxiosInstance, AxiosResponse } from 'axios';
import { message } from 'antd';
import type { Result } from '@/types';

export class BizError extends Error {
  override readonly name = 'BizError';
  readonly code: number;
  override readonly message: string;
  readonly traceId: string;
  /** 业务错误随响应体返回的结构化 data（如发布检测 3006 的 SkillPublishResultVO）。 */
  readonly data: unknown;
  constructor(code: number, message: string, traceId: string, data?: unknown) {
    super(message);
    this.code = code;
    this.message = message;
    this.traceId = traceId;
    this.data = data;
  }
}

/** 业务错误码 1002 = 未登录；与后端 BizCode.UNAUTHORIZED 同步。 */
const CODE_UNAUTHORIZED = 1002;

/**
 * 静默业务错误码 —— 命中后不弹全局 toast，由调用方读 {@link BizError.data} 自行渲染。
 * - 3006 = Skill 发布检测不通过（页面以错误清单展示，见 Skill 发布检测态）。
 */
const SILENT_BIZ_CODES = new Set<number>([3006]);

/** 防止 401 风暴：30s 内只跳一次登录页。 */
let lastLoginRedirectAt = 0;
const LOGIN_REDIRECT_DEBOUNCE_MS = 30_000;

function redirectToLogin(): void {
  const now = Date.now();
  if (now - lastLoginRedirectAt < LOGIN_REDIRECT_DEBOUNCE_MS) return;
  lastLoginRedirectAt = now;
  if (window.location.pathname === '/login') return;
  const from = encodeURIComponent(
    `${window.location.pathname}${window.location.search}`,
  );
  window.location.href = `/login?from=${from}`;
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true,
});

/**
 * 注入当前活动工作空间请求头 `X-Workspace-Num`。
 * - 从 localStorage 读取（与 stores/workspace.ts 同 key），避免在拦截器里依赖 React 状态
 * - 空间管理 / 通用 / 登录登出接口无需该头（common 为系统横切，不感知空间）
 * - 登录页上的 /auth/me 也不带（避免旧空间头干扰身份回显）
 * 详见技术方案 §7.6。
 */
instance.interceptors.request.use(config => {
  const url = config.url ?? '';
  const onLoginPage =
    typeof window !== 'undefined' && window.location.pathname === '/login';
  const skip =
    url.includes('/api/v1/workspace') ||
    url.includes('/api/v1/common') ||
    url.includes('/api/v1/platform-roles') ||
    url.includes('/api/v1/auth/login') ||
    url.includes('/api/v1/auth/logout') ||
    url.includes('/api/v1/users') ||
    (onLoginPage && url.includes('/api/v1/auth/me'));
  if (!skip) {
    const ws = localStorage.getItem('currentWorkspaceNum');
    if (ws) {
      config.headers = config.headers ?? {};
      (config.headers as Record<string, string>)['X-Workspace-Num'] = ws;
    }
  }
  return config;
});

instance.interceptors.response.use(
  (resp: AxiosResponse) => {
    const data = resp.data;
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === CODE_UNAUTHORIZED) {
        redirectToLogin();
        return Promise.reject(
          new BizError(data.code, data.message ?? '未登录', data.traceId ?? '-'),
        );
      }
      if (data.code !== 0) {
        const err = new BizError(
          data.code,
          data.message,
          data.traceId ?? '-',
          data.data,
        );
        if (!SILENT_BIZ_CODES.has(data.code)) {
          message.error(err.message);
        }
        return Promise.reject(err);
      }
    }
    return resp;
  },
  (err: AxiosError) => {
    if (err.response) {
      if (err.response.status === 401) {
        redirectToLogin();
        return Promise.reject(err);
      }
      // 尝试从响应体取结构化 message（后端 Result<T> 格式）
      // 403 权限错误的响应体形如 { code: 40301, message: "缺少 xxx 权限，请联系空间管理员" }
      const body = err.response.data as Record<string, unknown> | undefined;
      const bodyMessage = body && typeof body.message === 'string' && body.message
        ? body.message
        : undefined;
      if (bodyMessage) {
        message.error(bodyMessage);
      } else {
        message.error(`HTTP ${err.response.status}：${err.response.statusText ?? '请求失败'}`);
      }
    } else if (err.request) {
      message.error('网络异常，请稍后重试');
    } else {
      message.error(err.message ?? '未知错误');
    }
    return Promise.reject(err);
  },
);

export async function get<T>(
  url: string,
  params?: Record<string, any>,
): Promise<T> {
  const res = await instance.get<Result<T>>(url, { params });
  return res.data.data;
}

export async function post<T>(url: string, body?: any): Promise<T> {
  const res = await instance.post<Result<T>>(url, body);
  return res.data.data;
}

export { instance as default, instance as request };
