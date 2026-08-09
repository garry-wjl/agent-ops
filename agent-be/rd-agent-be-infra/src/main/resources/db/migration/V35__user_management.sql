-- V35__user_management.sql
-- 用户管理：sys_user 主表 + user_manage 权限种子

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` VARCHAR(64) NOT NULL COMMENT '业务编号 USR-xxx',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `email` VARCHAR(128) NOT NULL COMMENT '邮箱',
  `remark` VARCHAR(512) NULL COMMENT '备注',
  `status` VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  `create_no` VARCHAR(64) NOT NULL COMMENT '创建人',
  `update_no` VARCHAR(64) NOT NULL COMMENT '更新人',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_num` (`num`, `deleted`),
  UNIQUE KEY `uk_sys_user_username` (`username`, `deleted`),
  UNIQUE KEY `uk_sys_user_email` (`email`, `deleted`),
  KEY `idx_sys_user_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台用户主数据';

-- permission 表字段为 resource_domain（非 module）
INSERT INTO `permission` (`code`,`name`,`resource_domain`,`scope`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('user_manage:read','查看用户','user_manage','PLATFORM','用户列表/详情',801,NOW(3),NOW(3)),
('user_manage:create','创建用户','user_manage','PLATFORM','新建用户',802,NOW(3),NOW(3)),
('user_manage:update','编辑用户','user_manage','PLATFORM','编辑资料/重置密码',803,NOW(3),NOW(3)),
('user_manage:enable','启用用户','user_manage','PLATFORM','启用账号',804,NOW(3),NOW(3)),
('user_manage:disable','禁用用户','user_manage','PLATFORM','禁用账号',805,NOW(3),NOW(3)),
('user_manage:assign_role','分配用户平台角色','user_manage','PLATFORM','保存平台角色',806,NOW(3),NOW(3));

INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('/api/v1/users/page','["user_manage:read"]','用户分页',801,NOW(3),NOW(3)),
('/api/v1/users/detail','["user_manage:read"]','用户详情',802,NOW(3),NOW(3)),
('/api/v1/users/create','["user_manage:create"]','创建用户',803,NOW(3),NOW(3)),
('/api/v1/users/update','["user_manage:update"]','更新用户',804,NOW(3),NOW(3)),
('/api/v1/users/reset-password','["user_manage:update"]','重置密码',805,NOW(3),NOW(3)),
('/api/v1/users/enable','["user_manage:enable"]','启用用户',806,NOW(3),NOW(3)),
('/api/v1/users/disable','["user_manage:disable"]','禁用用户',807,NOW(3),NOW(3)),
('/api/v1/users/save-platform-roles','["user_manage:assign_role"]','保存平台角色',808,NOW(3),NOW(3));

INSERT INTO `role_permission` (`role_num`,`permission_code`,`create_time`,`update_time`)
SELECT 'RL-PLATFORM-ADMIN', p.code, NOW(3), NOW(3) FROM `permission` p
WHERE p.code LIKE 'user_manage:%'
AND NOT EXISTS (
  SELECT 1 FROM `role_permission` rp WHERE rp.role_num='RL-PLATFORM-ADMIN' AND rp.permission_code=p.code
);

UPDATE `role`
SET `permission_codes` = JSON_MERGE_PRESERVE(
  COALESCE(`permission_codes`, JSON_ARRAY()),
  JSON_ARRAY('user_manage:read','user_manage:create','user_manage:update','user_manage:enable','user_manage:disable','user_manage:assign_role')
)
WHERE `num` = 'RL-PLATFORM-ADMIN';
