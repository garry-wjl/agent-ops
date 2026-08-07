package ink.garry.rd.agent.ws.adapter.auth;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.query.AuthzQueryService;
import ink.garry.rd.agent.ws.application.auth.query.MePermissionResolver;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
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
 */
@RestController
@RequestMapping("/api/v1/auth")
public class MeController extends BaseController {

    @Resource
    private MePermissionResolver mePermissionResolver;
    @Resource
    private AuthzQueryService authzQueryService;
    @Resource
    private UserQueryService userQueryService;

    /** 前端 CurrentUser 契约。 */
    @Data
    @Builder
    public static class MeVO {
        /** 用户业务编号 User.num（兼容旧字段名 userId）。 */
        private String userId;
        /** 显示名（username）。 */
        private String userName;
        /** 角色枚举（兼容前端老字段：viewer / editor / admin）。 */
        private String role;
        /** 是否平台管理员 */
        private Boolean isPlatformAdmin;
        /** 当前工作空间业务编号 */
        private String currentWorkspaceNum;
        /** 当前空间持有的角色 num 列表 */
        private List<String> currentWorkspaceRoles;
        /** 权限并集 */
        private List<String> permissions;
    }

    /** 返回当前登录用户 + 权限。 */
    @GetMapping("/me")
    public Result<MeVO> me() {
        String userId = getCurrentUserId();
        String userName = getCurrentUserName();
        String workspaceNum = getCurrentWorkspaceNum();

        // 优先用 sys_user.username；无记录时回退上下文
        String resolvedName = userQueryService.findUsernameByNum(userId);
        if (StrUtil.isBlank(resolvedName) && !userId.startsWith("USR-")) {
            // 兼容旧上下文仍是 username
            String num = userQueryService.findNumByUsername(userId);
            if (StrUtil.isNotBlank(num)) {
                userId = num;
                resolvedName = userQueryService.findUsernameByNum(num);
            }
        }
        if (StrUtil.isNotBlank(resolvedName)) {
            userName = resolvedName;
        }

        boolean isPlatformAdmin = authzQueryService.isPlatformAdmin(userId);
        List<String> permissions;
        List<String> currentWorkspaceRoles = new ArrayList<>();
        if (workspaceNum != null && !workspaceNum.isBlank()) {
            Set<String> perms = mePermissionResolver.getMyPermissionCodes(userId, workspaceNum);
            permissions = new ArrayList<>(perms);
            currentWorkspaceRoles = new ArrayList<>(authzQueryService.listRoleNumsByUser(userId, workspaceNum));
        } else {
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
