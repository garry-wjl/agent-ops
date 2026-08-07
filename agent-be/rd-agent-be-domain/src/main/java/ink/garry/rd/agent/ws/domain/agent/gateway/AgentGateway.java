package ink.garry.rd.agent.ws.domain.agent.gateway;

import ink.garry.rd.agent.ws.domain.agent.Agent;

import java.util.List;

/**
 * Agent 聚合网关（生成业务编码 + 仓储 3 方法之外的读能力，实现位于 infra）。
 * <p>
 * 按"一聚合一个 Gateway"约定：业务编码生成、列表 / 校验类读查询统一收敛于此，
 * 避免污染 Repository 契约。
 */
public interface AgentGateway {

    /**
     * 生成 Agent 业务编号（前缀 AGT）。
     *
     * @return 形如 AGT20260511XXXXXX
     */
    String generateAgentNum();

    /**
     * 生成调用链 trace_id，用于 SSE invoke 时贯穿日志与审计。
     *
     * @return 全局唯一 traceId
     */
    String generateTraceId();

    /**
     * 分页查询（按创建方式 / 类型 / 模型 / 状态 / 关键词）。
     * 用于管理后台 Agent 列表页与挂载下拉。
     *
     * @param condition 过滤条件 + 分页参数
     * @return 当前页数据
     */
    PageResult<Agent> pageQuery(AgentPageCondition condition);

    /**
     * 校验同 workspace 下 name 是否已存在（创建/重命名时调用）。
     * <p>
     * 唯一性边界 = 工作空间；排除 deleted=1、sandbox=1 的记录。
     *
     * @param workspaceNum 归属工作空间业务编号（前缀 WS-）
     * @param name         新名称
     * @return true=已存在
     */
    boolean existsByWorkspaceAndName(String workspaceNum, String name);

    /**
     * 校验给定 nums 是否都是 NORMAL 类型（用于子 Agent 候选校验）。
     * SUPERVISOR / ROUTER 不允许嵌套，必须由 NORMAL 子 Agent 组成。
     *
     * @param agentNums 候选 Agent 业务编号列表
     * @return true=全部为 NORMAL
     */
    boolean allAreNormal(List<String> agentNums);

    /** 分页结果（domain 层简单容器，避免依赖 client） */
    record PageResult<T>(Long total, List<T> list) {}

    /** 分页查询条件（所有字段可空，按字段动态拼接 where） */
    record AgentPageCondition(
            String creationMode,
            String agentType,
            String model,
            String status,
            String keyword,
            Integer pageNo,
            Integer pageSize) {}
}
