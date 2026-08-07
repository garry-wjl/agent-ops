package ink.garry.rd.agent.ws.infra.common.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前请求的工作空间上下文。
 * <p>
 * 由 adapter 层 WorkspaceContextInterceptor 在请求开始时根据请求头 {@code X-Workspace-Num}
 * 解析并 set，请求结束时 clear。承载当前活动空间编号与调用者在该空间内的角色。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceContext {

    /** 当前活动工作空间业务编号（X-Workspace-Num 解析所得）。 */
    private String workspaceNum;

    /** 调用者在该空间内的角色：ADMIN（在 adminList）/ MEMBER（在 memberList）。 */
    private String role;

    /** 调用者是否为该空间成员（管理员或普通成员）。 */
    private boolean member;
}
