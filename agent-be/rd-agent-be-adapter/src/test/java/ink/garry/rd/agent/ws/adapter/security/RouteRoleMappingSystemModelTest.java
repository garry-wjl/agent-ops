package ink.garry.rd.agent.ws.adapter.security;

import ink.garry.rd.agent.ws.application.auth.AuthProperties;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.RoutePermissionEntity;
import ink.garry.rd.agent.ws.infra.auth.permission.mapper.RoutePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * RouteRoleMapping — 系统模型路由权限校验。
 * <p>V31 重构：RouteRoleMapping 改为 DB 驱动，测试通过 Mock RoutePermissionMapper 注入映射数据。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteRoleMappingSystemModelTest {

    @Mock
    private RoutePermissionMapper routePermissionMapper;

    private RouteRoleMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new RouteRoleMapping(new AuthProperties());
        try {
            var field = RouteRoleMapping.class.getDeclaredField("routePermissionMapper");
            field.setAccessible(true);
            field.set(mapping, routePermissionMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RoutePermissionEntity createRoute = new RoutePermissionEntity();
        createRoute.setPathPattern("/api/v1/system/model/create");
        createRoute.setPermissionCodes("[\"system:model_create\"]");
        createRoute.setSortOrder(703);

        lenient().when(routePermissionMapper.listAll()).thenReturn(List.of(createRoute));
    }

    @Test
    void systemModelCreate_shouldRequireSystemModelCreatePermission() {
        assertTrue(mapping.allow("/api/v1/system/model/create",
                Set.of("system:model_create"), false));
        assertFalse(mapping.allow("/api/v1/system/model/create",
                Set.of("model:create"), false));
        assertFalse(mapping.allow("/api/v1/system/model/create",
                Set.of("system:model_read"), false));
    }

    @Test
    void platformAdmin_shouldBypassAllChecks() {
        assertTrue(mapping.allow("/api/v1/system/model/create", Set.of(), true));
        assertTrue(mapping.allow("/api/v1/system/model/create", null, true));
    }

    @Test
    void resolvePermissionWorkspace_shouldUseSystemWorkspaceForSystemRoutesWithoutHeader() {
        assertEquals(AuthzConstants.PLATFORM_WORKSPACE_NUM,
                JwtAuthenticationFilter.resolvePermissionWorkspace("/api/v1/system/model/create", null));
        assertEquals(AuthzConstants.PLATFORM_WORKSPACE_NUM,
                JwtAuthenticationFilter.resolvePermissionWorkspace("/api/v1/platform-roles/assign", ""));
        assertEquals("WS-001",
                JwtAuthenticationFilter.resolvePermissionWorkspace("/api/v1/model/create", "WS-001"));
    }
}

