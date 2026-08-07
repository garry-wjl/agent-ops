package ink.garry.rd.agent.ws.application.auth;

import ink.garry.rd.agent.ws.application.auth.command.AuthzCommandService;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.factory.UserRoleBindingFactory;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository.UserRoleBindingRepository;
import ink.garry.rd.agent.ws.facade.exception.AuthzErrorCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.auth.permission.PermissionRegistry;
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
 * AuthzCommandService.bindUserRoles 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthzCommandServiceBindUserRolesTest {

    @Mock
    private UserRoleBindingFactory userRoleBindingFactory;
    @Mock
    private UserRoleBindingRepository userRoleBindingRepository;
    @Mock
    private PermissionRegistry permissionRegistry;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    private AuthzCommandService service;

    @BeforeEach
    void setUp() {
        service = new AuthzCommandService();
        injectField("userRoleBindingFactory", userRoleBindingFactory);
        injectField("userRoleBindingRepository", userRoleBindingRepository);
        injectField("permissionRegistry", permissionRegistry);
        injectField("redissonClient", redissonClient);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void injectField(String name, Object value) {
        try {
            var field = AuthzCommandService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void bindUserRoles_nullMap_shouldSkip() {
        service.bindUserRoles("WS-001", null, "creator", "operator");
        verify(userRoleBindingRepository, never()).listByWorkspace(any());
    }

    @Test
    void bindUserRoles_creatorWithoutSpaceAdmin_shouldThrow() {
        Map<String, Set<String>> input = Map.of(
                "creator", Set.of("RL-SPACE-MEMBER")
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bindUserRoles("WS-001", input, "creator", "operator"));
        assertEquals(AuthzErrorCode.SPACE_ADMIN_UNREMOVABLE.getCode(), ex.getCode());
    }

    @Test
    void bindUserRoles_creatorWithSpaceAdmin_shouldPass() {
        String ws = "WS-001";
        Map<String, Set<String>> input = Map.of(
                "creator", Set.of(AuthzConstants.ROLE_SPACE_ADMIN)
        );

        when(userRoleBindingRepository.listByWorkspace(ws)).thenReturn(List.of());
        UserRoleBinding newBinding = mock(UserRoleBinding.class);
        when(userRoleBindingFactory.buildBinding(
                eq(RoleBindingType.SPACE), eq("creator"), eq(ws),
                eq(Set.of(AuthzConstants.ROLE_SPACE_ADMIN))
        )).thenReturn(newBinding);

        service.bindUserRoles(ws, input, "creator", "operator");

        verify(newBinding).save("operator");
    }

    @Test
    void bindUserRoles_multipleUsersAndRoles_shouldUpsertAll() {
        String ws = "WS-001";
        Map<String, Set<String>> input = Map.of(
                "creator", Set.of(AuthzConstants.ROLE_SPACE_ADMIN, "RL-CUSTOM-1"),
                "userB", Set.of("RL-SPACE-MEMBER")
        );

        when(userRoleBindingRepository.listByWorkspace(ws)).thenReturn(List.of());

        UserRoleBinding bindingCreator = mock(UserRoleBinding.class);
        UserRoleBinding bindingUserB = mock(UserRoleBinding.class);
        when(userRoleBindingFactory.buildBindingByUser("creator", ws)).thenReturn(null);
        when(userRoleBindingFactory.buildBindingByUser("userB", ws)).thenReturn(null);
        when(userRoleBindingFactory.buildBinding(eq(RoleBindingType.SPACE), eq("creator"), eq(ws),
                eq(Set.of(AuthzConstants.ROLE_SPACE_ADMIN, "RL-CUSTOM-1"))))
                .thenReturn(bindingCreator);
        when(userRoleBindingFactory.buildBinding(eq(RoleBindingType.SPACE), eq("userB"), eq(ws),
                eq(Set.of("RL-SPACE-MEMBER"))))
                .thenReturn(bindingUserB);

        service.bindUserRoles(ws, input, "creator", "operator");

        verify(bindingCreator).save("operator");
        verify(bindingUserB).save("operator");
    }

    @Test
    void bindUserRoles_existingUsersNotInMap_shouldDelete() {
        String ws = "WS-001";
        Map<String, Set<String>> input = Map.of(
                "creator", Set.of(AuthzConstants.ROLE_SPACE_ADMIN)
        );

        UserRoleBinding existingDeletable = mock(UserRoleBinding.class);
        when(existingDeletable.getUserId()).thenReturn("oldUser");
        when(userRoleBindingRepository.listByWorkspace(ws))
                .thenReturn(List.of(existingDeletable));

        UserRoleBinding bindingCreator = mock(UserRoleBinding.class);
        when(userRoleBindingFactory.buildBindingByUser("creator", ws)).thenReturn(null);
        when(userRoleBindingFactory.buildBinding(eq(RoleBindingType.SPACE), eq("creator"),
                eq(ws), eq(Set.of(AuthzConstants.ROLE_SPACE_ADMIN))))
                .thenReturn(bindingCreator);

        UserRoleBinding oldBinding = mock(UserRoleBinding.class);
        when(userRoleBindingFactory.buildBindingByUser("oldUser", ws)).thenReturn(oldBinding);

        service.bindUserRoles(ws, input, "creator", "operator");

        verify(bindingCreator).save("operator");
        verify(oldBinding).delete("operator");
    }

    @Test
    void bindUserRoles_existingUserUpdated_shouldOverwriteRoles() {
        String ws = "WS-001";
        Map<String, Set<String>> input = Map.of(
                "creator", Set.of(AuthzConstants.ROLE_SPACE_ADMIN, "RL-CUSTOM-1")
        );

        UserRoleBinding existingBinding = mock(UserRoleBinding.class);
        when(existingBinding.getUserId()).thenReturn("creator");
        when(userRoleBindingRepository.listByWorkspace(ws))
                .thenReturn(List.of(existingBinding));

        when(userRoleBindingFactory.buildBindingByUser("creator", ws))
                .thenReturn(existingBinding);

        service.bindUserRoles(ws, input, "creator", "operator");

        verify(existingBinding).setRoleNums(Set.of(AuthzConstants.ROLE_SPACE_ADMIN, "RL-CUSTOM-1"));
        verify(existingBinding).save("operator");
    }

    @Test
    void bindCreatorAsSpaceAdmin_shouldCreateBinding() {
        String ws = "WS-002";
        UserRoleBinding binding = mock(UserRoleBinding.class);
        when(userRoleBindingFactory.buildBinding(
                eq(RoleBindingType.SPACE), eq("creator123"), eq(ws),
                eq(Set.of(AuthzConstants.ROLE_SPACE_ADMIN))
        )).thenReturn(binding);

        service.bindCreatorAsSpaceAdmin(ws, "creator123");

        verify(binding).save("creator123");
    }
}