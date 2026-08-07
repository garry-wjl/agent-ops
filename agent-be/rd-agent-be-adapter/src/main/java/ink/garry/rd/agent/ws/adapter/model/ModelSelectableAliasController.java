package ink.garry.rd.agent.ws.adapter.model;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.model.assembler.ModelVoAssembler;
import ink.garry.rd.agent.ws.application.model.ModelQueryService;
import ink.garry.rd.agent.ws.client.model.vo.ModelSelectableVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PRD 兼容别名：/api/v1/models/selectable。
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelSelectableAliasController extends BaseController {

    @Resource
    private ModelQueryService modelQueryService;
    @Resource
    private ModelVoAssembler assembler;

    @GetMapping("/selectable")
    public Result<List<ModelSelectableVO>> selectable() {
        return ok(assembler.toSelectableVOList(modelQueryService.listSelectable(getCurrentWorkspaceNum())));
    }
}
