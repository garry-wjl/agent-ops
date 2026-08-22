-- V39__agent_app_evaluation_rebuild.sql
-- Agent 应用评测：删除旧 Skill 评测表，重建评测集/评估器/评测任务；替换 evaluation 权限点

DROP TABLE IF EXISTS `evaluation_case`;
DROP TABLE IF EXISTS `evaluation`;
DROP TABLE IF EXISTS `eval_seed`;

CREATE TABLE `eval_dataset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `num` VARCHAR(64) NOT NULL,
  `workspace_num` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(512) DEFAULT NULL,
  `type` VARCHAR(32) NOT NULL COMMENT 'AGENT/CUSTOM',
  `agent_num` VARCHAR(64) DEFAULT NULL,
  `schema_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL COMMENT 'DRAFT/PUBLISHED',
  `latest_version` INT NOT NULL DEFAULT 0,
  `create_no` VARCHAR(64) NOT NULL,
  `update_no` VARCHAR(64) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_eval_dataset_num` (`num`),
  KEY `idx_eval_dataset_ws` (`workspace_num`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测集';

CREATE TABLE `eval_dataset_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `dataset_num` VARCHAR(64) NOT NULL,
  `version` INT NOT NULL,
  `schema_json` MEDIUMTEXT NOT NULL,
  `row_count` INT NOT NULL,
  `publish_no` VARCHAR(64) NOT NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_dataset_ver` (`dataset_num`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测集已发布版本';

CREATE TABLE `eval_dataset_row` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `num` VARCHAR(64) NOT NULL,
  `dataset_num` VARCHAR(64) NOT NULL,
  `version` INT DEFAULT NULL COMMENT 'NULL=草稿',
  `row_index` INT NOT NULL,
  `data_json` MEDIUMTEXT NOT NULL,
  `create_no` VARCHAR(64) NOT NULL,
  `update_no` VARCHAR(64) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_eval_dataset_row_num` (`num`),
  KEY `idx_dataset_row` (`dataset_num`, `version`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测集行';

CREATE TABLE `eval_grader` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `num` VARCHAR(64) NOT NULL,
  `workspace_num` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(512) DEFAULT NULL,
  `kind` VARCHAR(32) NOT NULL COMMENT 'BUILTIN/LLM/CODE',
  `builtin_code` VARCHAR(64) DEFAULT NULL,
  `config_json` MEDIUMTEXT NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `create_no` VARCHAR(64) NOT NULL,
  `update_no` VARCHAR(64) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_eval_grader_num` (`num`),
  KEY `idx_eval_grader_ws` (`workspace_num`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评估器';

CREATE TABLE `eval_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `num` VARCHAR(64) NOT NULL,
  `workspace_num` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(512) DEFAULT NULL,
  `dataset_num` VARCHAR(64) NOT NULL,
  `dataset_version` INT NOT NULL,
  `bind_mode` VARCHAR(32) NOT NULL COMMENT 'AGENT/NONE',
  `agent_num` VARCHAR(64) DEFAULT NULL,
  `agent_version_num` VARCHAR(64) DEFAULT NULL,
  `grader_bindings_json` MEDIUMTEXT NOT NULL,
  `label_config_json` MEDIUMTEXT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `total_count` INT NOT NULL DEFAULT 0,
  `passed_count` INT NOT NULL DEFAULT 0,
  `failed_count` INT NOT NULL DEFAULT 0,
  `creator_user_id` VARCHAR(64) NOT NULL,
  `create_no` VARCHAR(64) NOT NULL,
  `update_no` VARCHAR(64) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_eval_task_num` (`num`),
  KEY `idx_eval_task_ws` (`workspace_num`, `deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测任务';

CREATE TABLE `eval_task_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `num` VARCHAR(64) NOT NULL,
  `task_num` VARCHAR(64) NOT NULL,
  `row_index` INT NOT NULL,
  `input_json` MEDIUMTEXT NOT NULL,
  `actual_output` MEDIUMTEXT DEFAULT NULL,
  `trace_summary_json` MEDIUMTEXT DEFAULT NULL,
  `overall_pass` TINYINT(1) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `latency_ms` BIGINT DEFAULT NULL,
  `error_message` VARCHAR(1024) DEFAULT NULL,
  `label_json` MEDIUMTEXT DEFAULT NULL,
  `create_no` VARCHAR(64) NOT NULL,
  `update_no` VARCHAR(64) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_eval_task_item_num` (`num`),
  KEY `idx_task_item` (`task_num`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测任务用例';

CREATE TABLE `eval_task_item_score` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `task_item_num` VARCHAR(64) NOT NULL,
  `grader_num` VARCHAR(64) NOT NULL,
  `grader_version` INT NOT NULL,
  `score` DECIMAL(10,4) DEFAULT NULL,
  `passed` TINYINT(1) NOT NULL,
  `explanation` VARCHAR(2048) DEFAULT NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY `idx_item_score` (`task_item_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例×评估器得分';

-- 权限：删除旧 evaluation:*，插入新权限点
DELETE FROM `role_permission` WHERE `permission_code` LIKE 'evaluation:%';
DELETE FROM `route_permission` WHERE `path_pattern` LIKE '/api/v1/evaluation/%';
DELETE FROM `permission` WHERE `code` LIKE 'evaluation:%';

INSERT INTO `permission` (`code`,`name`,`resource_domain`,`scope`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('evaluation:dataset:read','查看评测集','evaluation','SPACE','评测集列表/详情/行/模板',510,NOW(3),NOW(3)),
('evaluation:dataset:create','创建评测集','evaluation','SPACE','新建评测集草稿',511,NOW(3),NOW(3)),
('evaluation:dataset:update','编辑评测集','evaluation','SPACE','编辑草稿/导入',512,NOW(3),NOW(3)),
('evaluation:dataset:delete','删除评测集','evaluation','SPACE','删除评测集',513,NOW(3),NOW(3)),
('evaluation:dataset:publish','发布评测集','evaluation','SPACE','发布评测集版本',514,NOW(3),NOW(3)),
('evaluation:grader:read','查看评估器','evaluation','SPACE','评估器列表/详情/预置',520,NOW(3),NOW(3)),
('evaluation:grader:create','创建评估器','evaluation','SPACE','创建内置/LLM 评估器',521,NOW(3),NOW(3)),
('evaluation:grader:update','编辑评估器','evaluation','SPACE','更新评估器配置',522,NOW(3),NOW(3)),
('evaluation:grader:delete','删除评估器','evaluation','SPACE','删除评估器',523,NOW(3),NOW(3)),
('evaluation:task:read','查看评测任务','evaluation','SPACE','任务列表/详情/明细',530,NOW(3),NOW(3)),
('evaluation:task:create','创建评测任务','evaluation','SPACE','创建并启动评测任务',531,NOW(3),NOW(3)),
('evaluation:task:execute','执行评测任务','evaluation','SPACE','取消/重跑等执行类操作',532,NOW(3),NOW(3)),
('evaluation:task:delete','删除评测任务','evaluation','SPACE','删除评测任务',533,NOW(3),NOW(3)),
('evaluation:task:compare','对比评测任务','evaluation','SPACE','任务对比',534,NOW(3),NOW(3));

-- 路由权限
INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('/api/v1/evaluation/dataset/command/create','["evaluation:dataset:create"]','评测集创建',510,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/updateDraft','["evaluation:dataset:update"]','评测集编辑草稿',511,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/importXlsx','["evaluation:dataset:update"]','评测集导入xlsx',512,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/publish','["evaluation:dataset:publish"]','评测集发布',513,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/delete','["evaluation:dataset:delete"]','评测集删除',514,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/query/**','["evaluation:dataset:read"]','评测集查询',515,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/command/createBuiltin','["evaluation:grader:create"]','评估器创建内置',520,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/command/update','["evaluation:grader:update"]','评估器更新',521,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/command/delete','["evaluation:grader:delete"]','评估器删除',522,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/command/trialRun','["evaluation:grader:read"]','评估器试跑',523,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/query/**','["evaluation:grader:read"]','评估器查询',524,NOW(3),NOW(3)),
('/api/v1/evaluation/task/command/createAndStart','["evaluation:task:create"]','评测任务创建启动',530,NOW(3),NOW(3)),
('/api/v1/evaluation/task/command/cancel','["evaluation:task:execute"]','评测任务取消',531,NOW(3),NOW(3)),
('/api/v1/evaluation/task/command/delete','["evaluation:task:delete"]','评测任务删除',532,NOW(3),NOW(3)),
('/api/v1/evaluation/task/query/compare','["evaluation:task:compare"]','评测任务对比',533,NOW(3),NOW(3)),
('/api/v1/evaluation/task/query/**','["evaluation:task:read"]','评测任务查询',534,NOW(3),NOW(3));

-- 授予平台管理员 + 空间管理员全部新权限
INSERT INTO `role_permission` (`role_num`,`permission_code`,`create_time`,`update_time`)
SELECT 'RL-PLATFORM-ADMIN', p.code, NOW(3), NOW(3) FROM `permission` p
WHERE p.code LIKE 'evaluation:%'
AND NOT EXISTS (
  SELECT 1 FROM `role_permission` rp WHERE rp.role_num='RL-PLATFORM-ADMIN' AND rp.permission_code=p.code
);

INSERT INTO `role_permission` (`role_num`,`permission_code`,`create_time`,`update_time`)
SELECT 'RL-SPACE-ADMIN', p.code, NOW(3), NOW(3) FROM `permission` p
WHERE p.code LIKE 'evaluation:%'
AND NOT EXISTS (
  SELECT 1 FROM `role_permission` rp WHERE rp.role_num='RL-SPACE-ADMIN' AND rp.permission_code=p.code
);

-- 空间成员仅 *:read
INSERT INTO `role_permission` (`role_num`,`permission_code`,`create_time`,`update_time`)
SELECT 'RL-SPACE-MEMBER', p.code, NOW(3), NOW(3) FROM `permission` p
WHERE p.code IN ('evaluation:dataset:read','evaluation:grader:read','evaluation:task:read')
AND NOT EXISTS (
  SELECT 1 FROM `role_permission` rp WHERE rp.role_num='RL-SPACE-MEMBER' AND rp.permission_code=p.code
);

-- 同步 role.permission_codes JSON（去掉旧 evaluation:*，合并新权限）
UPDATE `role`
SET `permission_codes` = JSON_MERGE_PRESERVE(
  COALESCE(`permission_codes`, JSON_ARRAY()),
  JSON_ARRAY(
    'evaluation:dataset:read','evaluation:dataset:create','evaluation:dataset:update','evaluation:dataset:delete','evaluation:dataset:publish',
    'evaluation:grader:read','evaluation:grader:create','evaluation:grader:update','evaluation:grader:delete',
    'evaluation:task:read','evaluation:task:create','evaluation:task:execute','evaluation:task:delete','evaluation:task:compare'
  )
)
WHERE `num` IN ('RL-PLATFORM-ADMIN','RL-SPACE-ADMIN');

UPDATE `role`
SET `permission_codes` = JSON_MERGE_PRESERVE(
  COALESCE(`permission_codes`, JSON_ARRAY()),
  JSON_ARRAY('evaluation:dataset:read','evaluation:grader:read','evaluation:task:read')
)
WHERE `num` = 'RL-SPACE-MEMBER';
