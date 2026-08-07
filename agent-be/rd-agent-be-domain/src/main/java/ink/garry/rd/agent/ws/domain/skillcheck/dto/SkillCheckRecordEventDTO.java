package ink.garry.rd.agent.ws.domain.skillcheck.dto;

import ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SkillCheckRecord 领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.SKILL_CHECK_RECORDED} 等事件类型解码使用。
 * 仅含属性，无业务逻辑；放在 {@code skillcheck.dto} 子包下（与既有 skill 域
 * {@code SkillDomainEventDTO} 的事件载荷集中放 dto/ 子包的项目专项规范一致）。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCheckRecordEventDTO {

    /** 检测记录业务编号（SCR...）。 */
    private String num;

    /** 所属 Skill 业务编号。 */
    private String skillNum;

    /** 检测的目标版本号。 */
    private String version;

    /** 整体检测结果（PASS / FAIL）。 */
    private SkillCheckResult result;

    /** 操作人（触发检测者）用户 ID。 */
    private String operatorId;

    /** 事件实际发生时间。 */
    private LocalDateTime occurredAt;

    /**
     * 从 SkillCheckRecord 聚合根快照构造事件载荷。
     *
     * @param record     检测记录聚合根
     * @param operatorId 操作人用户 ID
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static SkillCheckRecordEventDTO from(SkillCheckRecord record, String operatorId) {
        return SkillCheckRecordEventDTO.builder()
                .num(record.getNum())
                .skillNum(record.getSkillNum())
                .version(record.getVersion())
                .result(record.getResult())
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
