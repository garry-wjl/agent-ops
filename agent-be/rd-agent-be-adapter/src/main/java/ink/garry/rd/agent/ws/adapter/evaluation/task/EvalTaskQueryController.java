package ink.garry.rd.agent.ws.adapter.evaluation.task;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.task.EvalPublishGateService;
import ink.garry.rd.agent.ws.application.evaluation.task.EvalTaskQueryService;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalStatsVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskVO;
import ink.garry.rd.agent.ws.client.evaluation.task.PublishGateCheckParam;
import ink.garry.rd.agent.ws.client.evaluation.task.PublishGateCheckVO;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskCompareParam;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskCompareVO;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskPageQuery;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评测任务读接口。
 */
@RestController
@RequestMapping("/api/v1/evaluation/task/query")
public class EvalTaskQueryController extends BaseController {

    @Resource
    private EvalTaskQueryService evalTaskQueryService;
    @Resource
    private EvalPublishGateService evalPublishGateService;

    @PostMapping("/page")
    public Result<PageVO<EvalTaskVO>> page(@RequestBody TaskPageQuery query) {
        return ok(evalTaskQueryService.page(query, getCurrentWorkspaceNum()));
    }

    @GetMapping("/detail")
    public Result<EvalTaskDetailVO> detail(@RequestParam("num") String num) {
        return ok(evalTaskQueryService.detail(num, getCurrentWorkspaceNum()));
    }

    @GetMapping("/items")
    public Result<List<EvalTaskItemVO>> items(@RequestParam("taskNum") String taskNum) {
        return ok(evalTaskQueryService.listItems(taskNum, getCurrentWorkspaceNum()));
    }

    @GetMapping("/stats")
    public Result<EvalStatsVO> stats() {
        return ok(evalTaskQueryService.stats(getCurrentWorkspaceNum()));
    }

    @PostMapping("/checkPublishGate")
    public Result<PublishGateCheckVO> checkPublishGate(@Valid @RequestBody PublishGateCheckParam param) {
        return ok(evalPublishGateService.checkPublishGate(
                param.getAgentNum(), param.getAgentVersionNum(), getCurrentWorkspaceNum()));
    }

    @PostMapping("/compare")
    public Result<TaskCompareVO> compare(@Valid @RequestBody TaskCompareParam param) {
        return ok(evalTaskQueryService.compare(param, getCurrentWorkspaceNum()));
    }
}
