package ink.garry.rd.agent.ws.application.evaluation.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskVO;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskPageQuery;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetMapper;
import ink.garry.rd.agent.ws.infra.evaluation.grader.mapper.EvalGraderMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskItemMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskItemScoreMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 评测任务分页按关联 Agent 筛选单测。
 */
@ExtendWith(MockitoExtension.class)
class EvalTaskQueryServicePageFilterTest {

    @Mock
    private EvalTaskMapper evalTaskMapper;
    @Mock
    private EvalTaskItemMapper evalTaskItemMapper;
    @Mock
    private EvalTaskItemScoreMapper evalTaskItemScoreMapper;
    @Mock
    private EvalDatasetMapper evalDatasetMapper;
    @Mock
    private EvalGraderMapper evalGraderMapper;

    @InjectMocks
    private EvalTaskQueryService queryService;

    @Test
    void page_withAgentNum_mapsResult() {
        EvalTaskEntity e = new EvalTaskEntity();
        e.setNum("ETK1");
        e.setWorkspaceNum("WS1");
        e.setName("任务1");
        e.setAgentNum("AGT-1");
        e.setBindMode("AGENT");
        e.setStatus("FINISHED");
        e.setGraderBindingsJson("[]");

        Page<EvalTaskEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(e));
        page.setTotal(1);
        when(evalTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        TaskPageQuery query = new TaskPageQuery();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setAgentNum("AGT-1");

        PageVO<EvalTaskVO> result = queryService.page(query, "WS1");
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("ETK1", result.getList().get(0).getNum());
        assertEquals("AGT-1", result.getList().get(0).getAgentNum());
    }
}
