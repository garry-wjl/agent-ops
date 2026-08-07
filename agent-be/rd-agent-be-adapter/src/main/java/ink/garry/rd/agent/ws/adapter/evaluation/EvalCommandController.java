package ink.garry.rd.agent.ws.adapter.evaluation;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.EvalCommandService;
import ink.garry.rd.agent.ws.client.evaluation.AutoEvalParam;
import ink.garry.rd.agent.ws.client.evaluation.EvalRerunParam;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationVO;
import ink.garry.rd.agent.ws.client.evaluation.ManualEvalParam;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测写命令接口（创建/重跑/删除），路径前缀 /api/v1/evaluation/command。
 * 详见评测技术方案 §5.2.1。
 */
@RestController
@RequestMapping("/api/v1/evaluation/command")
@RequiredArgsConstructor
public class EvalCommandController extends BaseController {

    private final EvalCommandService commandService;

    @PostMapping("/createManual")
    public Result<EvaluationVO> createManual(@Valid @RequestBody ManualEvalParam param) {
        return ok(commandService.createManual(param, getCurrentUserId()));
    }

    @PostMapping("/createAuto")
    public Result<EvaluationVO> createAuto(@Valid @RequestBody AutoEvalParam param) {
        return ok(commandService.createAuto(param, getCurrentUserId()));
    }

    @PostMapping("/rerun")
    public Result<EvaluationVO> rerun(@Valid @RequestBody EvalRerunParam param) {
        return ok(commandService.rerun(param, getCurrentUserId()));
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam("evaluationNum") String evaluationNum) {
        commandService.delete(evaluationNum, getCurrentUserId());
        return ok(null);
    }
}
