-- ===== V36__session_add_invoke_context.sql =====
-- 会话默认调用上下文：供系统提示词变量替换继承
-- 对应技术方案：2026-08-09_系统提示词变量替换与调用上下文注入-技术方案.md
-- ====================================================================

ALTER TABLE `session`
    ADD COLUMN `invoke_context` TEXT NULL
    COMMENT '会话默认调用上下文 JSON object，供系统提示词变量替换继承'
    AFTER `origin`;
