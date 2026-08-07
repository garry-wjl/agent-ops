package ink.garry.rd.agent.ws.adapter.security;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import ink.garry.rd.agent.ws.application.auth.AuthProperties;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.RoutePermissionEntity;
import ink.garry.rd.agent.ws.infra.auth.permission.mapper.RoutePermissionMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 路径 → 所需权限 的集中映射（RBAC v1.0）。
 *
 * <p>V31 重构：路径-权限映射从 Java 硬编码迁移至 {@code route_permission} 表，
 * DB 作为唯一真相源，每次请求直接查库，无内存缓存，保证映射变更实时生效。
 *
 * <p>设计原则：
 * <ul>
 *   <li><b>PUBLIC_PATHS</b>：完全不走 JWT 校验</li>
 *   <li>DB 表 {@code route_permission}：按 Ant 风格路径匹配；命中后要求权限集有交集；
 *       未命中条目默认仅要求"已认证"</li>
 *   <li>platform_admin 直接放行（在 allow 入口短路）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRoleMapping {

    /** 完全公开路径；不解析 JWT。 */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/debug-console/test",
            "/api/v1/open/**",
            "/actuator/**",
            "/error",
            "/favicon.ico"
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final AuthProperties authProps;

    @Resource
    private RoutePermissionMapper routePermissionMapper;

    /** 判断路径是否完全公开（无需 JWT）。 */
    public boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    /** 鉴权全局开关。 */
    public boolean isAuthDisabled() {
        return authProps.isDisableAuth();
    }

    /**
     * 鉴权校验（权限码集合版本）。
     * <p>每次调用直接查 {@code route_permission} 表（行数稳定在 50~100 条），
     * 无内存缓存，保证路由映射变更实时生效。</p>
     *
     * @param path             请求路径
     * @param userPermissions  当前用户权限码并集（Redis / DB 解析得到）
     * @param isPlatformAdmin  是否平台管理员；true 直接放行
     * @return 是否允许通过
     */
    public boolean allow(String path, Set<String> userPermissions, boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return true;
        }
        List<RoutePermissionEntity> routes = routePermissionMapper.listAll();
        for (RoutePermissionEntity route : routes) {
            if (!MATCHER.match(route.getPathPattern(), path)) {
                continue;
            }
            List<String> required = parsePermissionCodes(route.getPermissionCodes());
            if (required.isEmpty()) {
                return true; // 空数组=仅登录即可
            }
            if (userPermissions == null || userPermissions.isEmpty()) {
                return false;
            }
            for (String code : required) {
                if (userPermissions.contains(code)) {
                    return true;
                }
            }
            return false;
        }
        // 未登记路径：仅要求已认证（filter 已保证）
        return true;
    }

    /**
     * 返回路径命中的权限要求（用于 403 错误体携带 permissionCode）。
     */
    public Set<String> requiredPermissions(String path) {
        List<RoutePermissionEntity> routes = routePermissionMapper.listAll();
        for (RoutePermissionEntity route : routes) {
            if (MATCHER.match(route.getPathPattern(), path)) {
                return Set.copyOf(parsePermissionCodes(route.getPermissionCodes()));
            }
        }
        return Set.of();
    }

    /**
     * 兼容旧签名：按 String 角色列表的旧调用方走"已认证即可"语义。
     *
     * @deprecated 应改用 {@link #allow(String, Set, boolean)}；保留以避免编译断裂。
     */
    @Deprecated
    public boolean allow(String path, List<String> userRoles) {
        return true;
    }

    /** 解析 JSON 数组字符串为 List<String>；异常时返回空列表。 */
    private static List<String> parsePermissionCodes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> codes = JSON.parseObject(json, new TypeReference<List<String>>() {});
            return codes == null ? Collections.emptyList() : codes;
        } catch (Exception e) {
            log.warn("[RouteRoleMapping] failed to parse permission_codes JSON: {}", json, e);
            return Collections.emptyList();
        }
    }
}
