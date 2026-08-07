package ink.garry.rd.agent.ws.domain.prompt.dto;

import ink.garry.rd.agent.ws.domain.prompt.Prompt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prompt 领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.PROMPT_*} 事件类型解码使用。
 * <p>
 * 设计要点（与 {@code SkillDomainEventDTO} / {@code ToolDomainEventDTO} 一致）：
 * <ul>
 *   <li>放在 {@code domain.prompt.dto} 子包下（本项目约定：事件载荷 DTO 集中在 dto/ 子包，
 *       属本项目专项规范）；</li>
 *   <li>仅含属性，无业务逻辑；承载 Prompt 当前快照字段与操作人；</li>
 *   <li>本期 Prompt 无消费方，仅留审计 / 扩展位。</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromptDomainEventDTO {

    /** Prompt 业务编号（PRM...）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** Prompt 引用键（事件发生时的当前快照）。 */
    private String promptKey;

    /** Prompt 描述信息（事件发生时的当前快照）。 */
    private String description;

    /** Prompt 标签数组（事件发生时的当前快照）。 */
    private List<String> tags;

    /** 操作人用户 ID（用于审计与事件链路追溯）。 */
    private String operatorId;

    /** 事件实际发生时间（领域内时钟取 LocalDateTime.now()）。 */
    private LocalDateTime occurredAt;

    /**
     * 从 Prompt 聚合根快照构造事件载荷。
     * <p>
     * 不携带 {@code templateContent}（模板原文可能较大，事件链路无需透传）。
     *
     * @param prompt     Prompt 聚合根
     * @param operatorId 操作人用户 ID
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static PromptDomainEventDTO from(Prompt prompt, String operatorId) {
        return PromptDomainEventDTO.builder()
                .num(prompt.getNum())
                .workspaceNum(prompt.getWorkspaceNum())
                .promptKey(prompt.getPromptKey())
                .description(prompt.getDescription())
                .tags(prompt.getTags())
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
