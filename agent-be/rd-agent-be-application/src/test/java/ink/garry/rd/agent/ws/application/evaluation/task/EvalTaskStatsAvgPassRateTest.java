package ink.garry.rd.agent.ws.application.evaluation.task;

import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 评测统计平均通过率计算单测。
 */
class EvalTaskStatsAvgPassRateTest {

    @Test
    void avgPassRate_ignoresEmptyTotals_andAverages() {
        EvalTaskEntity a = new EvalTaskEntity();
        a.setPassedCount(8);
        a.setTotalCount(10); // 80%
        EvalTaskEntity b = new EvalTaskEntity();
        b.setPassedCount(1);
        b.setTotalCount(1); // 100%
        EvalTaskEntity empty = new EvalTaskEntity();
        empty.setPassedCount(0);
        empty.setTotalCount(0);
        Double avg = EvalTaskQueryService.computeAvgPassRate(List.of(a, b, empty));
        assertEquals(90.0, avg);
    }

    @Test
    void avgPassRate_empty_returnsNull() {
        assertNull(EvalTaskQueryService.computeAvgPassRate(List.of()));
        assertNull(EvalTaskQueryService.computeAvgPassRate(null));
    }
}
