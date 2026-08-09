package ink.garry.rd.agent.ws.adapter.config;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.auth.AuthProperties;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
import ink.garry.rd.agent.ws.infra.common.util.TraceContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 用户上下文过滤器：
 *  - 优先级低于 {@code JwtAuthenticationFilter}（HIGHEST_PRECEDENCE+5），跑在它之后
 *  - 当 JWT 已注入 UserContext 时，本 Filter 跳过 header 解析，避免覆盖 JWT 解出的身份
 *  - 否则从 {@code X-User-Id} / {@code X-User-Name} Header 注入 UserContext
 *  - {@code app.auth.disable-auth=true} 且无 header 时，回落到 {@code app.auth.dev-user-id}
 *  - 若身份是 username（非 USR- 前缀），尝试解析为 {@code User.num}
 *  - 同时初始化 TraceContext（{@code X-Trace-Id} 或 UUID）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class UserContextFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String USER_NUM_PREFIX = "USR-";

    private final AuthProperties authProps;
    private final UserQueryService userQueryService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String traceId = req.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        TraceContext.set(traceId);
        resp.setHeader(HEADER_TRACE_ID, traceId);

        // JWT Filter 已注入则跳过 header 解析，避免被 header 覆盖
        if (UserContextHolder.get() == null) {
            String userId = req.getHeader(HEADER_USER_ID);
            String userName = req.getHeader(HEADER_USER_NAME);
            if ((userId == null || userId.isEmpty()) && authProps.isDisableAuth()) {
                userId = authProps.getDevUserId();
                if (userName == null || userName.isEmpty()) {
                    userName = userId;
                }
            }
            if (userId != null && !userId.isEmpty()) {
                ResolvedIdentity identity = resolveIdentity(userId, userName);
                UserContextHolder.set(UserContext.builder()
                        .userId(identity.userId())
                        .userName(identity.userName())
                        .build());
            }
        }

        try {
            chain.doFilter(req, resp);
        } finally {
            UserContextHolder.clear();
            TraceContext.clear();
        }
    }

    private ResolvedIdentity resolveIdentity(String rawUserId, String rawUserName) {
        String userId = rawUserId;
        String userName = StrUtil.blankToDefault(rawUserName, rawUserId);
        if (!userId.startsWith(USER_NUM_PREFIX)) {
            try {
                String num = userQueryService.findNumByUsername(userId);
                if (StrUtil.isNotBlank(num)) {
                    String username = userQueryService.findUsernameByNum(num);
                    return new ResolvedIdentity(num, StrUtil.blankToDefault(username, userId));
                }
            } catch (Exception ignore) {
                // bootstrap 前表可能尚未就绪，回退原值
            }
        } else if (StrUtil.isBlank(rawUserName) || rawUserName.equals(rawUserId)) {
            try {
                String username = userQueryService.findUsernameByNum(userId);
                if (StrUtil.isNotBlank(username)) {
                    userName = username;
                }
            } catch (Exception ignore) {
                // ignore
            }
        }
        return new ResolvedIdentity(userId, userName);
    }

    private record ResolvedIdentity(String userId, String userName) {
    }
}
