package ink.garry.rd.agent.ws.application.auth.listener;

import ink.garry.rd.agent.ws.domain.auth.role.dto.RoleDomainEventDTO;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.dto.UserRoleBindingEventDTO;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 鉴权领域事件监听器。
 * <p>订阅 5 个 authz 事件，本期仅 log.info；下期接 Redis evict 时在此扩分支。</p>
 */
@Slf4j
@Component
public class AuthzCacheEvictListener {

    private static final Set<String> AUTHZ_EVENT_TYPES = Set.of(
            DomainEventConstant.ROLE_CREATED,
            DomainEventConstant.ROLE_UPDATED,
            DomainEventConstant.ROLE_DELETED,
            DomainEventConstant.USER_ROLE_BOUND,
            DomainEventConstant.USER_ROLE_UNBOUND
    );

    @EventListener
    public void onAuthzEvent(DomainEventDTO event) {
        if (event == null || !AUTHZ_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        Object data = event.getData();
        String summary;
        if (data instanceof RoleDomainEventDTO role) {
            summary = "roleNum=" + role.getRoleNum() + " scope=" + role.getScope()
                    + " workspaceNum=" + role.getWorkspaceNum();
        } else if (data instanceof UserRoleBindingEventDTO binding) {
            summary = "workspaceNum=" + binding.getWorkspaceNum()
                    + " userId=" + binding.getUserId()
                    + " roleNums=" + binding.getRoleNums();
        } else {
            summary = data == null ? "<null>" : data.toString();
        }
        log.info("[authz-event] type={} id={} sender={} payload={{}}",
                event.getType(), event.getId(), event.getSender(), summary);
        // TODO S3：接通 AuthzQueryService.evictUserPermissionCache 精确淘汰
    }
}
