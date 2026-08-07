package ink.garry.rd.agent.ws.adapter.evaluation;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.EvalQueryService;
import ink.garry.rd.agent.ws.client.evaluation.DashboardStatsVO;
import ink.garry.rd.agent.ws.client.evaluation.EvalCaseVO;
import ink.garry.rd.agent.ws.client.evaluation.EvalCompareVO;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评测查询接口（只读），路径前缀 /api/v1/evaluation/query。
 * 详见评测技术方案 §5.2.1。
 */
@RestController
@RequestMapping("/api/v1/evaluation/query")
@RequiredArgsConstructor
public class EvalQueryController extends BaseController {

    private final EvalQueryService queryService;

    @PostMapping("/list")
    public Result<PageVO<EvaluationVO>> list(@Valid @RequestBody EvaluationPageQuery query) {
        return ok(queryService.pageList(query));
    }

    @GetMapping("/detail")
    public Result<EvaluationDetailVO> detail(@RequestParam("evaluationNum") String evaluationNum) {
        return ok(queryService.detail(evaluationNum));
    }

    @GetMapping("/cases")
    public Result<List<EvalCaseVO>> cases(@RequestParam("evaluationNum") String evaluationNum) {
        return ok(queryService.caseList(evaluationNum));
    }

    @GetMapping("/dashboardStats")
    public Result<DashboardStatsVO> dashboardStats() {
        return ok(queryService.dashboardStats());
    }

    @PostMapping("/compare")
    public Result<EvalCompareVO> compare(@RequestParam("baselineNum") String baselineNum,
                                         @RequestParam("candidateNum") String candidateNum) {
        return ok(queryService.compareEvaluations(baselineNum, candidateNum));
    }
}
