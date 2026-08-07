package ink.garry.rd.agent.ws.application.workspace;

import ink.garry.rd.agent.ws.application.auth.command.AuthzCommandService;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceCreateParamDTO;
import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import ink.garry.rd.agent.ws.domain.workspace.factory.WorkspaceFactory;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkspaceCommandService.createWorkspace 中 memberRoles 行为测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceCreateWithMemberRolesTest {

    @Mock
    private WorkspaceFactory workspaceFactory;
    @Mock
    private WorkspaceQueryService workspaceQueryService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private AuthzCommandService authzCommandService;
    @Mock
    private RLock rLock;

    @Captor
    private ArgumentCaptor<Map<String, Set<String>>> rolesCaptor;

    private WorkspaceCommandService service;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        service = new WorkspaceCommandService();
        injectField("workspaceFactory", workspaceFactory);
        injectField("workspaceQueryService", workspaceQueryService);
        injectField("redissonClient", redissonClient);
        injectField("authzCommandService", authzCommandService);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(workspaceQueryService.existsByCreatorAndName(anyString(), anyString())).thenReturn(false);

        workspace = mock(Workspace.class);
        when(workspace.getNum()).thenReturn("WS-001");
        when(workspace.getAdminList()).thenReturn(List.of("creator123"));
        when(workspace.getMemberList()).thenReturn(List.of());
        when(workspace.getCreateNo()).thenReturn("creator123");
        when(workspaceFactory.buildWorkspace(any(), any(), any(), any(), any()))
                .thenReturn(workspace);
    }

    private void injectField(String name, Object value) {
        try {
            var field = WorkspaceCommandService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void create_withMemberRoles_shouldBindRoles() {
        WorkspaceCreateParamDTO param = new WorkspaceCreateParamDTO();
        param.setName("测试空间");
        param.setDescription("desc");
        param.setMemberRoles(Map.of(
                "RL-SPACE-ADMIN", List.of("creator123", "admin002"),
                "RL-SPACE-MEMBER", List.of("member001")
        ));

        service.createWorkspace(param, "creator123");

        verify(authzCommandService).bindCreatorAsSpaceAdmin("WS-001", "creator123");

        verify(authzCommandService).bindUserRoles(
                eq("WS-001"),
                rolesCaptor.capture(),
                eq("creator123"),
                eq("creator123")
        );

        Map<String, Set<String>> captured = rolesCaptor.getValue();
        // memberRoles 已反转：roleNum → empNo[] → empNo → roleNum[]
        assertEquals(Set.of("RL-SPACE-ADMIN"), captured.get("creator123"));
        assertEquals(Set.of("RL-SPACE-ADMIN"), captured.get("admin002"));
        assertEquals(Set.of("RL-SPACE-MEMBER"), captured.get("member001"));
        // 确保创建人在 space_admin 中（保护检查通过）
        assertTrue(captured.containsKey("creator123"));
    }

    @Test
    void create_withoutMemberRoles_shouldAutoBindCreatorAsSpaceAdmin() {
        WorkspaceCreateParamDTO param = new WorkspaceCreateParamDTO();
        param.setName("测试空间");

        service.createWorkspace(param, "creator123");

        verify(authzCommandService).bindCreatorAsSpaceAdmin("WS-001", "creator123");
        verify(authzCommandService).bindUserRoles(
                eq("WS-001"),
                rolesCaptor.capture(),
                eq("creator123"),
                eq("creator123")
        );
        Map<String, Set<String>> captured = rolesCaptor.getValue();
        assertEquals(Set.of("RL-SPACE-ADMIN"), captured.get("creator123"));
    }

    @Test
    void creatorNotInSpaceAdminInMemberRoles_shouldStillSucceed() {
        // 前端可能传 memberRoles 但 creator 不在此角色中；bindCreatorAsSpaceAdmin 已在之前执行，
        // bindUserRoles 的 creator 保护会将其拦截（SPACE_ADMIN_UNREMOVABLE）
        WorkspaceCreateParamDTO param = new WorkspaceCreateParamDTO();
        param.setName("测试空间");
        param.setMemberRoles(Map.of(
                "RL-SPACE-MEMBER", List.of("member001")
        ));

        doThrow(new BusinessException(40302, "空间创建者必须始终持有空间管理员"))
                .when(authzCommandService)
                .bindUserRoles(eq("WS-001"), any(), eq("creator123"), eq("creator123"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createWorkspace(param, "creator123"));
        assertTrue(ex.getMessage().contains("空间创建者必须始终持有空间管理员"));
    }
}