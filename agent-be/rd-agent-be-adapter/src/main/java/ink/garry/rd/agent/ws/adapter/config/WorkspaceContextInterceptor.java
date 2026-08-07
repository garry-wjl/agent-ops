package ink.garry.rd.agent.ws.adapter.config;

import ink.garry.rd.agent.ws.application.workspace.WorkspaceQueryService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.workspace.constant.WorkspaceConstants;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 工作空间上下文拦截器。
 * <p>
 * preHandle：从请求头 {@code X-Workspace-Num} 解析当前活动空间；若请求头非空，校验调用者是否在该空间内
 * （经 {@link WorkspaceQueryService#getMyRole}），不在则抛 403（{@link BizCode#FORBIDDEN}），
 * 在则写入 {@link WorkspaceContextHolder}。afterCompletion：{@code clear()} 清理 ThreadLocal。
 * <p>
 * 仿 {@code UserContextFilter} 的上下文管理；调用者 id 由先于本拦截器执行的 UserContextFilter 注入。
 * 注册范围与 exclude 见 {@link WebMvcConfig}。
 */
@Slf4j
@Component
public class WorkspaceContextInterceptor implements HandlerInterceptor {

    @Resource
    private WorkspaceQueryService workspaceQueryService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String workspaceNum = request.getHeader(WorkspaceConstants.HEADER_X_WORKSPACE_NUM);
        // 请求头为空：不设置上下文，交由下游服务自身按 num 做权限校验
        if (workspaceNum == null || workspaceNum.isBlank()) {
            return true;
        }
        String empNo = UserContextHolder.currentUserId();
        if (empNo == null || empNo.isBlank()) {
            throw new BusinessException(BizCode.UNAUTHORIZED.getCode(), "未登录");
        }
        // 校验调用者是否在该空间内（管理员或成员）
        String role = workspaceQueryService.getMyRole(workspaceNum, empNo);
        if (role == null) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间");
        }
        WorkspaceContextHolder.set(WorkspaceContext.builder()
                .workspaceNum(workspaceNum)
                .role(role)
                .member(true)
                .build());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        WorkspaceContextHolder.clear();
    }
}
