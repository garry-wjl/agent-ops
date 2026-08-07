package ink.garry.rd.agent.ws.adapter.sandbox;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.sandbox.assembler.SandboxVoAssembler;
import ink.garry.rd.agent.ws.application.sandbox.SandboxQueryService;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxDetailVO;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxPageQueryParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙箱读侧控制器（GET）。
 * <p>
 * 2 个 GET 接口：page（按当前空间 + type/status/keyword 分页）/ detail（详情）。
 * 每个接口流程一致：Vo Param → {@link SandboxVoAssembler} → DTO → {@link SandboxQueryService}
 * → DTO → Vo → {@link Result}。当前工作空间编号由
 * {@link BaseController#getCurrentWorkspaceNum()} 从 {@code X-Workspace-Num} 头读取并传入 Service 做空间过滤。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxQueryController extends BaseController {

    @Resource
    private SandboxQueryService sandboxQueryService;
    @Resource
    private SandboxVoAssembler assembler;

    /**
     * 分页查询当前空间内的沙箱列表（按 type / status / keyword 筛选，按 update_time DESC）。
     *
     * @param query 分页 + 筛选条件
     * @return 分页沙箱 Vo
     */
    @GetMapping("/page")
    public Result<PageVO<SandboxVO>> page(SandboxPageQueryParam query) {
        PageVO<SandboxVO> vo = assembler.toSandboxPageVO(
                sandboxQueryService.pageSandboxes(assembler.toPageQueryDTO(query), getCurrentWorkspaceNum()));
        return ok(vo);
    }

    /**
     * 沙箱详情（全字段 + 当前状态）。
     *
     * @param num 沙箱业务编号
     * @return 详情 Vo
     */
    @GetMapping("/detail")
    public Result<SandboxDetailVO> detail(@RequestParam("num") String num) {
        SandboxDetailVO vo = assembler.toSandboxDetailVO(
                sandboxQueryService.getDetail(num, getCurrentWorkspaceNum()));
        return ok(vo);
    }
}
