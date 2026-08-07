package ink.garry.rd.agent.ws.adapter.evaluation;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.EvalQueryService;
import ink.garry.rd.agent.ws.application.evaluation.EvalSeedCommandService;
import ink.garry.rd.agent.ws.client.evaluation.EvalSeedParam;
import ink.garry.rd.agent.ws.client.evaluation.EvalSeedVO;
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
 * 评测黄金集种子接口（写 + 列表），路径前缀 /api/v1/evaluation/seed。
 * 详见评测技术方案 §5.2.1。
 */
@RestController
@RequestMapping("/api/v1/evaluation/seed")
@RequiredArgsConstructor
public class EvalSeedController extends BaseController {

    private final EvalSeedCommandService seedCommandService;
    private final EvalQueryService queryService;

    @PostMapping("/save")
    public Result<EvalSeedVO> save(@Valid @RequestBody EvalSeedParam param) {
        return ok(seedCommandService.saveSeed(param, getCurrentUserId()));
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam("seedNum") String seedNum) {
        seedCommandService.deleteSeed(seedNum, getCurrentUserId());
        return ok(null);
    }

    @GetMapping("/list")
    public Result<List<EvalSeedVO>> list(@RequestParam("skillNum") String skillNum) {
        return ok(queryService.seedList(skillNum));
    }
}
