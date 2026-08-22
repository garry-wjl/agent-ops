package ink.garry.rd.agent.ws.adapter.evaluation.grader;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.grader.EvalGraderQueryService;
import ink.garry.rd.agent.ws.client.evaluation.grader.EvalGraderVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderPresetVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluation/grader/query")
public class EvalGraderQueryController extends BaseController {

    @Resource
    private EvalGraderQueryService evalGraderQueryService;

    @PostMapping("/page")
    public Result<PageVO<EvalGraderVO>> page(@RequestBody GraderPageQuery query) {
        return ok(evalGraderQueryService.page(query, getCurrentWorkspaceNum()));
    }

    @GetMapping("/detail")
    public Result<EvalGraderVO> detail(@RequestParam("num") String num) {
        return ok(evalGraderQueryService.detail(num, getCurrentWorkspaceNum()));
    }

    @GetMapping("/presets")
    public Result<List<GraderPresetVO>> presets() {
        return ok(evalGraderQueryService.listPresets());
    }
}
