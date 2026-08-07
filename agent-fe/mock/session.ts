/**
 * mock — 调试台 / 会话域接口
 *
 * v2.4 起所有 /api/v1/session/** 走真后端（rd-agent-be SessionController）。
 * 本文件保留为空数组让 vite-plugin-mock 不拦截、由 vite proxy 转发。
 *
 * SSE invoke (/api/v1/agents/{num}/invoke) 不在此 mock，由 utils/sse 直连后端。
 */
import type { MockMethod } from 'vite-plugin-mock';

const mocks: MockMethod[] = [];

export default mocks;
