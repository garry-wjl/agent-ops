package ink.garry.rd.agent.ws.adapter.tool;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.tool.assembler.ToolVoAssembler;
import ink.garry.rd.agent.ws.application.tool.ToolQueryService;
import ink.garry.rd.agent.ws.client.tool.vo.AgentBriefVo;
import ink.garry.rd.agent.ws.client.tool.vo.ToolDetailVo;
import ink.garry.rd.agent.ws.client.tool.vo.ToolPageQueryParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolVo;
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
 * 工具读侧控制器（GET）。
 * <p>
 * 6 个 GET 接口：page（分页列表）/ detail（详情）/ mountable（Agent Step4 已发布工具列表）/
 * reuseCount（复用数）/ mountedAgents（复用数下钻 Agent 列表）/ checkName（名称唯一性校验）。
 * 每个接口流程一致：Vo Param → {@link ToolVoAssembler} → DTO → {@link ToolQueryService}
 * → DTO → Vo → {@link Result}。当前工作空间编号由
 * {@link BaseController#getCurrentWorkspaceNum()} 从 {@code X-Workspace-Num} 头读取并传入 Service 做空间过滤。
 * <p>
 * 接口约定：HTTP 仅 GET（查询）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tool/query")
public class ToolQueryController extends BaseController {

    @Resource
    private ToolQueryService toolQueryService;
    @Resource
    private ToolVoAssembler assembler;

    /**
     * 分页查询当前空间内的工具列表（按 type / creationMode / status / tag / keyword 筛选，
     * 按 update_time DESC；含 reuseCount）。
     *
     * @param query 分页 + 筛选条件
     * @return 分页工具 Vo
     */
    @GetMapping("/page")
    public Result<PageVO<ToolVo>> page(ToolPageQueryParam query) {
        PageVO<ToolVo> vo = assembler.toToolPageVO(
                toolQueryService.pageList(assembler.toPageQueryDTO(query), getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * 工具详情（全字段 + reuseCount）。
     *
     * @param num 工具业务编号
     * @return 详情 Vo
     */
    @GetMapping("/detail")
    public Result<ToolDetailVo> detail(@RequestParam("num") String num) {
        ToolDetailVo vo = assembler.toToolDetailVO(
                toolQueryService.detail(num, getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * 可挂载工具列表（仅 status=PUBLISHED），供 Agent CONFIG 模式 Step4 多选挂载。
     *
     * @return 已发布工具 Vo 列表（FE 按 type 分组展示）
     */
    @GetMapping("/mountable")
    public Result<List<ToolVo>> mountable() {
        List<ToolVo> vos = assembler.toToolVoList(
                toolQueryService.listMountable(getCurrentWorkspaceNum()));
        return ok(vos);
    }

    /**
     * 复用数：某工具被多少已发布 Agent 挂载。
     *
     * @param num 工具业务编号
     * @return 复用数
     */
    @GetMapping("/reuseCount")
    public Result<Integer> reuseCount(@RequestParam("num") String num) {
        return ok(toolQueryService.reuseCount(num));
    }

    /**
     * 复用数下钻：挂载某工具的已发布 Agent 简表。
     *
     * @param num 工具业务编号
     * @return 挂载该工具的 Agent 简表
     */
    @GetMapping("/mountedAgents")
    public Result<List<AgentBriefVo>> mountedAgents(@RequestParam("num") String num) {
        List<AgentBriefVo> vos = assembler.toAgentBriefVoList(
                toolQueryService.listMountedAgents(num));
        return ok(vos);
    }

    /**
     * 名称唯一性校验（工作空间内，不区分类型；前端失焦调用）。
     *
     * @param name       工具名称
     * @param excludeNum 编辑时排除自身的工具 num（创建场景不传）
     * @return true=已存在同名（不可用），false=可用
     */
    @GetMapping("/checkName")
    public Result<Boolean> checkName(@RequestParam("name") String name,
                                     @RequestParam(value = "excludeNum", required = false) String excludeNum) {
        return ok(toolQueryService.existsByName(getCurrentWorkspaceNum(), name, excludeNum));
    }
}
