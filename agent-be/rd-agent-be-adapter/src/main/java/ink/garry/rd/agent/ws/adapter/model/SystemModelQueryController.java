package ink.garry.rd.agent.ws.adapter.model;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.model.assembler.ModelVoAssembler;
import ink.garry.rd.agent.ws.application.model.ModelQueryService;
import ink.garry.rd.agent.ws.client.model.vo.ModelDetailVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelPageQueryParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelVO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统模型读侧入口。
 * <p>用于平台设置页管理系统模型；系统模型 API Key 仍由 assembler/query service 剔除。</p>
 */
@RestController
@RequestMapping("/api/v1/system/model")
public class SystemModelQueryController extends BaseController {

    @Resource
    private ModelQueryService modelQueryService;
    @Resource
    private ModelVoAssembler assembler;

    @GetMapping("/page")
    public Result<PageVO<ModelVO>> page(ModelPageQueryParam query) {
        PageVO<ModelVO> vo = assembler.toModelPageVO(
                modelQueryService.pageModels(assembler.toPageQueryDTO(query), ModelScope.PLATFORM, null));
        return ok(vo);
    }

    @GetMapping("/detail")
    public Result<ModelDetailVO> detail(@RequestParam("num") String num) {
        ModelDetailVO vo = assembler.toModelDetailVO(
                modelQueryService.getDetail(num, ModelScope.PLATFORM, null));
        return ok(vo);
    }
}
