-- V38__chat_attachment.sql
CREATE TABLE IF NOT EXISTS `chat_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num` VARCHAR(64) NOT NULL COMMENT '业务编号 CHA-...',
  `workspace_num` VARCHAR(64) NOT NULL COMMENT '工作空间',
  `file_id` VARCHAR(512) NOT NULL COMMENT 'OSS 对象 ID',
  `file_name` VARCHAR(256) NOT NULL COMMENT '原始文件名',
  `mime_type` VARCHAR(128) NOT NULL COMMENT 'MIME',
  `size_bytes` BIGINT NOT NULL COMMENT '字节数',
  `kind` VARCHAR(16) NOT NULL COMMENT 'IMAGE/FILE',
  `agent_num` VARCHAR(64) NULL COMMENT '关联 Agent，可空',
  `create_no` VARCHAR(64) NOT NULL COMMENT '创建人',
  `update_no` VARCHAR(64) NOT NULL COMMENT '更新人',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '禁止业务删除，恒 0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_attachment_file_id` (`file_id`),
  UNIQUE KEY `uk_chat_attachment_num` (`num`),
  KEY `idx_chat_attachment_ws_time` (`workspace_num`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天附件登记（永不删除）';
