/**
 * mock — 工具管理域接口（已下线）
 *
 * 服务端 tool 领域已接入真后端（rd-agent-be，ToolCommandController / ToolQueryController，
 * 默认 :8081）；工作空间过滤由 BE 按 X-Workspace-Num 头实现。
 *
 * vite-plugin-mock 优先级高于 proxy —— 若此处仍注册路由，会拦截 /api/v1/tool/** 导致
 * 请求到不了真后端、空间过滤不生效。故留空数组（不注册任何路由），由 vite proxy 转发到 be。
 *
 * 需要临时回到 mock 调 UI 时，从 git 历史恢复本文件的旧实现即可。
 */
import type { MockMethod } from "vite-plugin-mock";

export default [] as MockMethod[];
