-- ===== V33__session_add_origin.sql =====
-- 会话表新增 origin 字段，区分发起来源：DEBUG_CONSOLE（调试台）/ API（Open API）
-- 对应技术方案：2026-07-23_会话来源与会话历史Tab-技术方案.md
-- ====================================================================

-- 新增来源字段，非空，默认 DEBUG_CONSOLE（兼容存量数据）
ALTER TABLE `session`
    ADD COLUMN `origin` VARCHAR(32) NOT NULL DEFAULT 'DEBUG_CONSOLE'
    COMMENT '会话来源：DEBUG_CONSOLE（调试台）/ API（Open API）'
    AFTER `last_message_at`;

-- 补充索引：按来源过滤会话列表
ALTER TABLE `session`
    ADD KEY `idx_origin` (`origin`, `deleted`);