-- 用户长期记忆：按工作空间 + Agent + 用户一份，与会话沙箱隔离
CREATE TABLE IF NOT EXISTS agent_user_memory (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    num                VARCHAR(64)   NOT NULL COMMENT '业务编号',
    workspace_num      VARCHAR(64)   NOT NULL COMMENT '工作空间编号',
    agent_num          VARCHAR(64)   NOT NULL COMMENT 'Agent 编号',
    user_id            VARCHAR(64)   NOT NULL COMMENT '用户标识',
    memory_md          MEDIUMTEXT    NULL COMMENT 'Harness MEMORY.md',
    daily_ledger_json  MEDIUMTEXT    NULL COMMENT '按日账本 JSON',
    create_no          VARCHAR(64)   NULL,
    update_no          VARCHAR(64)   NULL,
    deleted            TINYINT(1)    NOT NULL DEFAULT 0,
    create_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_aum_num (num),
    UNIQUE KEY uk_aum_owner (workspace_num, agent_num, user_id)
) COMMENT='用户长期记忆（跨会话，需 Agent 开启后才写入）';
