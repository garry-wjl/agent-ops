-- ===== V34__skill_unique_by_workspace_name.sql =====
-- Skill 名称唯一性从按 ownerUserId 改为按工作空间隔离。
-- uk_skill_owner_name(owner_user_id, name, deleted) → uq_skill_ws_name(workspace_num, name, deleted)
-- 对应技术方案：2026-07-28-Skill名称按空间隔离-Bug修复方案.md
-- ====================================================================

-- 1. 删除旧的按 ownerUserId 的唯一索引
ALTER TABLE `skill`
    DROP INDEX `uk_skill_owner_name`;

-- 2. 新建按工作空间的唯一索引（与 Tool / Model / Sandbox 保持一致规范前缀 uq_）
--    存量数据中 workspace_num 为 NULL 的记录（极少边缘情况），MySQL unique 允许 NULL 值重复，
--    不影响正常流程（Controller 层已强制要求空间上下文）。
ALTER TABLE `skill`
    ADD UNIQUE KEY `uq_skill_ws_name` (`workspace_num`, `name`, `deleted`);