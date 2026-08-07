package ink.garry.rd.agent.ws.facade.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 调用上下文 DTO — 给 application 层 Runner / infra 层 Builder 使用的最小数据集合。
 * <p>
 * 用途:{@code AgentQueryService.loadAgentForInvoke(num)} 返回本 DTO,供
 * {@code ConfigAgentRunner} 通过 {@code ConfigAgentBuilder.build(dto, snap)} 装配
 * spring-ai-alibaba {@code Agent} 实例。
 * <p>
 * <b>分层规则</b>(详见 {@code docs/CODING-CONVENTIONS.md §3.1}):
 * <ul>
 *   <li>放在 facade 层,因 infra 层 Builder 与 application 层 Runner 都需要引用,
 *       facade 是两者共同可见的最低层;</li>
 *   <li>facade 不依赖 domain,故枚举字段用 String 存储 name(),
 *       与 {@code AgentDetailVO} 等出参对齐;</li>
 *   <li>QueryService 不允许返回 VO(VO 专给 Controller),也不允许返回 Domain Entity(application 不直传 domain);
 *       故另立此 DTO 作为跨层调用契约。</li>
 * </ul>
 * <p>
 * <b>字段最小化原则</b>:只携带 {@code ConfigAgentBuilder} 真正用到的字段
 * ({@code num / name / description / agentType / creationMode / status}),不包含 {@code ConfigSnapshot}
 * / {@code A2aSourceInfo} 等大对象;snapshot 由 Runner 通过 {@code InvokeContext} 单独传入。
 *
 * @see AgentDomainEventDTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentInvokeDTO {

    /** Agent 业务编号,前缀 AGT;跨聚合引用 ID,永不为空 */
    private String num;

    /** Agent 显示名,作为 spring-ai-alibaba Agent.name 出现在日志 / trace 中 */
    private String name;

    /** Agent 描述,作为 spring-ai-alibaba Agent.description;为空时由 Builder 兜底为空串 */
    private String description;

    /** 行为类型 NORMAL / SUPERVISOR / ROUTER;取自 {@code AgentType.name()};Builder 据此分支构建 */
    private String agentType;

    /** 创建方式 CONFIG / A2A;取自 {@code CreationMode.name()};Runner 用本字段做防御性校验 */
    private String creationMode;

    /** 生命周期状态 DRAFT_ONLY / PUBLISHED / OFFLINE;取自 {@code AgentStatus.name()} */
    private String status;
}
