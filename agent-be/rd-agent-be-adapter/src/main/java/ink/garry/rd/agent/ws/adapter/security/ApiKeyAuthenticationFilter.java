package ink.garry.rd.agent.ws.adapter.security;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.agent.AgentApiKeyCommandService;
import ink.garry.rd.agent.ws.application.agent.AgentApiKeyQueryService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentApiKeyDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.infra.common.util.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
import java.util.List;

/**
 * 对外调用（open）秘钥认证过滤器。
 * <p>
 * 仅拦截 {@code /api/v1/open/**}（{@link #shouldNotFilter} 对其余路径放行）：
 * <ol>
 *   <li>提取 {@code Authorization: Bearer ak-...}，缺失 / 非法 → 401 {@code API_KEY_MISSING}；</li>
 *   <li>{@link AgentApiKeyQueryService#authenticate} 内部 SHA-256 → findByKeyHash，未命中 → 401 {@code API_KEY_INVALID}；</li>
 *   <li>命中后把 {@code openAgentNum / openWorkspaceNum / openApiKeyNum} 写入 request 属性，
 *       作为后续用例的<b>权威归属</b>（Controller 用它校验 body.agentNum 一致性并隔离工作空间）；</li>
 *   <li>异步 {@link AgentApiKeyCommandService#touchUsedAsync} 刷新最近使用时间，不阻塞主链路。</li>
 * </ol>
 * 执行顺序在 JWT 之后（JWT 已对 open 路径放行，鉴权交给本过滤器）；归属一致性（key.agentNum vs body.agentNum）
 * 的 403 校验在 Controller 用注入的权威 agentNum 完成，避免在过滤器消费 body 流影响 SSE。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** 仅拦截的路径前缀 */
    private static final String OPEN_PATH_PREFIX = "/api/v1/open/";

    /** Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** request 属性 key：权威 Agent 业务编号（来自秘钥） */
    public static final String ATTR_OPEN_AGENT_NUM = "openAgentNum";
    /** request 属性 key：权威工作空间业务编号（来自秘钥） */
    public static final String ATTR_OPEN_WORKSPACE_NUM = "openWorkspaceNum";
    /** request 属性 key：命中的秘钥业务编号 */
    public static final String ATTR_OPEN_API_KEY_NUM = "openApiKeyNum";

    private final AgentApiKeyQueryService agentApiKeyQueryService;
    private final AgentApiKeyCommandService agentApiKeyCommandService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅拦截 /api/v1/open/**，其余路径一律放行
        return !request.getRequestURI().startsWith(OPEN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        // 1. 提取 Bearer 明文
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(resp, BizCode.API_KEY_MISSING, "缺少有效秘钥");
            return;
        }
        String rawKey = header.substring(BEARER_PREFIX.length()).trim();
        if (rawKey.isEmpty()) {
            writeError(resp, BizCode.API_KEY_MISSING, "缺少有效秘钥");
            return;
        }

        // 2. 认证查（SHA-256 → findByKeyHash）
        AgentApiKeyDTO key = agentApiKeyQueryService.authenticate(rawKey);
        if (key == null) {
            writeError(resp, BizCode.API_KEY_INVALID, "秘钥无效或已删除");
            return;
        }

        // 3. 注入权威归属到 request 属性
        req.setAttribute(ATTR_OPEN_AGENT_NUM, key.getAgentNum());
        req.setAttribute(ATTR_OPEN_WORKSPACE_NUM, key.getWorkspaceNum());
        req.setAttribute(ATTR_OPEN_API_KEY_NUM, key.getNum());

        // 4. 异步刷新最近使用时间（不阻塞主链路）
        agentApiKeyCommandService.touchUsedAsync(key.getNum());

        // 5. 放行
        chain.doFilter(req, resp);
    }

    private static void writeError(HttpServletResponse resp, BizCode code, String message) throws IOException {
        resp.setStatus(401);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        Result<List<Void>> body = Result.<List<Void>>fail(code.getCode(), message).withTraceId(TraceContext.get());
        resp.getWriter().write(JSON.toJSONString(body));
    }
}
