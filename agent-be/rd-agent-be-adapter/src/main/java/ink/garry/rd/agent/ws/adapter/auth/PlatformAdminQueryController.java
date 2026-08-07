package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.query.AuthzQueryService;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleSummaryVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台角色查询 Controller（仅 platform_admin 调用）。
 */
@RestController
@RequestMapping("/api/v1/platform-roles")
public class PlatformAdminQueryController extends BaseController {

    @Resource
    private AuthzQueryService authzQueryService;

    /** 列出全部平台管理员（empNo → 角色 VO 列表）。 */
    @GetMapping("/list-admins")
    public Result<Map<String, List<RoleVO>>> listAdmins() {
        return ok(authzQueryService.listPlatformAdmins());
    }

    /** 列出全部平台级角色（仅 scope=PLATFORM，内置 + 自定义）。 */
    @GetMapping("/role/list")
    public Result<List<RoleSummaryVO>> listPlatformRoles() {
        return ok(authzQueryService.listPlatformRoles());
    }
}
