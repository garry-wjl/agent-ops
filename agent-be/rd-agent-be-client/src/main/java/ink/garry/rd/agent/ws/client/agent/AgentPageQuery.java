package ink.garry.rd.agent.ws.client.agent;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 列表分页查询条件。
 * <p>
 * S2 内列表搜索仅按 num/name 模糊匹配 + 可选 creationMode 筛选；
 * 其余字段（agentType / model / status）保留以备 M3 扩展。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentPageQuery extends PageParam {
    /** 创建方式筛选：CONFIG / A2A；为空表示全部 */
    private String creationMode;
    /** 行为类型筛选 NORMAL / SUPERVISOR / ROUTER */
    private String agentType;
    /** LLM 模型筛选 */
    private String model;
    /** 状态筛选 DRAFT_ONLY / PUBLISHED / OFFLINE */
    private String status;
    /** 关键词模糊匹配 num / name */
    private String keyword;
}
