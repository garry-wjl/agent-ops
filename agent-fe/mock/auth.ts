/**
 * mock — /api/v1/auth/me
 *
 * 已下线：rd-agent-be 接入 GCAC SSO 后，/api/v1/auth/me 由真后端响应
 * （从 session_token cookie 解析 JWT → 返回当前用户）。
 *
 * Vite mock 优先级高于 proxy，留在这里会拦截掉真后端的响应导致 SSO 不闭环。
 * 留空数组即可（不注册任何路由），需要时取消注释 fallback 块即可临时回到 mock。
 */
import type { MockMethod } from 'vite-plugin-mock';
// import { ok } from './_helpers';

// 临时 mock fallback（dev 时后端没起 / 想绕开 SSO 时启用）：
// {
//   url: '/api/v1/auth/me',
//   method: 'get',
//   response: () => ok({ userId: 'mock-user', userName: 'mock-user', role: 'admin' }),
// }

export default [] as MockMethod[];
