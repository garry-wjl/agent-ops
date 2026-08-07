package ink.garry.rd.agent.ws.adapter.workspace;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.workspace.assembler.WorkspaceVoAssembler;
import ink.garry.rd.agent.ws.application.workspace.WorkspaceQueryService;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceDetailVO;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作空间读侧控制器。
 * <p>
 * 2 个 GET 接口：list（我可见的全部空间，不分页）/ detail（编辑抽屉详情）。
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 读取。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceQueryController extends BaseController {

    @Resource
    private WorkspaceQueryService workspaceQueryService;
    @Resource
    private WorkspaceVoAssembler assembler;

    /**
     * 列出当前用户可见的全部空间（「我创建 + 我加入」），不分页。
     *
     * @return 空间卡片 Vo 列表
     */
    @GetMapping("/list")
    public Result<List<WorkspaceVO>> list() {
        return ok(assembler.toWorkspaceVoList(
                workspaceQueryService.listMyWorkspaces(getCurrentUserId())));
    }

    /**
     * 空间详情（编辑抽屉用，含成员列表）。
     *
     * @param num 工作空间业务编号
     * @return 详情 Vo
     */
    @GetMapping("/detail")
    public Result<WorkspaceDetailVO> detail(@RequestParam("num") String num) {
        return ok(assembler.toWorkspaceDetailVO(
                workspaceQueryService.getDetail(num, getCurrentUserId())));
    }
}
