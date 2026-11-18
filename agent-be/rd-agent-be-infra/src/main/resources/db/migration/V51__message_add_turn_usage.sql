-- 一轮 Agent 运行的 Run ID 与 Token 用量。
-- 历史消息这几列为空；新消息在本轮结束落库时写入。
-- Token 只记在该轮的助手消息上，用户消息只记 run_id。

ALTER TABLE `message`
    ADD COLUMN `run_id` varchar(64) DEFAULT NULL COMMENT '本轮 Agent run 编号，历史消息为空' AFTER `trace_id`,
    ADD COLUMN `input_tokens` int DEFAULT NULL COMMENT '本轮输入 token，仅助手消息' AFTER `run_id`,
    ADD COLUMN `output_tokens` int DEFAULT NULL COMMENT '本轮输出 token，仅助手消息' AFTER `input_tokens`,
    ADD COLUMN `cached_tokens` int DEFAULT NULL COMMENT '本轮缓存 token，仅助手消息' AFTER `output_tokens`,
    ADD COLUMN `total_tokens` int DEFAULT NULL COMMENT '本轮总 token（输入+输出），仅助手消息' AFTER `cached_tokens`;
