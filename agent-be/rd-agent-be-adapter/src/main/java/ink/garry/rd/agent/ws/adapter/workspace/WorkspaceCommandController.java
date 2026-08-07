package ink.garry.rd.agent.ws.adapter.workspace;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.workspace.assembler.WorkspaceVoAssembler;
import ink.garry.rd.agent.ws.application.workspace.WorkspaceCommandService;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceCreateParam;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceDeleteParam;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceUpdateParam;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作空间写侧控制器。
 * <p>
 * 仅 3 个 POST 接口，对应界面「创建 / 编辑 / 删除」。每个接口流程一致：
 * Vo Param → {@link WorkspaceVoAssembler} → DTO → {@link WorkspaceCommandService} → 返回 {@link Result}。
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 从请求上下文读取，不从前端入参获取。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceCommandController extends BaseController {

    @Resource
    private WorkspaceCommandService workspaceCommandService;
    @Resource
    private WorkspaceVoAssembler assembler;

    /**
     * 创建工作空间（创建人自动入 adminList）。
     *
     * @param param 创建参数
     * @return 新空间卡片 Vo（含 num）
     */
    @PostMapping("/create")
    public Result<WorkspaceVO> create(@Valid @RequestBody WorkspaceCreateParam param) {
        String operatorId = getCurrentUserId();
        WorkspaceVO vo = assembler.toWorkspaceVO(
                workspaceCommandService.createWorkspace(assembler.toCreateDTO(param), operatorId));
        return ok(vo);
    }

    /**
     * 编辑工作空间（整体覆盖名称 / 描述 / 完整成员两栏）。
     *
     * @param param 编辑参数
     * @return 空
     */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody WorkspaceUpdateParam param) {
        workspaceCommandService.updateWorkspace(assembler.toUpdateDTO(param), getCurrentUserId());
        return ok(null);
    }

    /**
     * 软删工作空间（资产非空禁删）。
     *
     * @param param 删除参数（num）
     * @return 空
     */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody WorkspaceDeleteParam param) {
        workspaceCommandService.deleteWorkspace(assembler.toDeleteDTO(param), getCurrentUserId());
        return ok(null);
    }
}
