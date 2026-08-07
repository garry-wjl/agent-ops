package ink.garry.rd.agent.ws.adapter.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 {@link WorkspaceContextInterceptor}。
 * <p>
 * 拦截范围：
 * <ul>
 *   <li>{@code /api/v1/workspace/**}（排除 list / create —— 不依赖 X-Workspace-Num）：update / detail / delete 做跨空间访问校验；</li>
 *   <li>{@code /api/v1/agents/**}、{@code /api/v1/skill/**}、{@code /api/v1/sandbox/**}、{@code /api/v1/tool/**}、{@code /api/v1/prompt/**}、{@code /api/v1/model/**}：资产侧请求据 X-Workspace-Num 写入空间上下文，
 *       供 QueryService / ReadGateway 按当前空间过滤（请求头缺失时不设上下文，不做过滤）。</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private WorkspaceContextInterceptor workspaceContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(workspaceContextInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/workspace/**",
                        "/api/v1/roles/**",
                        "/api/v1/agents/**",
                        "/api/v1/skill/**",
                        "/api/v1/sandbox/**",
                        "/api/v1/tool/**",
                        "/api/v1/prompt/**",
                        "/api/v1/model/**",
                        "/api/v1/models/**")
                .excludePathPatterns(
                        "/api/v1/workspace/list",
                        "/api/v1/workspace/create");
    }
}
