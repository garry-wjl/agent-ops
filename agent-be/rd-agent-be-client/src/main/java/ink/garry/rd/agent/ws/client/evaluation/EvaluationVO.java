package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测视图 VO。
 * <p>
 * 列表/详情共享的头部字段（评测名称、所属 Agent/Skill、状态、用例统计与时间戳）。
 * 详情页可继承本类附加 cases 明细，见 {@link EvaluationDetailVO}。
 */
@Data
public class EvaluationVO {
    /** 评测业务编号 */
    private String num;
    /** 评测名称（人类可读） */
    private String name;
    /** 被评测 Agent 业务编号 */
    private String agentNum;
    /** 被评测 Agent 版本编号；为空表示对当前在线版本评测 */
    private String agentVersionNum;
    /** 被评测 Skill 业务编号；为空表示对整个 Agent 评测 */
    private String skillNum;
    /** 评测状态（如 PENDING/RUNNING/COMPLETED/FAILED） */
    private String status;
    /** 总用例数 */
    private Integer totalCaseCount;
    /** 通过用例数 */
    private Integer passedCaseCount;
    /** 失败用例数 */
    private Integer failedCaseCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
