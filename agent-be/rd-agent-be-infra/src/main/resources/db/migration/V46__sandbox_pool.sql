-- 沙箱热池 / 会话级运行时实例
-- 资产侧：池开关与水位；运行时表：IDLE/BOUND 实例与会话绑定

ALTER TABLE sandbox
    ADD COLUMN pool_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用热池' AFTER sandbox_instance_id,
    ADD COLUMN pool_size INT NOT NULL DEFAULT 1 COMMENT '热池常驻 IDLE 目标数' AFTER pool_enabled,
    ADD COLUMN max_concurrent INT NOT NULL DEFAULT 8 COMMENT '该资产最大活实例数' AFTER pool_size,
    ADD COLUMN session_idle_ttl_minutes INT NOT NULL DEFAULT 10 COMMENT '会话空闲回收 TTL（分钟）' AFTER max_concurrent;

CREATE TABLE IF NOT EXISTS sandbox_runtime_instance (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    num             VARCHAR(64)  NOT NULL COMMENT '运行时实例业务编号 SRI...',
    sandbox_num     VARCHAR(64)  NOT NULL COMMENT '沙箱资产编号',
    workspace_num   VARCHAR(64)  NOT NULL COMMENT '工作空间编号',
    opensandbox_instance_id VARCHAR(128) NOT NULL COMMENT 'OpenSandbox 容器 id',
    status          VARCHAR(32)  NOT NULL COMMENT 'IDLE / BOUND',
    session_num     VARCHAR(64)  NULL COMMENT '绑定会话编号（BOUND 时非空）',
    last_active_at  DATETIME(3)  NULL COMMENT '最近活跃时间',
    idle_since      DATETIME(3)  NULL COMMENT '进入 IDLE 时间',
    create_no       VARCHAR(64)  NULL,
    update_no       VARCHAR(64)  NULL,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sri_num (num),
    KEY idx_sri_sandbox_status (sandbox_num, status, deleted),
    KEY idx_sri_session (session_num, deleted),
    KEY idx_sri_os_id (opensandbox_instance_id, deleted)
) COMMENT='沙箱运行时容器实例（会话绑定 / 热池）';
