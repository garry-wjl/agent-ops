package ink.garry.rd.agent.ws.infra.auth.permission;

import cn.hutool.core.collection.CollUtil;
import ink.garry.rd.agent.ws.domain.auth.permission.PermissionMetadata;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.PermissionEntity;
import ink.garry.rd.agent.ws.infra.auth.permission.mapper.PermissionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限元数据注册中心（无缓存，每次直接从 DB 读取）。
 * <p>application / domain 层校验 permissionCodes 合法性时统一查此 Bean。
 * scope 字段（V31 新增）替代 {@code AuthzQueryService.PLATFORM_SCOPE_DOMAINS} 硬编码集合。</p>
 */
@Slf4j
@Component
public class PermissionRegistry {

    @Resource
    private PermissionMapper permissionMapper;

    /**
     * 返回全部权限元数据，按 sort_order 升序。
     */
    public Map<String, PermissionMetadata> listAll() {
        List<PermissionEntity> entities = permissionMapper.listAllOrderBySortOrder();
        Map<String, PermissionMetadata> result = new LinkedHashMap<>();
        for (PermissionEntity entity : entities) {
            result.put(entity.getCode(), entity.toDomain());
        }
        return result;
    }

    /**
     * 按 scope 返回权限元数据，按 sort_order 升序。
     *
     * @param scope {@code "PLATFORM"} 或 {@code "SPACE"}
     */
    public Map<String, PermissionMetadata> listByScope(String scope) {
        List<PermissionEntity> entities = permissionMapper.listByScope(scope);
        Map<String, PermissionMetadata> result = new LinkedHashMap<>();
        for (PermissionEntity entity : entities) {
            result.put(entity.getCode(), entity.toDomain());
        }
        return result;
    }

    /**
     * 按 code 查权限；不存在返回 null。
     */
    public PermissionMetadata findByCode(String code) {
        if (code == null) {
            return null;
        }
        PermissionEntity entity = permissionMapper.selectById(code);
        return entity == null ? null : entity.toDomain();
    }

    /**
     * 全部权限码集合。
     */
    public Set<String> allCodes() {
        return listAll().keySet();
    }

    /**
     * 校验权限码全部存在。
     *
     * @param codes 待校验集合；null / 空时返回 true
     * @return 全部存在返回 true
     */
    public boolean containsAll(Set<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return true;
        }
        return allCodes().containsAll(codes);
    }
}