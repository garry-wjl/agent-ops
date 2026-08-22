package ink.garry.rd.agent.ws.adapter.evaluation.task;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.task.EvalPublishGateService;
import ink.garry.rd.agent.ws.application.evaluation.task.EvalTaskCommandService;
import ink.garry.rd.agent.ws.application.evaluation.task.EvalTaskQueryService;
import ink.garry.rd.agent.ws.client.evaluation.task.CreateAndStartTaskParam;
import ink.garry.rd.agent.ws.client.evaluation.task.CreateTaskResultVO;
import ink.garry.rd.agent.ws.client.evaluation.task.SaveLabelsParam;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskNumParam;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测任务写接口。
 */
@RestController
@RequestMapping("/api/v1/evaluation/task/command")
public class EvalTaskCommandController extends BaseController {

    @Resource
    private EvalTaskCommandService evalTaskCommandService;

    @PostMapping("/createAndStart")
    public Result<CreateTaskResultVO> createAndStart(@Valid @RequestBody CreateAndStartTaskParam param) {
        String num = evalTaskCommandService.createAndStart(param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new CreateTaskResultVO(num));
    }

    @PostMapping("/rerunFailed")
    public Result<Void> rerunFailed(@Valid @RequestBody TaskNumParam param) {
        evalTaskCommandService.rerunFailed(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/saveLabels")
    public Result<Void> saveLabels(@Valid @RequestBody SaveLabelsParam param) {
        evalTaskCommandService.saveLabels(param, getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody TaskNumParam param) {
        evalTaskCommandService.cancel(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody TaskNumParam param) {
        evalTaskCommandService.delete(param.getNum(), getCurrentUserId());
        return ok(null);
    }
}
