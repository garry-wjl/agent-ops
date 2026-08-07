package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

/**
 * 评测看板（Dashboard）全局统计 VO。
 * <p>
 * 用于评测首页的指标卡片：累计评测数、累计用例数、平均通过率。
 */
@Data
public class DashboardStatsVO {
    /** 累计评测总数 */
    private Long evaluationCount;
    /** 累计用例总数（跨所有评测求和） */
    private Long caseCount;
    /** 平均通过率，范围 [0, 1] */
    private Double averagePassRate;
}
