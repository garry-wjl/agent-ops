package ink.garry.rd.agent.ws.adapter.model;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.model.assembler.ModelVoAssembler;
import ink.garry.rd.agent.ws.application.model.ModelQueryService;
import ink.garry.rd.agent.ws.client.model.vo.ModelDetailVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelPageQueryParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelSelectableVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型读侧控制器（GET）。
 * <p>
 * 2 个 GET 接口：page（按当前空间 + name/modelId/status/keyword 分页）/ detail（详情）。
 * 每个接口流程一致：Vo Param → {@link ModelVoAssembler} → DTO → {@link ModelQueryService}
 * → DTO → Vo → {@link Result}。当前工作空间编号由
 * {@link BaseController#getCurrentWorkspaceNum()} 从 {@code X-Workspace-Num} 头读取并传入 Service 做空间过滤。
 * 出参模型 apiKey 始终脱敏（{@code 前缀+****}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model")
public class ModelQueryController extends BaseController {

    @Resource
    private ModelQueryService modelQueryService;
    @Resource
    private ModelVoAssembler assembler;

    /**
     * 分页查询当前空间内的模型列表（按 name / modelId / status / keyword 筛选，按 update_time DESC）。
     *
     * @param query 分页 + 筛选条件
     * @return 分页模型 Vo
     */
    @GetMapping("/page")
    public Result<PageVO<ModelVO>> page(ModelPageQueryParam query) {
        PageVO<ModelVO> vo = assembler.toModelPageVO(
                modelQueryService.pageModels(assembler.toPageQueryDTO(query), getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * 模型详情（全字段 + 当前状态，apiKey 脱敏）。
     *
     * @param num 模型业务编号
     * @return 详情 Vo
     */
    @GetMapping("/detail")
    public Result<ModelDetailVO> detail(@RequestParam("num") String num) {
        ModelDetailVO vo = assembler.toModelDetailVO(
                modelQueryService.getDetail(num, getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * Agent 可选模型列表：系统启用模型 + 当前空间启用模型，不返回 API Key。
     *
     * @return 可选模型列表
     */
    @GetMapping("/selectable")
    public Result<List<ModelSelectableVO>> selectable() {
        return ok(assembler.toSelectableVOList(modelQueryService.listSelectable(getCurrentWorkspaceNum())));
    }
}
