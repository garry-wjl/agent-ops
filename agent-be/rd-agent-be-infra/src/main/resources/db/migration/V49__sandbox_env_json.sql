-- ===== V49__sandbox_env_json.sql =====
-- Agent 沙箱规格环境变量（创建容器时注入 OpenSandbox / Docker）

ALTER TABLE `sandbox`
    ADD COLUMN `env_json` TEXT NULL COMMENT '容器环境变量 JSON object<string,string>' AFTER `session_idle_ttl_minutes`;
