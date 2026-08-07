/**
 * 登录会话辅助：处理「退出后仍停留在登录页」。
 * <p>本地 disable-auth 时 /auth/me 仍会成功，需用标记阻止登录页自动跳走。</p>
 */

const FORCE_LOGIN_KEY = 'agentops:force_login';

/** 退出登录时打标，登录页看到后不自动跳转业务页。 */
export function markForceLogin(): void {
  try {
    sessionStorage.setItem(FORCE_LOGIN_KEY, '1');
  } catch {
    // ignore
  }
}

/** 用户主动登录 / 本地免登进入后清除标记。 */
export function clearForceLogin(): void {
  try {
    sessionStorage.removeItem(FORCE_LOGIN_KEY);
  } catch {
    // ignore
  }
}

/** 是否处于「强制展示登录页」状态。 */
export function isForceLogin(): boolean {
  try {
    return sessionStorage.getItem(FORCE_LOGIN_KEY) === '1';
  } catch {
    return false;
  }
}
