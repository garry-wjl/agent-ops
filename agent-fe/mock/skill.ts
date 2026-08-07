/**
 * mock — Skill 域接口（已下线）
 *
 * rd-agent-be Skill 模块已接真后端，且工作空间过滤在 BE 的
 * SkillQueryService.pageList / detail 中按 X-Workspace-Num 实现。
 * 若仍保留 mock，会拦截 /api/v1/skill/query/* 导致请求到不了真后端、空间过滤不生效。
 *
 * 需要临时回到 mock 调 UI 时，可从 git 历史恢复本文件的旧实现。
 */
import type { MockMethod } from 'vite-plugin-mock';

export default [] as MockMethod[];
