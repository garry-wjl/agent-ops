/**
 * mock — Agent 域接口
 *
 * v2.4 起所有 /api/v1/agent/** 接口走真后端（rd-agent-be，默认 8081）。
 * 本文件保留为空数组，让 vite-plugin-mock 不拦截、由 vite proxy 转发。
 *
 * 如需临时回退到 mock，把历史 mock 项搬回（git log 可查）。
 */
import type { MockMethod } from 'vite-plugin-mock';

const mocks: MockMethod[] = [];

export default mocks;
