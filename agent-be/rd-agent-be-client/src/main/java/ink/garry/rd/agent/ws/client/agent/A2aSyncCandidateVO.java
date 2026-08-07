package ink.garry.rd.agent.ws.client.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 兜底对账候选 VO（v2.6 / 架构重构）。
 * <p>
 * 由 {@code AgentQueryService.listA2aSyncCandidates()} 输出，{@code A2aNacosSyncListener.pollAll}
 * 据此遍历需要与 Nacos 对账的 A2A Agent。仅承载两个字段：业务编号 {@link #num} 与 Agent 名称
 * {@link #name}（同时也是 Nacos 订阅键 / 拉取键）。
 * <p>
 * 入选规则：{@code creationMode = A2A} 且 {@code status ∈ {PENDING_SYNC, PUBLISHED}}。
 * DRAFT_ONLY 尚未发起接入、OFFLINE 已下线均不参与对账。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2aSyncCandidateVO {

    /** Agent 业务编号（前缀 AGT），用于 createByNum 重建领域对象。 */
    private String num;

    /** Agent 名称，等同于 Nacos AgentCard.name / nacosService，用于 fetcher.fetch(name) 拉取最新源信息。 */
    private String name;
}
