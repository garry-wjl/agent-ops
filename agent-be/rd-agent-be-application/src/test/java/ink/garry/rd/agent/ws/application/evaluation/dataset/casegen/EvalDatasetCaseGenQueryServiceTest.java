package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetCaseGenJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 自动生成 Case 读侧。
 */
@ExtendWith(MockitoExtension.class)
class EvalDatasetCaseGenQueryServiceTest {

    @Mock
    private EvalDatasetCaseGenJobMapper caseGenJobMapper;

    @InjectMocks
    private EvalDatasetCaseGenQueryService queryService;

    @Test
    void detail_ok() {
        EvalDatasetCaseGenJobEntity e = baseEntity();
        when(caseGenJobMapper.selectOne(any())).thenReturn(e);

        CaseGenJobVO vo = queryService.detail("ECG1", "WS1");
        assertEquals("ECG1", vo.getNum());
        assertEquals("RUNNING", vo.getStatus());
        assertEquals(40, vo.getProgressPct());
    }

    @Test
    void detail_notFound() {
        when(caseGenJobMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.detail("ECG-X", "WS1"));
        assertEquals(BizCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void detail_forbiddenOtherWorkspace() {
        EvalDatasetCaseGenJobEntity e = baseEntity();
        e.setWorkspaceNum("WS-OTHER");
        when(caseGenJobMapper.selectOne(any())).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.detail("ECG1", "WS1"));
        assertEquals(BizCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void page_filtersByDataset() {
        EvalDatasetCaseGenJobEntity e = baseEntity();
        IPage<EvalDatasetCaseGenJobEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(e));
        page.setTotal(1);
        when(caseGenJobMapper.selectPage(any(), any())).thenReturn(page);

        CaseGenJobPageQuery q = new CaseGenJobPageQuery();
        q.setDatasetNum("EDS1");
        q.setPageNo(1);
        q.setPageSize(20);
        PageVO<CaseGenJobVO> vo = queryService.page(q, "WS1");
        assertEquals(1, vo.getTotal());
        assertEquals("ECG1", vo.getList().get(0).getNum());
    }

    private EvalDatasetCaseGenJobEntity baseEntity() {
        EvalDatasetCaseGenJobEntity e = new EvalDatasetCaseGenJobEntity();
        e.setNum("ECG1");
        e.setWorkspaceNum("WS1");
        e.setDatasetNum("EDS1");
        e.setGeneratorAgentNum("AGT-G");
        e.setStatus("RUNNING");
        e.setProgressPct(40);
        e.setDeleted(0);
        return e;
    }
}
