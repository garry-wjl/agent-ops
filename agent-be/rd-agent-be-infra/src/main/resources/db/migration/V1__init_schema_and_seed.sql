-- ====================================================================
-- rd-agent-be 首次上线数据库初始化脚本（单份，Flyway V1）
-- --------------------------------------------------------------------
-- 生成日期：2026-07-09
-- 数据来源：测试环境 rd_agent_be 库（rm-wz950hyg68l84xwz9.mysql...）定版快照
-- 策略：
--   - 结构：以测试库为准，但修正其历史污染（agent 表 comment 双重编码乱码、
--     skill/skill_version 停在 V5 未跑 V9 收敛），对齐到与当前代码一致的干净版。
--   - 种子数据：取测试库权限相关权威种子——
--       permission(55) / route_permission(58) / role(4 内置) /
--       role_permission(117) / workspace(仅 WS-DEFAULT，admin/member 清空)。
--   - 不含：业务运行时数据（agent/skill/model/... 等表仅建空表）；
--           user_workspace_role 不预置（新环境随用户登录由应用层写入）。
--   - 不含：flyway_schema_history（Flyway 启动时自建）。
-- 全新空库启动服务即自动执行本脚本，建全表 + 灌权限种子。
-- ====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `agent`;
CREATE TABLE `agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 AGT...',
  `name` varchar(128) NOT NULL COMMENT 'Agent 名称',
  `description` varchar(512) DEFAULT NULL COMMENT '描述',
  `tags` json DEFAULT NULL COMMENT '业务标签 JSON 数组(CONFIG/A2A 共用)',
  `creation_mode` varchar(16) NOT NULL COMMENT '创建方式 CONFIG/A2A',
  `agent_type` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '类型 NORMAL/SUPERVISOR/ROUTER',
  `owner_user_id` varchar(64) NOT NULL COMMENT '负责人 userId（A2A 取 Nacos 元数据 owner，缺失则 system）',
  `workspace_num` varchar(64) NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT_ONLY' COMMENT '状态 DRAFT_ONLY/PUBLISHED/OFFLINE（A2A 由 Nacos healthy 同步）',
  `current_version_num` varchar(64) DEFAULT NULL COMMENT '当前在线版本号（仅 CONFIG）',
  `config_snapshot` json DEFAULT NULL COMMENT 'v3.0：当前在线版本 snapshot 镜像（仅 CONFIG）；发布事务内同步更新；调试/评测/挂载下拉直接读，避免 join agent_version',
  `sandbox` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否空白沙盒 Agent',
  `a2a_source` json DEFAULT NULL COMMENT 'A2A 来源信息 JSON（仅 A2A）：nacosGroup/nacosService/instanceIp/instancePort/endpointPath/remoteVersion/remoteSkills/agentCardJson/lastSyncedAt/lastSyncEventType',
  `nacos_service_key` varchar(256) DEFAULT NULL COMMENT 'A2A 幂等键 = nacosGroup@@nacosService（仅 A2A）',
  `create_no` varchar(64) NOT NULL COMMENT '创建人（A2A 为 nacos-sync）',
  `update_no` varchar(64) NOT NULL COMMENT '更新人（A2A 为 nacos-sync）',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_num` (`num`),
  UNIQUE KEY `uk_agent_nacos_key` (`nacos_service_key`,`deleted`),
  KEY `idx_creation_mode` (`creation_mode`,`deleted`),
  KEY `idx_status` (`status`,`deleted`),
  KEY `idx_agent_workspace` (`workspace_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 元信息';

DROP TABLE IF EXISTS `agent_a2a_sync_history`;
CREATE TABLE `agent_a2a_sync_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `agent_num` varchar(64) NOT NULL COMMENT '所属 A2A Agent num',
  `remote_version` varchar(64) DEFAULT NULL COMMENT '本次同步获取到的远端版本号（取自 Agent Card version 字段，可空）',
  `sync_event_type` varchar(32) NOT NULL COMMENT '同步事件来源：INSTANCE_ADDED / INSTANCE_CHANGED / INSTANCE_REMOVED / POLLING_RECONCILE / MANUAL_RESYNC',
  `triggered_by` varchar(64) NOT NULL COMMENT '触发人：订阅 / 兜底轮询固定 nacos-sync；手动重新同步为操作用户 userId',
  `agent_card_json` mediumtext COMMENT '本次同步落地时的 Agent Card 完整 JSON，用于历史溯源',
  `synced_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '同步发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_synced_at` (`agent_num`,`synced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='A2A Nacos 同步历史';

DROP TABLE IF EXISTS `agent_api_key`;
CREATE TABLE `agent_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号 AK...',
  `agent_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属 Agent 业务编号',
  `workspace_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '冗余归属空间',
  `remark` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备注',
  `key_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256(明文),认证比对用',
  `key_cipher` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SecretCipher 可逆密文,小眼睛解密用',
  `key_prefix` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '明文前8位,列表掩码用',
  `last_used_at` datetime(3) DEFAULT NULL COMMENT '最近一次成功认证时间',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_agent_api_key_num` (`num`),
  UNIQUE KEY `uq_agent_api_key_hash` (`key_hash`),
  KEY `idx_agent_api_key_agent` (`agent_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent API 调用秘钥';

DROP TABLE IF EXISTS `agent_version`;
CREATE TABLE `agent_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 AVN...',
  `agent_num` varchar(64) NOT NULL COMMENT '所属 Agent num',
  `status` varchar(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'v3.0：DRAFT 草稿 / PUBLISHED 当前在线（current_flag=1） / ARCHIVED 历史已发布',
  `version_num` varchar(32) DEFAULT NULL COMMENT 'v1.0.0；DRAFT 时为 NULL，发布时按 changeLevel 计算后赋值',
  `semver_major` int DEFAULT NULL,
  `semver_minor` int DEFAULT NULL,
  `semver_patch` int DEFAULT NULL,
  `config_snapshot` json NOT NULL COMMENT '完整配置 snapshot',
  `remark` varchar(512) DEFAULT NULL COMMENT '发布备注（≥10字）；DRAFT 时为 NULL',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人 userId；DRAFT 时为 NULL',
  `published_at` timestamp(3) NULL DEFAULT NULL COMMENT '发布时间；DRAFT 时为 NULL',
  `current_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否当前在线版本 0/1',
  `editor_user_id` varchar(64) DEFAULT NULL COMMENT 'v3.0：当前编辑者（DRAFT 行用）',
  `lock_until` timestamp(3) NULL DEFAULT NULL COMMENT 'v3.0：草稿编辑锁过期时间',
  `create_no` varchar(64) NOT NULL,
  `update_no` varchar(64) NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version_num` (`num`),
  UNIQUE KEY `uk_agent_version_no` (`agent_num`,`version_num`),
  KEY `idx_agent_current` (`agent_num`,`current_flag`,`deleted`),
  KEY `idx_published_at` (`agent_num`,`published_at`),
  KEY `idx_agent_status` (`agent_num`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 版本快照（不可变）';

DROP TABLE IF EXISTS `eval_seed`;
CREATE TABLE `eval_seed` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 ESD...',
  `skill_num` varchar(64) NOT NULL COMMENT '关联的 Skill num',
  `input` mediumtext NOT NULL COMMENT '种子输入文本',
  `expected_output` mediumtext COMMENT '期望输出文本',
  `create_no` varchar(64) NOT NULL,
  `update_no` varchar(64) NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seed_num` (`num`),
  KEY `idx_seed_skill` (`skill_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测黄金集种子';

DROP TABLE IF EXISTS `evaluation`;
CREATE TABLE `evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 EVL...',
  `name` varchar(128) DEFAULT NULL COMMENT '评测名称（人类可读）',
  `agent_num` varchar(64) NOT NULL COMMENT '被评测的 Agent num',
  `agent_version_num` varchar(64) DEFAULT NULL COMMENT '被评测 Agent 版本 num；可空表示评测最新版本',
  `skill_num` varchar(64) DEFAULT NULL COMMENT '被评测 Skill num；可空表示对整个 Agent 评测',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / FINISHED / FAILED',
  `creator_user_id` varchar(64) NOT NULL COMMENT '评测发起人 userId',
  `total_case_count` int NOT NULL DEFAULT '0' COMMENT '评测用例总数（finish 时回填）',
  `passed_case_count` int NOT NULL DEFAULT '0' COMMENT '评测通过用例数（finish 时回填）',
  `failed_case_count` int NOT NULL DEFAULT '0' COMMENT '评测失败用例数（finish 时回填）',
  `create_no` varchar(64) NOT NULL,
  `update_no` varchar(64) NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_num` (`num`),
  KEY `idx_eval_agent` (`agent_num`,`deleted`),
  KEY `idx_eval_skill` (`skill_num`,`deleted`),
  KEY `idx_eval_status_create` (`status`,`create_time`),
  KEY `idx_eval_creator` (`creator_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测任务';

DROP TABLE IF EXISTS `evaluation_case`;
CREATE TABLE `evaluation_case` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 EVC...',
  `evaluation_num` varchar(64) NOT NULL COMMENT '所属评测的业务编号',
  `input` mediumtext NOT NULL COMMENT '用例输入文本',
  `expected_output` mediumtext COMMENT '用例期望输出',
  `actual_output` mediumtext COMMENT '实际输出（执行后回填）',
  `judge_result` mediumtext COMMENT 'Judge 评分结果 JSON/文本',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / PASSED / FAILED',
  `create_no` varchar(64) NOT NULL,
  `update_no` varchar(64) NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_eval_case_num` (`num`),
  KEY `idx_eval_case_eval` (`evaluation_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测执行用例';


DROP TABLE IF EXISTS `invocation_trace`;
CREATE TABLE `invocation_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 TRC...',
  `trace_id` varchar(64) NOT NULL COMMENT 'traceId',
  `session_num` varchar(64) DEFAULT NULL COMMENT '会话 num',
  `agent_num` varchar(64) NOT NULL COMMENT 'Agent num',
  `agent_version_num` varchar(64) NOT NULL COMMENT 'Agent version num',
  `caller_user_id` varchar(64) NOT NULL COMMENT '调用人 userId',
  `input_summary` varchar(512) DEFAULT NULL COMMENT '输入摘要',
  `output_summary` varchar(512) DEFAULT NULL COMMENT '输出摘要',
  `step_count` int DEFAULT NULL COMMENT '步骤数',
  `total_tokens` int DEFAULT NULL COMMENT 'token 数',
  `total_latency_ms` int DEFAULT NULL COMMENT '总耗时毫秒',
  `status` varchar(16) NOT NULL COMMENT 'SUCCESS/FAILED/TRUNCATED',
  `create_no` varchar(64) NOT NULL COMMENT '创建人',
  `update_no` varchar(64) NOT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invocation_num` (`num`),
  UNIQUE KEY `uk_trace_id` (`trace_id`),
  KEY `idx_agent_time` (`agent_num`,`create_time`),
  KEY `idx_session` (`session_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调试台调用审计';

DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 MSG...',
  `session_num` varchar(64) NOT NULL COMMENT '会话 num',
  `role` varchar(16) NOT NULL COMMENT 'USER/ASSISTANT/TOOL',
  `input_type` varchar(16) DEFAULT NULL COMMENT 'TEXT/JSON，仅 USER 消息必填',
  `content` mediumtext COMMENT '消息内容',
  `step_chain` json DEFAULT NULL COMMENT '工具调用步骤链',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '调用 traceId',
  `create_no` varchar(64) NOT NULL COMMENT '创建人',
  `update_no` varchar(64) NOT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `segments_json` json DEFAULT NULL COMMENT '助手消息按到达顺序的段列表(thinking/text/tool_use),与 FE AssistantSegment 同构',
  `content_blocks_json` json DEFAULT NULL COMMENT 'AgentScope Msg.content 原始 ContentBlock 列表,供 trace 导出 / 评测复用,不返 FE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_num` (`num`),
  KEY `idx_session_time` (`session_num`,`create_time`,`deleted`),
  KEY `idx_message_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调试台消息';

DROP TABLE IF EXISTS `model`;
CREATE TABLE `model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号 MDL+yyyyMMddHHmm+4位序号',
  `workspace_num` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '归属工作空间业务编号；系统模型为空',
  `scope` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SPACE' COMMENT '模型归属：SPACE=空间模型，PLATFORM=系统模型',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `model_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户填写的模型标识（空间内唯一）',
  `api_key_cipher` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API Key AES 密文（SecretCipher 加密，绝不存明文）',
  `api_key_prefix` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API Key 明文前缀，列表脱敏展示用（prefix+****）',
  `base_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型服务端点 Base URL',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ENABLED/DISABLED',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注，≤500 字',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `platform_model_id_key` varchar(128) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`scope` = _utf8mb4'PLATFORM') then `model_id` else NULL end)) STORED COMMENT '系统模型 model_id 唯一约束生成列',
  `platform_name_key` varchar(128) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`scope` = _utf8mb4'PLATFORM') then `name` else NULL end)) STORED COMMENT '系统模型 name 唯一约束生成列',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_model_num` (`num`),
  UNIQUE KEY `uq_model_ws_model_id` (`workspace_num`,`model_id`,`deleted`),
  UNIQUE KEY `uq_model_ws_name` (`workspace_num`,`name`,`deleted`),
  UNIQUE KEY `uq_model_platform_model_id` (`platform_model_id_key`,`deleted`),
  UNIQUE KEY `uq_model_platform_name` (`platform_name_key`,`deleted`),
  KEY `idx_model_ws_status` (`workspace_num`,`status`,`deleted`),
  KEY `idx_model_scope_status` (`scope`,`workspace_num`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 模型接入资源主表';

DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
  `code` varchar(64) NOT NULL COMMENT '权限编码 resource:action',
  `name` varchar(64) NOT NULL COMMENT '权限中文名',
  `resource_domain` varchar(32) NOT NULL COMMENT '资源域',
  `scope` varchar(16) NOT NULL DEFAULT 'SPACE' COMMENT 'PLATFORM=仅平台角色可分配；SPACE=空间角色可分配',
  `description` varchar(500) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`code`),
  KEY `idx_permission_domain` (`resource_domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限元数据表';

DROP TABLE IF EXISTS `prompt`;
CREATE TABLE `prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编码 PRM+yyyyMMddHHmm+序号，系统生成',
  `workspace_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `prompt_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Prompt 引用键，工作空间内唯一',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述，≤500 字',
  `template_content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板原文（含 {{变量}}，原样存储不解析）',
  `tags` json DEFAULT NULL COMMENT '标签数组，≤20 个',
  `owner_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '负责人/创建人用户 ID',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_prompt_num` (`num`),
  UNIQUE KEY `uq_prompt_ws_num` (`workspace_num`,`num`,`deleted`),
  UNIQUE KEY `uq_prompt_ws_key` (`workspace_num`,`prompt_key`,`deleted`),
  KEY `idx_prompt_ws` (`workspace_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt 提示词资产主表';

DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `num` varchar(64) NOT NULL COMMENT '业务编号，RL-PLATFORM-* / RL-SPACE-*',
  `name` varchar(64) NOT NULL COMMENT '角色名',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `scope` varchar(16) NOT NULL COMMENT 'PLATFORM / SPACE',
  `workspace_num` varchar(64) DEFAULT NULL COMMENT '所属空间；scope=SPACE 时必填',
  `builtin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否内置',
  `permission_codes` json NOT NULL COMMENT '权限码集合 JSON 数组',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED',
  `create_no` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_no` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_num` (`num`),
  UNIQUE KEY `uq_role_name_scope_ws` (`name`,`scope`,`workspace_num`),
  KEY `idx_role_scope_ws` (`scope`,`workspace_num`),
  KEY `idx_role_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_num` varchar(64) NOT NULL,
  `permission_code` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_perm` (`role_num`,`permission_code`),
  KEY `idx_role_perm_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';

DROP TABLE IF EXISTS `route_permission`;
CREATE TABLE `route_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `path_pattern` varchar(255) NOT NULL COMMENT 'Ant 风格路径，如 /api/v1/agents/create 或 /api/v1/agents/apiKey/command/**',
  `permission_codes` json NOT NULL COMMENT '任一命中即放行的权限码数组；空数组表示仅登录即可',
  `description` varchar(200) DEFAULT NULL COMMENT '该路由业务含义备注',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_route_path` (`path_pattern`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='路由-权限映射表，DB 作为唯一真相源，替代 RouteRoleMapping 硬编码';

DROP TABLE IF EXISTS `sandbox`;
CREATE TABLE `sandbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号 SBX+yyyyMMddHHmm+4位序号',
  `workspace_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '沙箱名称',
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CODE' COMMENT '沙箱类型：CODE=代码沙箱',
  `cpu` decimal(4,1) NOT NULL COMMENT 'CPU 核数，0.5 步进，区间 0.5~16',
  `memory_mb` int NOT NULL COMMENT '内存大小（MB），区间 128~65536',
  `alive_minutes` int NOT NULL COMMENT '容器存活时间（分钟），区间 1~1440',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/INITIALIZED/ONLINE/OFFLINE/FAILED',
  `remark` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注，≤100 字',
  `sandbox_instance_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'OpenSandbox 容器 id，草稿 / 失败态为空',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_sandbox_num` (`num`),
  UNIQUE KEY `uq_sandbox_ws_name` (`workspace_num`,`name`,`deleted`),
  KEY `idx_sandbox_ws_status` (`workspace_num`,`status`,`deleted`),
  KEY `idx_sandbox_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='沙箱资产主表';

DROP TABLE IF EXISTS `session`;
CREATE TABLE `session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 SES...',
  `agent_num` varchar(64) NOT NULL COMMENT 'Agent num',
  `agent_version_num` varchar(64) NOT NULL COMMENT '会话起始时锁定的 Agent 版本',
  `skill_hint` varchar(128) DEFAULT NULL COMMENT '调试 Skill 时的 skill_name',
  `creator_user_id` varchar(64) NOT NULL COMMENT '创建人 userId',
  `title` varchar(128) DEFAULT NULL COMMENT '会话标题',
  `last_message_at` timestamp(3) NULL DEFAULT NULL COMMENT '最后一条消息时间',
  `create_no` varchar(64) NOT NULL COMMENT '创建人',
  `update_no` varchar(64) NOT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_num` (`num`),
  KEY `idx_creator_agent` (`creator_user_id`,`agent_num`,`deleted`),
  KEY `idx_last_message` (`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调试台会话';

DROP TABLE IF EXISTS `skill`;
CREATE TABLE `skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 SKL...',
  `name` varchar(128) NOT NULL COMMENT 'Skill 名称（同一负责人下唯一）',
  `description` text COMMENT 'Skill 描述（最长 5000 字符,由 domain 层 Skill.domainValidate 硬约束）',
  `tags` json DEFAULT NULL COMMENT 'v2.0：自由标签数组',
  `source` varchar(16) NOT NULL DEFAULT 'SELF' COMMENT 'v2.0：来源 SELF/COMPANY',
  `owner_user_id` varchar(64) NOT NULL COMMENT '负责人 userId',
  `workspace_num` varchar(64) NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'v2.1：DRAFT / PUBLISHED / DEPRECATED（旧 DRAFT_ONLY 收敛为 DRAFT）',
  `current_version_num` varchar(64) DEFAULT NULL COMMENT '当前在线版本号；publish 写入、rollbackToVersion 切到目标版本号、unpublish 保留',
  `create_no` varchar(64) NOT NULL COMMENT '创建人',
  `update_no` varchar(64) NOT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_num` (`num`),
  UNIQUE KEY `uk_skill_owner_name` (`owner_user_id`,`name`,`deleted`),
  KEY `idx_skill_status` (`status`,`deleted`),
  KEY `idx_skill_workspace` (`workspace_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Skill 元信息';

DROP TABLE IF EXISTS `skill_check_record`;
CREATE TABLE `skill_check_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号 SCR+yyyyMMddHHmm+4位序号',
  `skill_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属 Skill 业务编号',
  `version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检测的目标版本号',
  `result` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '整体结果 PASS / FAIL',
  `size_result` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大小检测 PASS / FAIL / SKIPPED',
  `format_result` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '格式检测 PASS / FAIL / SKIPPED',
  `availability_result` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '可用性检测 PASS / FAIL / SKIPPED',
  `errors` text COLLATE utf8mb4_unicode_ci COMMENT '错误明细数组 JSON',
  `cost_ms` bigint NOT NULL DEFAULT '0' COMMENT '检测总耗时（毫秒）',
  `workspace_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '检测时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_scr_num` (`num`),
  KEY `idx_scr_skill` (`skill_num`),
  KEY `idx_scr_workspace` (`workspace_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 发布检测记录';

DROP TABLE IF EXISTS `skill_resource_file`;
CREATE TABLE `skill_resource_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `owner_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属类型 SKILL=草稿树 / VERSION=版本快照树',
  `owner_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属业务编号：skill.num 或 skill_version.num',
  `path` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源相对路径（树内唯一标识）',
  `type` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型 FILE / FOLDER',
  `parent_path` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父节点相对路径；根节点为 NULL',
  `encoding` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容编码 text / base64；文件夹为空',
  `mime` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 类型；文件夹为空',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '文件内容：文本原文或 Base64 串；文件夹为空',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_srf_owner_path` (`owner_type`,`owner_num`,`path`),
  KEY `idx_srf_owner` (`owner_type`,`owner_num`),
  KEY `idx_srf_parent` (`owner_type`,`owner_num`,`parent_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 资源文件树（内容入库，替代对象存储）';

DROP TABLE IF EXISTS `skill_version`;
CREATE TABLE `skill_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) NOT NULL COMMENT '业务编号 SVN...',
  `skill_num` varchar(64) NOT NULL COMMENT '所属 Skill num',
  `workspace_num` varchar(64) NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `version` varchar(32) NOT NULL COMMENT 'v2.2：版本号字符串，用户输入（约定 vX.Y.Z 但后端不解析）',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '发布时的 Skill 名称快照',
  `description` text COMMENT '发布时的描述快照（最长 5000 字符,由 domain 层 SkillVersion.domainValidate 硬约束）',
  `tags` json DEFAULT NULL COMMENT '发布时的标签数组快照',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'v2.8：版本生命周期 DRAFT / PUBLISHED / DEPRECATED',
  `create_no` varchar(64) NOT NULL,
  `update_no` varchar(64) NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version_num` (`num`),
  UNIQUE KEY `uk_skill_version_no` (`skill_num`,`version`),
  KEY `idx_skill_create_time` (`skill_num`,`create_time`),
  KEY `idx_skill_version_workspace` (`workspace_num`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Skill 版本快照（不可变）';

DROP TABLE IF EXISTS `tool`;
CREATE TABLE `tool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号 MCP/FC 前缀+yyyyMMddHHmm+4位序号',
  `workspace_num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WS-DEFAULT' COMMENT '归属工作空间业务编号',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具名称，工作空间内唯一（不区分类型）',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具描述，≤500 字',
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具类型：MCP / FUNCTION_CALL，建好不可改',
  `creation_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建方式：REMOTE/API_PACKAGE/OPENAPI_SPEC/MANUAL',
  `tags` json DEFAULT NULL COMMENT '标签数组，≤20 个',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DEPRECATED',
  `mcp_config_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MCP 配置子类型：LOCAL/REMOTE（仅 MCP-REMOTE）',
  `mcp_config` text COLLATE utf8mb4_unicode_ci COMMENT 'MCP 配置 JSON 原文，≤64KB（仅 MCP-REMOTE）',
  `proxy_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用 MCP 代理 0/1',
  `proxy_headers` json DEFAULT NULL COMMENT '透传请求头数组 [{name,value,description}]',
  `package_mode` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MCP API 打包方式：EXISTING_API/OPENAPI_PASTE',
  `source_fc_tool_num` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源 FC 工具 num（EXISTING_API 引用，动态跟随）',
  `open_api_spec` mediumtext COLLATE utf8mb4_unicode_ci COMMENT 'OpenAPI/Swagger 原文，≤1MB（OPENAPI_SPEC/OPENAPI_PASTE）',
  `base_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FC 手动录入 Base URL',
  `endpoints` json DEFAULT NULL COMMENT 'FC 手动录入端点数组',
  `endpoint_meta` json DEFAULT NULL COMMENT '发布时解析的端点元数据（端点数+摘要）',
  `owner_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '负责人/创建人用户 ID',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tool_num` (`num`),
  UNIQUE KEY `uq_tool_ws_name` (`workspace_num`,`name`,`deleted`),
  KEY `idx_tool_ws_type_status` (`workspace_num`,`type`,`status`,`deleted`),
  KEY `idx_tool_source_fc` (`source_fc_tool_num`),
  KEY `idx_tool_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具资产主表（MCP/FunctionCall）';

DROP TABLE IF EXISTS `user_workspace_role`;
CREATE TABLE `user_workspace_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `num` varchar(64) NOT NULL COMMENT '业务编码：UR-PLATFORM-{userId} / UR-SPACE-{workspaceNum}-{userId}',
  `workspace_num` varchar(64) NOT NULL COMMENT '工作空间编号；SYSTEM 表示平台角色',
  `user_id` varchar(64) NOT NULL COMMENT '工号',
  `role_nums` json NOT NULL COMMENT '该用户在该空间下持有的角色 num JSON 数组',
  `create_no` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_no` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_uwr_user_ws` (`workspace_num`,`user_id`),
  KEY `idx_uwr_user` (`user_id`),
  KEY `idx_uwr_ws` (`workspace_num`),
  KEY `idx_uwr_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-空间-角色关联表';

DROP TABLE IF EXISTS `workspace`;
CREATE TABLE `workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号 WS-...',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '空间名称',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '空间描述',
  `admin_list` json NOT NULL COMMENT '管理员工号数组 JSON，如 ["10001"]，至少 1 个',
  `member_list` json NOT NULL COMMENT '普通成员工号数组 JSON，如 ["10003"]，可空数组',
  `create_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人工号',
  `update_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人工号（兼任删除人）',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=正常 1=删除',
  `create_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（兼任删除时间）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_workspace_num` (`num`),
  UNIQUE KEY `uq_workspace_creator_name` (`create_no`,`name`,`deleted`),
  KEY `idx_workspace_creator` (`create_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间主表';


-- ====================================================================
-- 种子数据：权限元数据 / 路由权限映射 / 内置角色 / 角色-权限关联 / 默认工作空间
-- ====================================================================

-- permission：权限元数据（55 条）
INSERT INTO `permission` VALUES ('agent:create','创建 Agent','agent','SPACE','新建 Agent',101,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('agent:delete','删除 Agent','agent','SPACE','删除 / 下线 Agent',104,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('agent:invoke','调用 Agent','agent','SPACE','通过调试台 / 接口调用 Agent',106,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('agent:manage_apikey','管理 API Key','agent','SPACE','创建 / 删除 / 查看 Agent 的 API Key',107,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('agent:publish','发布 Agent','agent','SPACE','发布草稿为正式版本',105,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('agent:read','查看 Agent','agent','SPACE','查看 Agent 列表 + 详情 + 版本历史',102,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('agent:update','编辑 Agent','agent','SPACE','修改 Agent 配置（草稿态）',103,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('debug_console:access','访问调试台','debug_console','SPACE','进入调试台页面 + 发起调试调用',601,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('evaluation:create','创建评测任务','evaluation','SPACE','新建 Agent / Skill 评测任务',501,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('evaluation:delete','删除评测任务','evaluation','SPACE','删除评测记录',504,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('evaluation:execute','执行评测','evaluation','SPACE','运行评测任务（人工/自动）',503,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('evaluation:manage_seed','管理种子用例','evaluation','SPACE','创建 / 编辑 / 删除种子用例',505,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('evaluation:read','查看评测','evaluation','SPACE','查看评测列表 + 报告 + 历史',502,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('knowledge_base:create','创建知识库','knowledge_base','SPACE','新建知识库',401,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('knowledge_base:delete','删除知识库','knowledge_base','SPACE','删除知识库',404,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('knowledge_base:read','查看知识库','knowledge_base','SPACE','查看知识库列表 + 详情',402,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('knowledge_base:update','编辑知识库','knowledge_base','SPACE','修改知识库配置',403,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('model:create','新建模型','model','SPACE','新建模型配置',901,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('model:delete','删除模型','model','SPACE','删除草稿态模型',905,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('model:read','查看模型','model','SPACE','查看可用模型列表 + 详情',902,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('model:update','编辑模型','model','SPACE','修改模型配置',903,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('prompt:create','创建 Prompt','prompt','SPACE','新建 Prompt 模板',701,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('prompt:delete','删除 Prompt','prompt','SPACE','删除 Prompt',704,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('prompt:publish','发布 Prompt','prompt','SPACE','发布 Prompt 为 Active 状态',705,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('prompt:read','查看 Prompt','prompt','SPACE','查看 Prompt 列表 + 详情',702,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('prompt:update','编辑 Prompt','prompt','SPACE','修改 Prompt 配置',703,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('role_manage:create','新建角色','role_manage','PLATFORM','在空间内创建自定义角色',1101,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('role_manage:delete','删除角色','role_manage','PLATFORM','删除未被绑定的自定义角色',1103,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('role_manage:edit','编辑角色','role_manage','PLATFORM','编辑自定义角色名称/描述/权限',1102,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('sandbox:create','创建沙箱','sandbox','SPACE','新建沙箱配置',801,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('sandbox:delete','删除沙箱','sandbox','SPACE','删除沙箱',804,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('sandbox:read','查看沙箱','sandbox','SPACE','查看沙箱列表 + 详情',802,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('sandbox:update','编辑沙箱','sandbox','SPACE','修改沙箱配置',803,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('skill:create','创建 Skill','skill','SPACE','新建 Skill',201,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('skill:delete','删除 Skill','skill','SPACE','删除 / 弃用 Skill',204,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('skill:publish','发布 Skill','skill','SPACE','发布 Skill 草稿为正式版本',205,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('skill:read','查看 Skill','skill','SPACE','查看 Skill 列表 + 详情 + 版本历史',202,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('skill:sync','同步公司库','skill','SPACE','一键同步公司 Skill 库',206,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('skill:update','编辑 Skill','skill','SPACE','修改 Skill 配置（草稿态）',203,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('system:model_create','新建系统模型','system','PLATFORM','在系统设置中新建平台级模型配置',950,'2026-06-18 16:58:09.772','2026-06-18 16:58:19.564'),('system:model_delete','删除系统模型','system','PLATFORM','删除草稿态平台级模型',953,'2026-06-18 16:58:09.772','2026-06-18 16:58:19.564'),('system:model_read','查看系统模型','system','PLATFORM','查看系统设置中的平台级模型列表与详情',951,'2026-06-18 16:58:09.772','2026-06-18 16:58:19.564'),('system:model_update','编辑系统模型','system','PLATFORM','修改平台级模型配置/启用/禁用',952,'2026-06-18 16:58:09.772','2026-06-18 16:58:19.564'),('tool:create','创建工具','tool','SPACE','新建工具',301,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('tool:delete','删除工具','tool','SPACE','删除工具',304,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('tool:publish','发布工具','tool','SPACE','发布工具配置',305,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('tool:read','查看工具','tool','SPACE','查看工具列表 + 详情',302,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('tool:update','编辑工具','tool','SPACE','修改工具配置',303,'2026-06-17 14:58:20.984','2026-06-17 14:58:20.984'),('user_role:assign','分配平台角色','user_role','PLATFORM','给用户添加平台角色',1201,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('user_role:edit','编辑平台角色','user_role','PLATFORM','修改用户已绑定的平台角色',1202,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('user_role:remove','解除平台角色','user_role','PLATFORM','解除用户的部分或全部平台角色',1203,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('workspace:create','创建工作空间','workspace','PLATFORM','新建工作空间',1001,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('workspace:delete','删除工作空间','workspace','PLATFORM','逻辑删除工作空间',1004,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('workspace:read','查看工作空间','workspace','PLATFORM','查看我可见的工作空间列表',1002,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564'),('workspace:update','编辑工作空间','workspace','PLATFORM','修改工作空间属性 + 管理成员',1003,'2026-06-17 14:58:20.984','2026-06-18 16:58:19.564');

-- route_permission：路由-权限映射（58 条）
INSERT INTO `route_permission` VALUES (1,'/api/v1/agents/create','[\"agent:create\"]','Agent 新建',101,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(2,'/api/v1/agents/version/create','[\"agent:update\"]','Agent 版本新建',102,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(3,'/api/v1/agents/version/edit','[\"agent:update\"]','Agent 版本编辑',103,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(4,'/api/v1/agents/version/delete','[\"agent:delete\"]','Agent 版本删除',104,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(5,'/api/v1/agents/publish','[\"agent:publish\"]','Agent 发布',105,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(6,'/api/v1/agents/offline','[\"agent:delete\"]','Agent 下线',106,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(7,'/api/v1/agents/apiKey/command/**','[\"agent:manage_apikey\"]','Agent API Key 管理',107,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(8,'/api/v1/agents/invoke','[\"agent:invoke\"]','Agent 调用',108,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(9,'/api/v1/skill/command/create','[\"skill:create\"]','Skill 新建',201,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(10,'/api/v1/skill/command/update','[\"skill:update\"]','Skill 编辑',202,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(11,'/api/v1/skill/command/discardDraft','[\"skill:update\"]','Skill 丢弃草稿',203,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(12,'/api/v1/skill/command/publish','[\"skill:publish\"]','Skill 发布',204,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(13,'/api/v1/skill/command/rollback','[\"skill:publish\"]','Skill 回滚',205,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(14,'/api/v1/skill/command/unpublish','[\"skill:publish\"]','Skill 下架',206,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(15,'/api/v1/skill/command/delete','[\"skill:delete\"]','Skill 删除',207,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(16,'/api/v1/tool/command/create','[\"tool:create\"]','工具新建',301,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(17,'/api/v1/tool/command/update','[\"tool:update\"]','工具编辑',302,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(18,'/api/v1/tool/command/publish','[\"tool:publish\"]','工具发布',303,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(19,'/api/v1/tool/command/unpublish','[\"tool:publish\"]','工具下架',304,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(20,'/api/v1/tool/command/republish','[\"tool:publish\"]','工具重新发布',305,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(21,'/api/v1/tool/command/deleteDraft','[\"tool:delete\"]','工具草稿删除',306,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(22,'/api/v1/sandbox/create','[\"sandbox:create\"]','沙箱新建',401,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(23,'/api/v1/sandbox/update','[\"sandbox:update\"]','沙箱编辑',402,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(24,'/api/v1/sandbox/delete','[\"sandbox:delete\"]','沙箱删除',403,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(25,'/api/v1/sandbox/submit','[\"sandbox:update\"]','沙箱提交',404,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(26,'/api/v1/sandbox/offline','[\"sandbox:update\"]','沙箱下线',405,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(27,'/api/v1/sandbox/reonline','[\"sandbox:update\"]','沙箱重新上线',406,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(28,'/api/v1/prompt/command/create','[\"prompt:create\"]','Prompt 新建',501,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(29,'/api/v1/prompt/command/update','[\"prompt:update\"]','Prompt 编辑',502,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(30,'/api/v1/prompt/command/delete','[\"prompt:delete\"]','Prompt 删除',503,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(31,'/api/v1/model/create','[\"model:create\"]','模型新建',601,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(32,'/api/v1/model/update','[\"model:update\"]','模型编辑',602,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(33,'/api/v1/model/enable','[\"model:update\"]','模型启用',603,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(34,'/api/v1/model/disable','[\"model:update\"]','模型禁用',604,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(35,'/api/v1/model/delete','[\"model:delete\"]','模型删除',605,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(36,'/api/v1/model/page','[\"model:read\"]','模型列表',606,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(37,'/api/v1/model/detail','[\"model:read\"]','模型详情',607,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(38,'/api/v1/model/selectable','[\"model:read\"]','可选模型列表（旧）',608,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(39,'/api/v1/models/selectable','[\"model:read\"]','可选模型列表（新）',609,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(40,'/api/v1/system/model/page','[\"system:model_read\"]','系统模型列表',701,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(41,'/api/v1/system/model/detail','[\"system:model_read\"]','系统模型详情',702,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(42,'/api/v1/system/model/create','[\"system:model_create\"]','系统模型新建',703,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(43,'/api/v1/system/model/update','[\"system:model_update\"]','系统模型编辑',704,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(44,'/api/v1/system/model/enable','[\"system:model_update\"]','系统模型启用',705,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(45,'/api/v1/system/model/disable','[\"system:model_update\"]','系统模型禁用',706,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(46,'/api/v1/system/model/delete','[\"system:model_delete\"]','系统模型删除',707,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(47,'/api/v1/workspace/create','[\"workspace:create\"]','工作空间创建',801,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(48,'/api/v1/workspace/update','[\"workspace:update\"]','工作空间编辑',802,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(49,'/api/v1/workspace/delete','[\"workspace:delete\"]','工作空间删除',803,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(50,'/api/v1/roles/create','[\"role_manage:create\"]','空间角色新建',901,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(51,'/api/v1/roles/update','[\"role_manage:edit\"]','空间角色编辑',902,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(52,'/api/v1/roles/delete','[\"role_manage:delete\"]','空间角色删除',903,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(53,'/api/v1/roles/list-all','[\"role_manage:create\", \"role_manage:edit\", \"role_manage:delete\"]','全平台角色列表',904,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(54,'/api/v1/platform-roles/assign','[\"user_role:assign\"]','平台角色分配',905,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(55,'/api/v1/platform-roles/unassign','[\"user_role:remove\"]','平台角色解除',906,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(56,'/api/v1/platform-roles/save-user-roles','[\"user_role:edit\"]','平台角色批量保存',907,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(57,'/api/v1/platform-roles/role/**','[\"role_manage:create\", \"role_manage:edit\", \"role_manage:delete\"]','平台角色 CRUD',908,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981'),(58,'/api/v1/platform-roles/list-admins','[\"user_role:assign\", \"user_role:edit\", \"user_role:remove\"]','平台管理员列表',909,'2026-06-18 16:58:42.981','2026-06-18 16:58:42.981');

-- role：4 个内置角色（builtin=1, deleted=0）
INSERT INTO `role` VALUES (1,'RL-PLATFORM-ADMIN','平台管理员','拥有全部权限','PLATFORM',NULL,1,'[\"agent:create\", \"agent:read\", \"agent:update\", \"agent:delete\", \"agent:publish\", \"agent:invoke\", \"agent:manage_apikey\", \"skill:create\", \"skill:read\", \"skill:update\", \"skill:delete\", \"skill:publish\", \"skill:sync\", \"tool:create\", \"tool:read\", \"tool:update\", \"tool:delete\", \"tool:publish\", \"knowledge_base:create\", \"knowledge_base:read\", \"knowledge_base:update\", \"knowledge_base:delete\", \"evaluation:create\", \"evaluation:read\", \"evaluation:execute\", \"evaluation:delete\", \"evaluation:manage_seed\", \"debug_console:access\", \"prompt:create\", \"prompt:read\", \"prompt:update\", \"prompt:delete\", \"prompt:publish\", \"sandbox:create\", \"sandbox:read\", \"sandbox:update\", \"sandbox:delete\", \"model:create\", \"model:read\", \"model:update\", \"model:delete\", \"workspace:create\", \"workspace:read\", \"workspace:update\", \"workspace:delete\", \"role_manage:create\", \"role_manage:edit\", \"role_manage:delete\", \"user_role:assign\", \"user_role:edit\", \"user_role:remove\", \"system:model_create\", \"system:model_read\", \"system:model_update\", \"system:model_delete\"]','ENABLED','SYSTEM','2026-06-17 14:58:21.008','SYSTEM','2026-06-18 16:58:09.808',0),(2,'RL-SPACE-ADMIN','空间管理员','拥有该空间内全部操作权限','SPACE',NULL,1,'[\"agent:create\", \"agent:read\", \"agent:update\", \"agent:delete\", \"agent:publish\", \"agent:invoke\", \"agent:manage_apikey\", \"skill:create\", \"skill:read\", \"skill:update\", \"skill:delete\", \"skill:publish\", \"skill:sync\", \"tool:create\", \"tool:read\", \"tool:update\", \"tool:delete\", \"tool:publish\", \"knowledge_base:create\", \"knowledge_base:read\", \"knowledge_base:update\", \"knowledge_base:delete\", \"evaluation:create\", \"evaluation:read\", \"evaluation:execute\", \"evaluation:delete\", \"evaluation:manage_seed\", \"debug_console:access\", \"prompt:create\", \"prompt:read\", \"prompt:update\", \"prompt:delete\", \"prompt:publish\", \"sandbox:create\", \"sandbox:read\", \"sandbox:update\", \"sandbox:delete\", \"model:create\", \"model:read\", \"model:update\", \"model:delete\", \"workspace:read\", \"workspace:update\", \"workspace:delete\", \"role_manage:create\", \"role_manage:edit\", \"role_manage:delete\"]','ENABLED','SYSTEM','2026-06-17 14:58:21.020','SYSTEM','2026-06-18 16:57:56.716',0),(3,'RL-SPACE-MEMBER','空间成员','仅拥有该空间内的只读权限','SPACE',NULL,1,'[\"agent:read\", \"skill:read\", \"tool:read\", \"knowledge_base:read\", \"evaluation:read\", \"debug_console:access\", \"prompt:read\", \"sandbox:read\", \"model:read\", \"workspace:read\", \"agent:invoke\"]','ENABLED','SYSTEM','2026-06-17 14:58:21.033','SYSTEM','2026-06-17 14:58:21.033',0),(4,'RL-PLATFORM-USER','普通用户','平台默认角色，首次登录自动分配；仅拥有工作空间基础管理权限','PLATFORM',NULL,1,'[\"workspace:create\", \"workspace:read\", \"workspace:update\", \"workspace:delete\"]','ENABLED','SYSTEM','2026-06-18 16:58:56.458','SYSTEM','2026-06-18 16:58:56.458',0);

-- role_permission：内置角色-权限关联（117 条）
INSERT INTO `role_permission` VALUES (1,'RL-PLATFORM-ADMIN','agent:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(2,'RL-PLATFORM-ADMIN','agent:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(3,'RL-PLATFORM-ADMIN','agent:invoke','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(4,'RL-PLATFORM-ADMIN','agent:manage_apikey','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(5,'RL-PLATFORM-ADMIN','agent:publish','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(6,'RL-PLATFORM-ADMIN','agent:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(7,'RL-PLATFORM-ADMIN','agent:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(8,'RL-PLATFORM-ADMIN','debug_console:access','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(9,'RL-PLATFORM-ADMIN','evaluation:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(10,'RL-PLATFORM-ADMIN','evaluation:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(11,'RL-PLATFORM-ADMIN','evaluation:execute','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(12,'RL-PLATFORM-ADMIN','evaluation:manage_seed','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(13,'RL-PLATFORM-ADMIN','evaluation:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(14,'RL-PLATFORM-ADMIN','knowledge_base:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(15,'RL-PLATFORM-ADMIN','knowledge_base:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(16,'RL-PLATFORM-ADMIN','knowledge_base:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(17,'RL-PLATFORM-ADMIN','knowledge_base:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(18,'RL-PLATFORM-ADMIN','model:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(19,'RL-PLATFORM-ADMIN','model:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(21,'RL-PLATFORM-ADMIN','model:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(22,'RL-PLATFORM-ADMIN','model:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(23,'RL-PLATFORM-ADMIN','prompt:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(24,'RL-PLATFORM-ADMIN','prompt:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(25,'RL-PLATFORM-ADMIN','prompt:publish','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(26,'RL-PLATFORM-ADMIN','prompt:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(27,'RL-PLATFORM-ADMIN','prompt:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(28,'RL-PLATFORM-ADMIN','role_manage:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(29,'RL-PLATFORM-ADMIN','role_manage:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(30,'RL-PLATFORM-ADMIN','role_manage:edit','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(31,'RL-PLATFORM-ADMIN','sandbox:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(32,'RL-PLATFORM-ADMIN','sandbox:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(33,'RL-PLATFORM-ADMIN','sandbox:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(34,'RL-PLATFORM-ADMIN','sandbox:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(35,'RL-PLATFORM-ADMIN','skill:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(36,'RL-PLATFORM-ADMIN','skill:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(37,'RL-PLATFORM-ADMIN','skill:publish','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(38,'RL-PLATFORM-ADMIN','skill:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(39,'RL-PLATFORM-ADMIN','skill:sync','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(40,'RL-PLATFORM-ADMIN','skill:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(41,'RL-PLATFORM-ADMIN','tool:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(42,'RL-PLATFORM-ADMIN','tool:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(43,'RL-PLATFORM-ADMIN','tool:publish','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(44,'RL-PLATFORM-ADMIN','tool:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(45,'RL-PLATFORM-ADMIN','tool:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(46,'RL-PLATFORM-ADMIN','user_role:assign','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(47,'RL-PLATFORM-ADMIN','user_role:edit','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(48,'RL-PLATFORM-ADMIN','user_role:remove','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(49,'RL-PLATFORM-ADMIN','workspace:create','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(50,'RL-PLATFORM-ADMIN','workspace:delete','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(51,'RL-PLATFORM-ADMIN','workspace:read','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(52,'RL-PLATFORM-ADMIN','workspace:update','2026-06-17 14:58:21.054','2026-06-17 14:58:21.054'),(64,'RL-SPACE-ADMIN','agent:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(65,'RL-SPACE-ADMIN','agent:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(66,'RL-SPACE-ADMIN','agent:invoke','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(67,'RL-SPACE-ADMIN','agent:manage_apikey','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(68,'RL-SPACE-ADMIN','agent:publish','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(69,'RL-SPACE-ADMIN','agent:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(70,'RL-SPACE-ADMIN','agent:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(71,'RL-SPACE-ADMIN','debug_console:access','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(72,'RL-SPACE-ADMIN','evaluation:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(73,'RL-SPACE-ADMIN','evaluation:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(74,'RL-SPACE-ADMIN','evaluation:execute','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(75,'RL-SPACE-ADMIN','evaluation:manage_seed','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(76,'RL-SPACE-ADMIN','evaluation:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(77,'RL-SPACE-ADMIN','knowledge_base:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(78,'RL-SPACE-ADMIN','knowledge_base:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(79,'RL-SPACE-ADMIN','knowledge_base:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(80,'RL-SPACE-ADMIN','knowledge_base:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(81,'RL-SPACE-ADMIN','model:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(82,'RL-SPACE-ADMIN','model:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(84,'RL-SPACE-ADMIN','model:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(85,'RL-SPACE-ADMIN','model:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(86,'RL-SPACE-ADMIN','prompt:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(87,'RL-SPACE-ADMIN','prompt:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(88,'RL-SPACE-ADMIN','prompt:publish','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(89,'RL-SPACE-ADMIN','prompt:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(90,'RL-SPACE-ADMIN','prompt:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(91,'RL-SPACE-ADMIN','role_manage:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(92,'RL-SPACE-ADMIN','role_manage:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(93,'RL-SPACE-ADMIN','role_manage:edit','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(94,'RL-SPACE-ADMIN','sandbox:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(95,'RL-SPACE-ADMIN','sandbox:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(96,'RL-SPACE-ADMIN','sandbox:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(97,'RL-SPACE-ADMIN','sandbox:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(98,'RL-SPACE-ADMIN','skill:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(99,'RL-SPACE-ADMIN','skill:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(100,'RL-SPACE-ADMIN','skill:publish','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(101,'RL-SPACE-ADMIN','skill:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(102,'RL-SPACE-ADMIN','skill:sync','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(103,'RL-SPACE-ADMIN','skill:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(104,'RL-SPACE-ADMIN','tool:create','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(105,'RL-SPACE-ADMIN','tool:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(106,'RL-SPACE-ADMIN','tool:publish','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(107,'RL-SPACE-ADMIN','tool:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(108,'RL-SPACE-ADMIN','tool:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(109,'RL-SPACE-ADMIN','workspace:delete','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(110,'RL-SPACE-ADMIN','workspace:read','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(111,'RL-SPACE-ADMIN','workspace:update','2026-06-17 14:58:21.064','2026-06-17 14:58:21.064'),(127,'RL-SPACE-MEMBER','agent:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(128,'RL-SPACE-MEMBER','skill:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(129,'RL-SPACE-MEMBER','tool:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(130,'RL-SPACE-MEMBER','knowledge_base:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(131,'RL-SPACE-MEMBER','evaluation:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(132,'RL-SPACE-MEMBER','debug_console:access','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(133,'RL-SPACE-MEMBER','prompt:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(134,'RL-SPACE-MEMBER','sandbox:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(135,'RL-SPACE-MEMBER','model:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(136,'RL-SPACE-MEMBER','workspace:read','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(137,'RL-SPACE-MEMBER','agent:invoke','2026-06-17 14:58:21.078','2026-06-17 14:58:21.078'),(139,'RL-PLATFORM-ADMIN','system:model_create','2026-06-18 16:58:09.824','2026-06-18 16:58:09.824'),(140,'RL-PLATFORM-ADMIN','system:model_read','2026-06-18 16:58:09.824','2026-06-18 16:58:09.824'),(141,'RL-PLATFORM-ADMIN','system:model_update','2026-06-18 16:58:09.824','2026-06-18 16:58:09.824'),(142,'RL-PLATFORM-ADMIN','system:model_delete','2026-06-18 16:58:09.824','2026-06-18 16:58:09.824'),(143,'RL-PLATFORM-USER','workspace:create','2026-06-18 16:58:56.471','2026-06-18 16:58:56.471'),(144,'RL-PLATFORM-USER','workspace:read','2026-06-18 16:58:56.471','2026-06-18 16:58:56.471'),(145,'RL-PLATFORM-USER','workspace:update','2026-06-18 16:58:56.471','2026-06-18 16:58:56.471'),(146,'RL-PLATFORM-USER','workspace:delete','2026-06-18 16:58:56.471','2026-06-18 16:58:56.471');

-- workspace：默认工作空间 WS-DEFAULT（admin/member 清空，新环境随用户登录填充）
INSERT INTO `workspace` VALUES (1,'WS-DEFAULT','默认工作空间','平台默认工作空间，承载存量资产','[]','[]','system','system',0,'2026-06-04 07:34:46.679','2026-06-04 07:34:46.679');

SET FOREIGN_KEY_CHECKS = 1;
