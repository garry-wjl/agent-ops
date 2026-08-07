package ink.garry.rd.agent.ws.adapter.model;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.model.assembler.ModelVoAssembler;
import ink.garry.rd.agent.ws.application.model.ModelCommandService;
import ink.garry.rd.agent.ws.client.model.dto.ModelCreateParamDTO;
import ink.garry.rd.agent.ws.client.model.vo.ModelCreateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelOperateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelUpdateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelVO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统模型写侧入口。
 * <p>与空间模型共用应用服务，但可信入口固定 scope=PLATFORM；
 * 路由权限由 system:model_create / system:model_update / system:model_delete 分别控制。</p>
 */
@RestController
@RequestMapping("/api/v1/system/model")
public class SystemModelCommandController extends BaseController {

    @Resource
    private ModelCommandService modelCommandService;
    @Resource
    private ModelVoAssembler assembler;

    @PostMapping("/create")
    public Result<ModelVO> create(@Valid @RequestBody ModelCreateParam param) {
        ModelCreateParamDTO dto = assembler.toCreateDTO(param);
        dto.setScope(ModelScope.PLATFORM.name());
        dto.setWorkspaceNum(null);
        ModelVO vo = assembler.toModelVO(modelCommandService.createModel(dto, getCurrentUserId()));
        return ok(vo);
    }

    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody ModelUpdateParam param) {
        modelCommandService.updateModel(assembler.toUpdateDTO(param), getCurrentUserId(), ModelScope.PLATFORM, null);
        return ok(null);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody ModelOperateParam param) {
        modelCommandService.deleteModel(param.getNum(), getCurrentUserId(), ModelScope.PLATFORM, null);
        return ok(null);
    }

    @PostMapping("/enable")
    public Result<Void> enable(@Valid @RequestBody ModelOperateParam param) {
        modelCommandService.enableModel(param.getNum(), getCurrentUserId(), ModelScope.PLATFORM, null);
        return ok(null);
    }

    @PostMapping("/disable")
    public Result<Void> disable(@Valid @RequestBody ModelOperateParam param) {
        modelCommandService.disableModel(param.getNum(), getCurrentUserId(), ModelScope.PLATFORM, null);
        return ok(null);
    }
}
