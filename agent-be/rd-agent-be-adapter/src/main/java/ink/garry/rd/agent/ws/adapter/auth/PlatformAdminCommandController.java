package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.command.AuthzCommandService;
import ink.garry.rd.agent.ws.client.auth.role.param.RoleCreateParam;
import ink.garry.rd.agent.ws.client.auth.role.param.RoleUpdateParam;
import ink.garry.rd.agent.ws.client.auth.roleassignment.param.PlatformRoleAssignParam;
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
 * 平台角色管理 Controller（仅 platform_admin 调用）。
 * <p>含平台角色 CRUD 与用户-平台角色绑定两类命令。</p>
 */
@RestController
@RequestMapping("/api/v1/platform-roles")
public class PlatformAdminCommandController extends BaseController {

    @Resource
    private AuthzCommandService authzCommandService;

    /** 创建平台自定义角色（scope=PLATFORM）。 */
    @PostMapping("/role/create")
    public Result<String> createPlatformRole(@RequestBody RoleCreateParam param) {
        String roleNum = authzCommandService.createPlatformRole(param, getCurrentUserId());
        return ok(roleNum);
    }

    /** 编辑平台自定义角色（整体覆盖；内置角色禁止编辑）。 */
    @PostMapping("/role/update")
    public Result<Void> updatePlatformRole(@RequestBody RoleUpdateParam param) {
        authzCommandService.updateRole(param, getCurrentUserId());
        return ok(null);
    }

    /** 删除平台自定义角色（被用户绑定时拒绝；内置角色禁止删除）。 */
    @PostMapping("/role/delete")
    public Result<Void> deletePlatformRole(@RequestBody DeleteRoleRequest req) {
        if (req == null || req.getRoleNum() == null || req.getRoleNum().isBlank()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "roleNum 不能为空");
        }
        authzCommandService.deleteRole(req.getRoleNum(), getCurrentUserId());
        return ok(null);
    }

    /** 把平台角色赋给某工号（写 user_workspace_role workspace_num=SYSTEM 行）。 */
    @PostMapping("/assign")
    public Result<Void> assignPlatformRole(@RequestBody PlatformRoleAssignParam param) {
        authzCommandService.assignPlatformRole(param, getCurrentUserId());
        return ok(null);
    }

    /** 解除某工号的某平台角色绑定。 */
    @PostMapping("/unassign")
    public Result<Void> unassignPlatformRole(@RequestBody PlatformRoleAssignParam param) {
        authzCommandService.unassignPlatformRole(
                param.getEmpNo(), param.getPlatformRoleNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 覆盖式保存某工号的全部平台角色（添加/编辑用户角色弹窗 → 保存）。
     * <p>roleNums 为空 → 解除该用户全部平台角色；非空 → 覆盖。</p>
     */
    @PostMapping("/save-user-roles")
    public Result<Void> saveUserRoles(@RequestBody SaveUserRolesRequest req) {
        if (req == null || req.getEmpNo() == null || req.getEmpNo().isBlank()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "empNo 不能为空");
        }
        java.util.Set<String> roleNums = req.getRoleNums() == null
                ? java.util.Set.of() : new java.util.LinkedHashSet<>(req.getRoleNums());
        authzCommandService.saveUserPlatformRoles(req.getEmpNo(), roleNums, getCurrentUserId());
        return ok(null);
    }

    /** delete 请求体 */
    @Data
    public static class DeleteRoleRequest {
        /** 角色业务编号（必填） */
        private String roleNum;
    }

    /** save-user-roles 请求体 */
    @Data
    public static class SaveUserRolesRequest {
        /** 目标工号（必填） */
        private String empNo;
        /** 完整角色 num 列表（覆盖式；可空，空表示解除全部） */
        private java.util.List<String> roleNums;
    }
}
