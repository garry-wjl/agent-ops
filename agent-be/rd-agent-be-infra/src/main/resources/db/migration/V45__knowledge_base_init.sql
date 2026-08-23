-- 知识库管理初始化（v2.2 技术方案 §8.3）
CREATE TABLE workspace_kb_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workspace_num   VARCHAR(64)  NOT NULL,
    config_json     JSON         NOT NULL,
    create_no       VARCHAR(64)  DEFAULT NULL,
    update_no       VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_workspace_num (workspace_num, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_base (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    num                 VARCHAR(32)  NOT NULL,
    workspace_num       VARCHAR(64)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    description         VARCHAR(500) DEFAULT NULL,
    kb_type             VARCHAR(20)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    index_config        JSON         NOT NULL,
    source_config       JSON         DEFAULT NULL,
    retrieval_defaults  JSON         DEFAULT NULL,
    file_count          INT          NOT NULL DEFAULT 0,
    chunk_count         INT          NOT NULL DEFAULT 0,
    create_no           VARCHAR(64)  DEFAULT NULL,
    update_no           VARCHAR(64)  DEFAULT NULL,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_num (num, deleted),
    UNIQUE KEY uk_workspace_name (workspace_num, name, deleted),
    KEY idx_workspace (workspace_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_base_file (
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    num                     VARCHAR(32)  NOT NULL,
    kb_num                  VARCHAR(32)  NOT NULL,
    oss_file_id             VARCHAR(128) NOT NULL,
    file_name               VARCHAR(255) NOT NULL,
    mime_type               VARCHAR(128) DEFAULT NULL,
    file_size               BIGINT       NOT NULL DEFAULT 0,
    index_status            VARCHAR(32)  NOT NULL,
    index_config_snapshot   JSON         DEFAULT NULL,
    chunk_count             INT          NOT NULL DEFAULT 0,
    vector_doc_ids          JSON         DEFAULT NULL,
    error_message           VARCHAR(1000) DEFAULT NULL,
    indexed_at              DATETIME(3)  DEFAULT NULL,
    create_no               VARCHAR(64)  DEFAULT NULL,
    update_no               VARCHAR(64)  DEFAULT NULL,
    create_time             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_num (num, deleted),
    KEY idx_kb_num (kb_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kb_index_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    num             VARCHAR(32)  NOT NULL,
    kb_num          VARCHAR(32)  NOT NULL,
    file_num        VARCHAR(32)  DEFAULT NULL COMMENT 'REINDEX_ALL 父任务为 NULL',
    task_type       VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    payload         JSON         DEFAULT NULL,
    error_message   VARCHAR(1000) DEFAULT NULL,
    create_no       VARCHAR(64)  DEFAULT NULL,
    update_no       VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_num (num),
    KEY idx_kb_status (kb_num, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
