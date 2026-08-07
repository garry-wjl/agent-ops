package ink.garry.rd.agent.ws.adapter.prompt;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.prompt.assembler.PromptVoAssembler;
import ink.garry.rd.agent.ws.application.prompt.PromptQueryService;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptDetailVo;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptPageQueryParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 读侧控制器（GET）。
 * <p>
 * 3 个 GET 接口：page（分页列表）/ detail（详情）/ checkKey（Prompt Key 唯一性校验）。
 * 每个接口流程一致：Vo Param → {@link PromptVoAssembler} → DTO → {@link PromptQueryService}
 * → DTO → Vo → {@link Result}。当前工作空间编号由
 * {@link BaseController#getCurrentWorkspaceNum()} 从 {@code X-Workspace-Num} 头读取并传入 Service 做空间过滤。
 * <p>
 * 接口约定：HTTP 仅 GET（查询）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prompt/query")
public class PromptQueryController extends BaseController {

    @Resource
    private PromptQueryService promptQueryService;
    @Resource
    private PromptVoAssembler assembler;

    /**
     * 分页查询当前空间内的 Prompt 列表（按 tag / keyword 筛选，按 update_time DESC）。
     *
     * @param query 分页 + 筛选条件
     * @return 分页 Prompt Vo
     */
    @GetMapping("/page")
    public Result<PageVO<PromptVo>> page(PromptPageQueryParam query) {
        PageVO<PromptVo> vo = assembler.toPromptPageVO(
                promptQueryService.pageList(assembler.toPageQueryDTO(query), getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * Prompt 详情（全字段）。
     *
     * @param num Prompt 业务编号
     * @return 详情 Vo
     */
    @GetMapping("/detail")
    public Result<PromptDetailVo> detail(@RequestParam("num") String num) {
        PromptDetailVo vo = assembler.toPromptDetailVO(
                promptQueryService.detail(num, getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * Prompt Key 唯一性校验（工作空间内；前端失焦调用）。
     *
     * @param promptKey  Prompt 引用键
     * @param excludeNum 编辑时排除自身的 Prompt num（创建场景不传）
     * @return true=已存在同 Key（不可用），false=可用
     */
    @GetMapping("/checkKey")
    public Result<Boolean> checkKey(@RequestParam("promptKey") String promptKey,
                                    @RequestParam(value = "excludeNum", required = false) String excludeNum) {
        return ok(promptQueryService.existsByKey(getCurrentWorkspaceNum(), promptKey, excludeNum));
    }
}
