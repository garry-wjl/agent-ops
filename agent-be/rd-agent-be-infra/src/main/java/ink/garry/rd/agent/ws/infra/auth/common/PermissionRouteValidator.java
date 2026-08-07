package ink.garry.rd.agent.ws.infra.auth.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.RoutePermissionEntity;
import ink.garry.rd.agent.ws.infra.auth.permission.mapper.RoutePermissionMapper;
import ink.garry.rd.agent.ws.infra.auth.permission.PermissionRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 启动完整性校验：验证 {@code route_permission} 表中引用的所有权限码在 {@code permission} 表中均存在。
 *
 * <p>设计意图：DB 作为唯一真相源后，{@code PermissionCode.java} 已删除，
 * 编译期不再有静态常量保护。此 Validator 在启动阶段 fail-fast，
 * 防止 {@code route_permission} 中出现拼写错误或未迁移的权限码，
 * 避免运行时鉴权静默失效。</p>
 *
 * <p>执行时机：所有 Bean 初始化完成 + Flyway 迁移完成后，Spring Boot {@code ApplicationRunner} 阶段。</p>
 */
@Slf4j
@Component
public class PermissionRouteValidator implements ApplicationRunner {

    @Resource
    private RoutePermissionMapper routePermissionMapper;
    @Resource
    private PermissionRegistry permissionRegistry;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[PermissionRouteValidator] 开始校验 route_permission 引用完整性...");

        Set<String> validCodes = permissionRegistry.allCodes();
        List<RoutePermissionEntity> routes = routePermissionMapper.listAll();

        List<String> invalidEntries = new ArrayList<>();
        for (RoutePermissionEntity route : routes) {
            List<String> codes = parseCodes(route.getPermissionCodes());
            for (String code : codes) {
                if (!validCodes.contains(code)) {
                    invalidEntries.add(
                            String.format("path_pattern='%s' 引用了不存在的权限码: '%s'",
                                    route.getPathPattern(), code));
                }
            }
        }

        if (!invalidEntries.isEmpty()) {
            String msg = "[PermissionRouteValidator] 启动校验失败：route_permission 存在无效权限码引用，"
                    + "请检查 Flyway migration 是否完整。\n"
                    + String.join("\n", invalidEntries);
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[PermissionRouteValidator] 校验通过：{} 条路由映射，{} 个有效权限码。",
                routes.size(), validCodes.size());
    }

    private static List<String> parseCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> codes = JSON.parseObject(json, new TypeReference<List<String>>() {});
            return codes == null ? List.of() : codes;
        } catch (Exception e) {
            return List.of();
        }
    }
}
