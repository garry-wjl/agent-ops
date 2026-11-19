-- Agent Team：编排定义、一次运行，以及会话要挂载的运行级 OSS 目录。

CREATE TABLE `agent_team` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '团队业务编号',
  `workspace_num` varchar(64) NOT NULL COMMENT '工作空间编号',
  `name` varchar(128) NOT NULL COMMENT '名称',
  `description` varchar(500) DEFAULT NULL COMMENT '说明',
  `mode` varchar(32) NOT NULL COMMENT 'orchestration 或 leader',
  `status` varchar(16) NOT NULL COMMENT 'DRAFT 或 PUBLISHED',
  `definition_json` mediumtext NOT NULL COMMENT '画布或 Leader/Worker JSON',
  `create_no` varchar(64) NOT NULL COMMENT '创建人',
  `update_no` varchar(64) NOT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_team_num` (`num`),
  KEY `idx_agent_team_ws` (`workspace_num`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Team 定义';

CREATE TABLE `agent_team_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '运行业务编号',
  `team_num` varchar(64) NOT NULL COMMENT '团队编号',
  `workspace_num` varchar(64) NOT NULL COMMENT '工作空间编号',
  `team_name` varchar(128) NOT NULL COMMENT '启动时的名称',
  `mode` varchar(32) NOT NULL COMMENT 'orchestration 或 leader',
  `input_text` text NOT NULL COMMENT '本次任务说明',
  `status` varchar(32) NOT NULL COMMENT 'running/waiting/succeeded/failed/cancelled',
  `oss_sub_path` varchar(256) NOT NULL COMMENT '共享目录 OSS 前缀 workspace/runs/run',
  `snapshot_json` mediumtext NOT NULL COMMENT '启动时冻结的定义',
  `runtime_json` mediumtext NOT NULL COMMENT '节点或 Leader 运行状态',
  `create_no` varchar(64) NOT NULL COMMENT '创建人',
  `update_no` varchar(64) NOT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_team_run_num` (`num`),
  KEY `idx_agent_team_run_ws` (`workspace_num`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Team 一次运行';

CREATE TABLE `session_run_workspace` (
  `session_num` varchar(64) NOT NULL COMMENT '会话编号',
  `run_num` varchar(64) NOT NULL COMMENT '运行编号',
  `sub_path` varchar(256) NOT NULL COMMENT '要挂到容器工作目录的 OSS 前缀',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`session_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话挂载的编排运行目录';
