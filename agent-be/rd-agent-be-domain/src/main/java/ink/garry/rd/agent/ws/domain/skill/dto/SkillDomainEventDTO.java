package ink.garry.rd.agent.ws.domain.skill.dto;

import ink.garry.rd.agent.ws.domain.skill.Skill;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillSource;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.SKILL_*} 事件类型解码使用。
 * <p>
 * v2.5 设计：
 * <ul>
 *   <li>放在 {@code domain.skill.dto} 子包下（按本项目约定，事件载荷 DTO 集中在 dto/ 子包，
 *       与通用六层模板"四子包"约束有差异，属本项目专项规范）；</li>
 *   <li>仅含属性，无业务逻辑；同时承载 Skill 本身字段与某些事件特有字段（如 version、operatorId）；</li>
 *   <li>对应 {@code SkillVersionPublishedEvent} 等 Spring 事件场景的载荷数据。</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillDomainEventDTO {

    /** Skill 业务编号（SKL...）。 */
    private String num;

    /** Skill 展示名称。 */
    private String name;

    /** 涉及的版本号（适用 publish / rollback 等事件；其他事件可为 null）。 */
    private String version;

    /** Skill 描述信息（事件发生时的当前快照）。 */
    private String description;

    /** Skill 标签数组（事件发生时的当前快照）。 */
    private List<String> tags;

    /** Skill 来源（SELF / COMPANY）。 */
    private SkillSource source;

    /** Skill 负责人用户 ID。 */
    private String ownerUserId;

    /** Skill 生命周期状态（事件发生后的状态）。 */
    private SkillStatus status;

    /** 操作人用户 ID（用于审计与事件链路追溯）。 */
    private String operatorId;

    /** 事件实际发生时间（领域内时钟取 LocalDateTime.now()）。 */
    private LocalDateTime occurredAt;

    /**
     * 从 Skill 聚合根快照构造事件载荷。
     *
     * @param skill      Skill 聚合根
     * @param version    本次事件涉及的版本号（如 publish 的新版本号；非版本类事件传 null）
     * @param operatorId 操作人用户 ID
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static SkillDomainEventDTO from(Skill skill, String version, String operatorId) {
        return SkillDomainEventDTO.builder()
                .num(skill.getNum())
                .name(skill.getName())
                .version(version != null ? version : skill.getCurrentVersionNum())
                .description(skill.getDescription())
                .tags(skill.getTags())
                .source(skill.getSource())
                .ownerUserId(skill.getOwnerUserId())
                .status(skill.getStatus())
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
