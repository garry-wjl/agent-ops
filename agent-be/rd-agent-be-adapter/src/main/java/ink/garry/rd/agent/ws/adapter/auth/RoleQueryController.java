package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.query.AuthzQueryService;
import ink.garry.rd.agent.ws.application.auth.query.MePermissionResolver;
import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionGroupVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleDetailVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleSummaryVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleVO;
import ink.garry.rd.agent.ws.client.auth.roleassignment.vo.MyPermissionVO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 角色 / 权限读侧 Controller（查询类）。
 */
@RestController
@RequestMapping("/api/v1")
public class RoleQueryController extends BaseController {

    @Resource
    private AuthzQueryService authzQueryService;
    @Resource
    private MePermissionResolver mePermissionResolver;

    /** 列出空间内全部角色（内置 + 自定义）。 */
    @GetMapping("/roles/list-in-workspace")
    public Result<List<RoleVO>> listInWorkspace(
            @RequestParam(value = "workspaceNum", required = false) String workspaceNum) {
        return ok(authzQueryService.listRolesInWorkspace(resolveWorkspaceNum(workspaceNum)));
    }

    /** 全平台角色总览（platform_admin 用）。 */
    @GetMapping("/roles/list-all")
    public Result<List<RoleSummaryVO>> listAll() {
        return ok(authzQueryService.listAllRolesForPlatformAdmin());
    }

    /** 角色权限明细（按资源域分组）。 */
    @GetMapping("/roles/detail")
    public Result<RoleDetailVO> getRoleDetail(@RequestParam("roleNum") String roleNum) {
        RoleDetailVO vo = authzQueryService.getRoleDetail(roleNum);
        if (vo == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "角色不存在: " + roleNum);
        }
        return ok(vo);
    }

    /** 全部权限元数据（按资源域分组；scope=PLATFORM 仅返回平台域，scope=SPACE 仅返回空间域）。 */
    @GetMapping("/permissions/list")
    public Result<List<PermissionGroupVO>> listPermissions(
            @RequestParam(value = "scope", required = false) String scope) {
        return ok(authzQueryService.listPermissions(scope));
    }

    /** 「我的权限」抽屉。 */
    @GetMapping("/roles/my-permissions")
    public Result<MyPermissionVO> myPermissions(
            @RequestParam(value = "workspaceNum", required = false) String workspaceNum) {
        return ok(mePermissionResolver.getMyPermissions(getCurrentUserId(), resolveWorkspaceNum(workspaceNum)));
    }

    /** 编辑空间抽屉的成员-角色映射（empNo → RoleVO 列表）。 */
    @GetMapping("/roles/list-member-roles")
    public Result<Map<String, List<RoleVO>>> listMemberRoles(
            @RequestParam(value = "workspaceNum", required = false) String workspaceNum) {
        return ok(authzQueryService.listMemberRoles(resolveWorkspaceNum(workspaceNum)));
    }

    private String resolveWorkspaceNum(String fromParam) {
        if (fromParam != null && !fromParam.isBlank()) {
            return fromParam;
        }
        String fromCtx = getCurrentWorkspaceNum();
        if (fromCtx == null || fromCtx.isBlank()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "缺少 workspaceNum 参数或 X-Workspace-Num 上下文");
        }
        return fromCtx;
    }
}
