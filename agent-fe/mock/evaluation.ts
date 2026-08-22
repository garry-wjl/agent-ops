/**
 * mock — 评测域
 * 旧 Skill 评测 mock 已拆除；本文件保留空数组，让 vite-plugin-mock 不拦截，
 * 由 vite proxy 转发到 rd-agent-be 新评测 API。
 */
import type { MockMethod } from 'vite-plugin-mock';

export default [] as MockMethod[];
