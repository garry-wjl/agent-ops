-- V37__align_user_workspace_role_collation.sql
-- 修复禁用用户时 JOIN sys_user.num = user_workspace_role.user_id 的 collation 冲突：
--   sys_user            → utf8mb4_unicode_ci（V35）
--   user_workspace_role → utf8mb4_0900_ai_ci（V1）
-- 统一为 utf8mb4_unicode_ci，与工作区主表等资产表一致。

ALTER TABLE `user_workspace_role`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
