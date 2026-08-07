package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * A2A 接入草稿请求参数（v2.6）。
 * <p>
 * 对应「A2A Agent 接入表单」流程：
 * <ul>
 *   <li>[暂存草稿]：所有字段可空，仅落库 status=DRAFT_ONLY，{@code nacosAgentName} 允许后填。</li>
 *   <li>[确认接入] 升级草稿（{@code agentNum} 传值）：将现有草稿升级为 status=PENDING_SYNC，
 *       订阅对应 {@code nacosAgentName}。</li>
 *   <li>[确认接入] 直接新建（{@code agentNum} 留空）：新建 A2A Agent 行，status=PENDING_SYNC。</li>
 * </ul>
 */
@Data
public class A2aDraftParam {

    /**
     * Nacos AI Server 中的 agent name；[确认接入] 时必填，[暂存草稿] 可空。
     * 后端 [确认接入] 校验：调 NacosAgentCardFetcher.fetch；失败抛
     * {@code BizCode.A2A_AGENT_NOT_FOUND_IN_NACOS / NACOS_UNREACHABLE}。
     */
    private String nacosAgentName;

    /** 显示名（草稿覆盖；[确认接入] 后会被 Nacos 同步覆盖） */
    private String displayName;

    /** 描述 */
    private String description;

    /** 备注（仅本地草稿可见） */
    private String remark;

    /**
     * 已存在的草稿 Agent num；
     * 「[继续接入] 升级草稿为正式记录」流程会传该字段，后端 update 现有草稿为 PENDING_SYNC；
     * 否则新建一行。
     */
    private String agentNum;
}
