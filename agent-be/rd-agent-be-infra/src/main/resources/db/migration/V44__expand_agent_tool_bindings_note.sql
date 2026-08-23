-- V44: Agent 工具绑定支持「整组 + 具体工具」双模式（说明性迁移）。
--
-- - 无 itemKind：整组挂载（兼容原 toolNums / 仅 toolNum 的 toolRefs）
-- - 有 itemKind（FC_ENDPOINT / MCP_TOOL）：具体工具绑定
--
-- 可选数据展开（非必须）：
--   python3 agent-be/scripts/migrate-expand-tool-bindings.py [--dry-run]
--
-- 本 SQL 不改表结构（toolRefs 为 JSON 内字段扩展）。

SELECT 1;
