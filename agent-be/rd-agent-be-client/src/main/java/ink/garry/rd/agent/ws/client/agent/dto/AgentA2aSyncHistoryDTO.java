package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A2A 同步历史 DTO — application 层 {@code AgentQueryService.findA2aSyncHistoryByAgentNum} 出参。
 * <p>
 * 用途:供 application 内部用例(非 Controller 直接出参)以及部分 application → application
 * 跨服务调用使用;Controller 出参请用 {@code client.agent.A2aSyncHistoryVO}。
 * <p>
 * <b>放在 client 而非 facade</b>:本 DTO 仅被 application / adapter 层消费,不被 infra 消费;
 * 详见 {@code docs/CODING-CONVENTIONS.md §3.1}。
 * <p>
 * <b>字段策略</b>:
 * <ul>
 *   <li>{@code syncEventType} 以 String 形式承载 {@code SyncEventType.name()},
 *       避免 client 层反向依赖 domain 枚举;</li>
 *   <li>{@code agentCardJson} 保持原始 JSON 字符串,由调用方按需 fastjson2 反序列化;</li>
 *   <li>本表无逻辑删除字段(append-only 审计表),DTO 不暴露 deleted 位。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentA2aSyncHistoryDTO {

    /** 自增主键 */
    private Long id;

    /** 所属 A2A Agent 业务编号 */
    private String agentNum;

    /** 远端版本号(取自 Agent Card version 字段,可空) */
    private String remoteVersion;

    /** 同步事件来源 NACOS_SUBSCRIBE / POLL_FALLBACK / MANUAL;取自 {@code SyncEventType.name()} */
    private String syncEventType;

    /** 触发人 userId(订阅 / 兜底轮询固定 nacos-sync) */
    private String triggeredBy;

    /**
     * 同步落地时的 Agent Card 完整 JSON。
     * <p>
     * 保持原始 JSON 字符串,由调用方按需 fastjson2 反序列化。
     */
    private String agentCardJson;

    /** 同步发生时间 */
    private LocalDateTime syncedAt;
}
