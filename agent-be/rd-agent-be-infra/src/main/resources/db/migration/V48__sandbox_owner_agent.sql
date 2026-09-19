-- 沙箱资产归属 Agent（规格元数据，禁止多 Agent 共享）
-- 同 Agent 可因版本复制持有多行历史沙箱，故不加 DB 唯一约束
ALTER TABLE sandbox
    ADD COLUMN owner_agent_num VARCHAR(64) NULL COMMENT '归属 Agent 业务编号；空=存量未归属' AFTER workspace_num;

CREATE INDEX idx_sandbox_owner_agent ON sandbox (workspace_num, owner_agent_num);

-- 关闭热池：存量一律按会话现开
UPDATE sandbox SET pool_enabled = 0 WHERE deleted = 0 AND pool_enabled = 1;
