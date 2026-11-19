-- 按 Run ID 查会话详情。一轮通常有用户、助手两条消息，索引只用于定位 session_num。
-- 历史消息 run_id 为空，MySQL 索引允许多个 NULL。

ALTER TABLE `message`
    ADD KEY `idx_message_run_id` (`run_id`);
