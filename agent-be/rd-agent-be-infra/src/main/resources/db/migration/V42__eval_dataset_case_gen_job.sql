-- V42__eval_dataset_case_gen_job.sql
-- 评测集自动生成 Case：异步任务表 + 路由权限

CREATE TABLE IF NOT EXISTS `eval_dataset_case_gen_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `num` VARCHAR(64) NOT NULL,
  `workspace_num` VARCHAR(64) NOT NULL,
  `dataset_num` VARCHAR(64) NOT NULL,
  `generator_agent_num` VARCHAR(64) NOT NULL,
  `generator_agent_version_num` VARCHAR(64) DEFAULT NULL,
  `target_count` INT DEFAULT NULL COMMENT 'NULL=自行决定；指定时平台硬上限50',
  `clear_draft` TINYINT(1) NOT NULL DEFAULT 0,
  `instruction_mode` VARCHAR(16) NOT NULL DEFAULT 'APPEND' COMMENT 'APPEND/OVERRIDE',
  `user_instruction` MEDIUMTEXT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/FINISHED/FAILED/CANCELLED',
  `progress_pct` INT NOT NULL DEFAULT 0,
  `progress_message` VARCHAR(512) DEFAULT NULL,
  `parsed_count` INT NOT NULL DEFAULT 0,
  `written_count` INT NOT NULL DEFAULT 0,
  `skipped_count` INT NOT NULL DEFAULT 0,
  `error_message` VARCHAR(2048) DEFAULT NULL,
  `raw_output` MEDIUMTEXT DEFAULT NULL,
  `prompt_snapshot` MEDIUMTEXT DEFAULT NULL,
  `retry_of_num` VARCHAR(64) DEFAULT NULL,
  `create_no` VARCHAR(64) NOT NULL,
  `update_no` VARCHAR(64) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_case_gen_job_num` (`num`),
  KEY `idx_case_gen_dataset` (`dataset_num`, `deleted`, `status`),
  KEY `idx_case_gen_ws` (`workspace_num`, `deleted`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测集自动生成Case任务';

INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`)
SELECT '/api/v1/evaluation/dataset/command/startCaseGen','["evaluation:dataset:update"]','评测集自动生成Case启动',516,NOW(3),NOW(3)
WHERE NOT EXISTS (
  SELECT 1 FROM `route_permission` WHERE `path_pattern`='/api/v1/evaluation/dataset/command/startCaseGen'
);

INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`)
SELECT '/api/v1/evaluation/dataset/command/retryCaseGen','["evaluation:dataset:update"]','评测集自动生成Case重试',517,NOW(3),NOW(3)
WHERE NOT EXISTS (
  SELECT 1 FROM `route_permission` WHERE `path_pattern`='/api/v1/evaluation/dataset/command/retryCaseGen'
);
