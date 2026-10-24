-- AgentScope streamEvents 细粒度 SSE：开放面 / 调试台新路径（不改旧 invoke）
INSERT INTO `route_permission` (`path_pattern`, `permission_codes`, `description`, `sort_order`, `create_time`, `update_time`)
SELECT '/api/v1/debug-console/stream', '["agent:invoke"]', '调试台 streamEvents 流式调用', 108, NOW(3), NOW(3)
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `route_permission` WHERE `path_pattern` = '/api/v1/debug-console/stream'
);
