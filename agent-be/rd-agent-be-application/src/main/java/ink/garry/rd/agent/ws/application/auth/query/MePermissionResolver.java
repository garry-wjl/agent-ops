package ink.garry.rd.agent.ws.application.auth.query;

import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionGroupVO;
import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleVO;
import ink.garry.rd.agent.ws.client.auth.roleassignment.vo.MyPermissionVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「我的权限」抽屉装配器 / MeController 权限字段装配器。
 */
@Component
public class MePermissionResolver {

    @Resource
    private AuthzQueryService authzQueryService;

    /**
     * 装配 MyPermissionVO（含 platform_admin 标识、角色列表、按域分组的权限）。
     */
    public MyPermissionVO getMyPermissions(String userId, String workspaceNum) {
        MyPermissionVO vo = new MyPermissionVO();
        vo.setUserId(userId);
        vo.setWorkspaceNum(workspaceNum);
        vo.setIsPlatformAdmin(authzQueryService.isPlatformAdmin(userId));
        // roles：当前空间持有的角色 → 按角色清单整列表查询
        Map<String, List<RoleVO>> memberRoles = authzQueryService.listMemberRoles(workspaceNum);
        vo.setRoles(memberRoles.getOrDefault(userId, new ArrayList<>()));
        // permissionsByDomain
        Set<String> permissions = authzQueryService.resolveUserPermissions(userId, workspaceNum);
        vo.setPermissionsByDomain(groupByDomain(authzQueryService.listPermissions(), permissions));
        return vo;
    }

    /**
     * 仅返回权限码扁平集合，MeController 使用。
     */
    public Set<String> getMyPermissionCodes(String userId, String workspaceNum) {
        return authzQueryService.resolveUserPermissions(userId, workspaceNum);
    }

    /**
     * 按 listPermissions 全分组结构过滤出当前用户拥有的权限子集。
     */
    private static List<PermissionGroupVO> groupByDomain(List<PermissionGroupVO> all, Set<String> own) {
        if (own == null || own.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> ownSet = new LinkedHashSet<>(own);
        Map<String, PermissionGroupVO> result = new LinkedHashMap<>();
        for (PermissionGroupVO src : all) {
            List<PermissionVO> kept = new ArrayList<>();
            for (PermissionVO p : src.getPermissions()) {
                if (ownSet.contains(p.getCode())) {
                    kept.add(p);
                }
            }
            if (!kept.isEmpty()) {
                PermissionGroupVO g = new PermissionGroupVO();
                g.setResourceDomain(src.getResourceDomain());
                g.setResourceDomainName(src.getResourceDomainName());
                g.setPermissions(kept);
                result.put(src.getResourceDomain(), g);
            }
        }
        return new ArrayList<>(result.values());
    }
}
