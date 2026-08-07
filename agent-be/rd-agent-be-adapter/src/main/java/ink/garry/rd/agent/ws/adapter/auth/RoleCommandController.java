package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.command.AuthzCommandService;
import ink.garry.rd.agent.ws.client.auth.role.param.RoleCreateParam;
import ink.garry.rd.agent.ws.client.auth.role.param.RoleUpdateParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色写侧 Controller（命令类）。
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleCommandController extends BaseController {

    @Resource
    private AuthzCommandService authzCommandService;

    /** 创建空间自定义角色（workspaceNum 从 X-Workspace-Num 取）。 */
    @PostMapping("/create")
    public Result<String> createRole(@RequestBody RoleCreateParam param) {
        String workspaceNum = getCurrentWorkspaceNum();
        if (workspaceNum == null || workspaceNum.isBlank()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "缺少 X-Workspace-Num 上下文");
        }
        String roleNum = authzCommandService.createRole(param, workspaceNum, getCurrentUserId());
        return ok(roleNum);
    }

    /** 编辑空间自定义角色（整体覆盖）。 */
    @PostMapping("/update")
    public Result<Void> updateRole(@RequestBody RoleUpdateParam param) {
        authzCommandService.updateRole(param, getCurrentUserId());
        return ok(null);
    }

    /** 删除空间自定义角色（被用户绑定时拒绝）。 */
    @PostMapping("/delete")
    public Result<Void> deleteRole(@RequestBody DeleteRoleRequest req) {
        if (req == null || req.getRoleNum() == null || req.getRoleNum().isBlank()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "roleNum 不能为空");
        }
        authzCommandService.deleteRole(req.getRoleNum(), getCurrentUserId());
        return ok(null);
    }

    /** delete 请求体 */
    @Data
    public static class DeleteRoleRequest {
        /** 角色业务编号（必填） */
        private String roleNum;
    }
}
