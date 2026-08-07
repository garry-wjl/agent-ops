package ink.garry.rd.agent.ws.adapter.security;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.auth.query.AuthzQueryService;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.auth.token.LocalTokenIssuer;
import ink.garry.rd.agent.ws.facade.auth.token.UserClaims;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.auth.audit.AuthAuditHelper;
import ink.garry.rd.agent.ws.infra.auth.token.JwtProperties;
import ink.garry.rd.agent.ws.infra.common.util.TraceContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT 鉴权过滤器。
 * <p>权限管理 v1.0 起：解析 JWT 成功后通过 {@link AuthzQueryService} 获取用户权限并集，
 * 再交由 {@link RouteRoleMapping#allow(String, Set, boolean)} 做按路径 RBAC 校验。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 请求头：当前活动工作空间编号（与 WorkspaceContextInterceptor 同口径） */
    private static final String WORKSPACE_HEADER = "X-Workspace-Num";

    private final JwtProperties jwtProps;
    private final LocalTokenIssuer tokenIssuer;
    private final RouteRoleMapping routeRoleMapping;
    private final AuthAuditHelper audit;
    private final AuthzQueryService authzQueryService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();

        if (routeRoleMapping.isAuthDisabled()) {
            chain.doFilter(req, resp);
            return;
        }
        if (routeRoleMapping.isPublic(path)) {
            chain.doFilter(req, resp);
            return;
        }

        String token = extractToken(req);
        if (token == null) {
            writeError(resp, BizCode.UNAUTHORIZED, "missing authentication token", null);
            return;
        }

        UserClaims claims;
        try {
            claims = tokenIssuer.parse(token);
        } catch (BusinessException e) {
            audit.recordAccessDenied(null, path, "invalid_token:" + e.getMessage());
            writeError(resp, BizCode.UNAUTHORIZED, "invalid authentication token", null);
            return;
        }

        String userId = claims.getAccount();
        String workspaceNum = req.getHeader(WORKSPACE_HEADER);

        boolean isPlatformAdmin = false;
        Set<String> permissions = new LinkedHashSet<>();
        try {
            isPlatformAdmin = authzQueryService.isPlatformAdmin(userId);
            String permissionWorkspaceNum = resolvePermissionWorkspace(path, workspaceNum);
            if (!isPlatformAdmin && permissionWorkspaceNum != null && !permissionWorkspaceNum.isBlank()) {
                permissions = authzQueryService.resolveUserPermissions(userId, permissionWorkspaceNum);
            } else if (isPlatformAdmin) {
                // platform_admin 直接放行；不读 Redis
                permissions = Set.of();
            }
        } catch (Exception ex) {
            log.warn("[JwtAuthenticationFilter] resolve permissions failed userId={} ws={}",
                    userId, workspaceNum, ex);
        }

        if (!routeRoleMapping.allow(path, permissions, isPlatformAdmin)) {
            audit.recordAccessDenied(userId, path, "permission_insufficient");
            Set<String> required = routeRoleMapping.requiredPermissions(path);
            String permHint = required.isEmpty() ? "" : String.join(",", required);
            writeError(resp, BizCode.FORBIDDEN_PERMISSION,
                    "缺少 " + permHint + " 权限，请联系空间管理员", permHint);
            return;
        }

        UserContextHolder.set(UserContext.builder()
                .userId(userId)
                .userName(userId)
                .roles(claims.getRoles() == null ? java.util.Collections.emptyList() : claims.getRoles())
                .build());
        try {
            chain.doFilter(req, resp);
        } finally {
            UserContextHolder.clear();
        }
    }

    private String extractToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (jwtProps.getCookieName().equals(c.getName())) {
                String v = c.getValue();
                return (v == null || v.isBlank()) ? null : v;
            }
        }
        return null;
    }

    private static void writeError(HttpServletResponse resp, BizCode code, String message, String permissionCode)
            throws IOException {
        int status = switch (code) {
            case FORBIDDEN, FORBIDDEN_PERMISSION -> 403;
            default -> 401;
        };
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        Result<List<String>> body = Result.<List<String>>fail(code.getCode(), message).withTraceId(TraceContext.get());
        if (permissionCode != null && !permissionCode.isBlank()) {
            body.setData(java.util.List.of(permissionCode));
        }
        resp.getWriter().write(JSON.toJSONString(body));
    }

    static String resolvePermissionWorkspace(String path, String workspaceNum) {
        if (workspaceNum != null && !workspaceNum.isBlank()) {
            return workspaceNum;
        }
        if (path != null && (path.startsWith("/api/v1/system/")
                || path.startsWith("/api/v1/platform-roles/")
                || path.startsWith("/api/v1/workspace/"))) {
            return AuthzConstants.PLATFORM_WORKSPACE_NUM;
        }
        return workspaceNum;
    }
}
