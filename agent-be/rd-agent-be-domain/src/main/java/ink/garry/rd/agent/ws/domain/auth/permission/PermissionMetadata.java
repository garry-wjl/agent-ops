package ink.garry.rd.agent.ws.domain.auth.permission;

/**
 * 权限元数据（不可变值对象）。
 * <p>由 PermissionRegistry 按需从 DB 读取，运行时只读；不属于聚合，不发事件。</p>
 *
 * @param code           权限编码（resource:action 格式）
 * @param name           权限中文名
 * @param resourceDomain 资源域（agent / skill / tool / knowledge_base / evaluation /
 *                       debug_console / prompt / sandbox / model / workspace / system）
 * @param scope          适用角色范围：PLATFORM=平台角色可分配；SPACE=空间角色可分配
 * @param description    权限描述
 * @param sortOrder      展示排序
 */
public record PermissionMetadata(
        String code,
        String name,
        String resourceDomain,
        String scope,
        String description,
        int sortOrder
) {
}
