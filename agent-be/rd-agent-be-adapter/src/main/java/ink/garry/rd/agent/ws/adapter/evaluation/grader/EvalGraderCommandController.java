package ink.garry.rd.agent.ws.adapter.evaluation.grader;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.grader.EvalGraderCommandService;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateBuiltinGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateCodeGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateGraderResultVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateLlmGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.DistillLlmGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderNumParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderTrialResultVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderTrialRunParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.UpdateGraderParam;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评估器写接口。
 */
@RestController
@RequestMapping("/api/v1/evaluation/grader/command")
public class EvalGraderCommandController extends BaseController {

    @Resource
    private EvalGraderCommandService evalGraderCommandService;

    /** 从预置创建内置评估器。 */
    @PostMapping("/createBuiltin")
    public Result<CreateGraderResultVO> createBuiltin(@Valid @RequestBody CreateBuiltinGraderParam param) {
        String num = evalGraderCommandService.createBuiltin(param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new CreateGraderResultVO(num));
    }

    /** 创建 LLM 评估器。 */
    @PostMapping("/createLlm")
    public Result<CreateGraderResultVO> createLlm(@Valid @RequestBody CreateLlmGraderParam param) {
        String num = evalGraderCommandService.createLlm(param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new CreateGraderResultVO(num));
    }

    /** 创建 CODE 评估器。 */
    @PostMapping("/createCode")
    public Result<CreateGraderResultVO> createCode(@Valid @RequestBody CreateCodeGraderParam param) {
        String num = evalGraderCommandService.createCode(param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new CreateGraderResultVO(num));
    }

    /** 从任务标注蒸馏 LLM 评估器。 */
    @PostMapping("/distillFromTask")
    public Result<CreateGraderResultVO> distillFromTask(@Valid @RequestBody DistillLlmGraderParam param) {
        String num = evalGraderCommandService.distillFromTask(param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new CreateGraderResultVO(num));
    }

    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody UpdateGraderParam param) {
        evalGraderCommandService.update(param, getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody GraderNumParam param) {
        evalGraderCommandService.delete(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/trialRun")
    public Result<GraderTrialResultVO> trialRun(@Valid @RequestBody GraderTrialRunParam param) {
        return ok(evalGraderCommandService.trialRun(param));
    }
}
