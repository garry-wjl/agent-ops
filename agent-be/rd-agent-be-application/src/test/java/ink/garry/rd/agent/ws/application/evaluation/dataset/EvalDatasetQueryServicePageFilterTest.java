package ink.garry.rd.agent.ws.application.evaluation.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DatasetPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetMapper;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetRowMapper;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 评测集分页按关联 Agent 筛选单测。
 */
@ExtendWith(MockitoExtension.class)
class EvalDatasetQueryServicePageFilterTest {

    @Mock
    private EvalDatasetMapper evalDatasetMapper;
    @Mock
    private EvalDatasetVersionMapper evalDatasetVersionMapper;
    @Mock
    private EvalDatasetRowMapper evalDatasetRowMapper;

    @InjectMocks
    private EvalDatasetQueryService queryService;

    @Test
    void page_withAgentNum_mapsResult() {
        EvalDatasetEntity e = new EvalDatasetEntity();
        e.setNum("EDS1");
        e.setWorkspaceNum("WS1");
        e.setName("集1");
        e.setAgentNum("AGT-1");
        e.setType("AGENT");
        e.setStatus("DRAFT");

        Page<EvalDatasetEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(e));
        page.setTotal(1);
        when(evalDatasetMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        DatasetPageQuery query = new DatasetPageQuery();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setAgentNum("AGT-1");

        PageVO<EvalDatasetVO> result = queryService.page(query, "WS1");
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("EDS1", result.getList().get(0).getNum());
        assertEquals("AGT-1", result.getList().get(0).getAgentNum());
    }

    @Test
    void page_withoutAgentNum_returnsEmpty() {
        Page<EvalDatasetEntity> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(evalDatasetMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageVO<EvalDatasetVO> result = queryService.page(new DatasetPageQuery(), "WS1");
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getList().size());
    }
}
