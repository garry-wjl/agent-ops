package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.query.AuthzQueryService;
import ink.garry.rd.agent.ws.application.auth.query.MePermissionResolver;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户信息端点。
 * <p>
 * 由前端 {@code AuthProvider} 启动时调用 {@code GET /api/v1/auth/me} 拉取身份回显 + 权限。
 * 权限管理 v1.0 起返回值补 {@code permissions / currentWorkspaceRoles / isPlatformAdmin / currentWorkspaceNum}。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class MeController extends BaseController {

    @Resource
    private MePermissionResolver mePermissionResolver;
    @Resource
    private AuthzQueryService authzQueryService;

    /** 前端 CurrentUser 契约。 */
    @Data
    @Builder
    public static class MeVO {
        /** 用户 ID（登录账号 / AD）。 */
        private String userId;
        /** 显示名。 */
        private String userName;
        /** 角色枚举（兼容前端老字段：viewer / editor / admin）。 */
        private String role;
        /** 是否平台管理员（权限管理 v1.0 新增） */
        private Boolean isPlatformAdmin;
        /** 当前工作空间业务编号（X-Workspace-Num 解析得到） */
        private String currentWorkspaceNum;
        /** 当前空间持有的角色 num 列表 */
        private List<String> currentWorkspaceRoles;
        /** 权限并集（含 platform_admin 全集 / 当前空间角色并集） */
        private List<String> permissions;
    }

    /** 返回当前登录用户 + 权限。 */
    @GetMapping("/me")
    public Result<MeVO> me() {
        String userId = getCurrentUserId();
        String userName = getCurrentUserName();
        String workspaceNum = getCurrentWorkspaceNum();

        boolean isPlatformAdmin = authzQueryService.isPlatformAdmin(userId);
        List<String> permissions;
        List<String> currentWorkspaceRoles = new ArrayList<>();
        if (workspaceNum != null && !workspaceNum.isBlank()) {
            // 有空间上下文：解析该空间的权限并集
            Set<String> perms = mePermissionResolver.getMyPermissionCodes(userId, workspaceNum);
            permissions = new ArrayList<>(perms);
            currentWorkspaceRoles = new ArrayList<>(authzQueryService.listRoleNumsByUser(userId, workspaceNum));
        } else {
            // 无空间上下文（用户在首页 / HomeLayout）：
            // platform_admin 返回全集；普通用户也要解析 SYSTEM workspace 下的平台角色权限，
            // 否则被授予平台角色（如 role_manage:create）的非 admin 用户在首页看不到对应导航。
            permissions = new ArrayList<>(mePermissionResolver.getMyPermissionCodes(userId,
                    ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants.PLATFORM_WORKSPACE_NUM));
        }

        MeVO vo = MeVO.builder()
                .userId(userId)
                .userName(userName != null ? userName : userId)
                .role(mapRole(getCurrentUserRoles(), isPlatformAdmin))
                .isPlatformAdmin(isPlatformAdmin)
                .currentWorkspaceNum(workspaceNum)
                .currentWorkspaceRoles(currentWorkspaceRoles)
                .permissions(permissions)
                .build();
        return ok(vo);
    }

    /**
     * 将后端角色列表映射为前端单值 role。
     * <p>平台管理员优先返回 admin；否则按旧字段语义兜底。</p>
     */
    private static String mapRole(List<String> roles, boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return "admin";
        }
        if (roles == null || roles.isEmpty()) {
            return "viewer";
        }
        if (roles.contains("itadmin") || roles.contains("admin")) {
            return "admin";
        }
        if (roles.contains("editor")) {
            return "editor";
        }
        return "viewer";
    }
}
