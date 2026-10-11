-- Skill 压缩包升级：解压覆盖草稿 + 自动版本号 + 发布三检
INSERT INTO `route_permission` (`path_pattern`, `permission_codes`, `description`, `sort_order`, `create_time`, `update_time`)
SELECT '/api/v1/skill/command/upgradeFromZip', '["skill:publish"]', 'Skill 压缩包升级并发布', 208, NOW(3), NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM `route_permission` WHERE `path_pattern` = '/api/v1/skill/command/upgradeFromZip'
);
