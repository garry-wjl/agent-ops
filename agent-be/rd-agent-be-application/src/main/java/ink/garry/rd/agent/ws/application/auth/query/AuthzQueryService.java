package ink.garry.rd.agent.ws.application.auth.query;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionGroupVO;
import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleDetailVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleSummaryVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleVO;
import ink.garry.rd.agent.ws.domain.auth.permission.PermissionMetadata;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository.UserRoleBindingRepository;
import ink.garry.rd.agent.ws.infra.auth.common.AuthzRedisKeys;
import ink.garry.rd.agent.ws.infra.auth.permission.PermissionRegistry;
import ink.garry.rd.agent.ws.infra.auth.role.entity.RoleEntity;
import ink.garry.rd.agent.ws.infra.auth.role.mapper.RoleMapper;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper.UserRoleBindingMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 鉴权读侧应用服务（QueryService）。
 * <p>提供角色列表 / 权限元数据 / 用户权限解析等只读能力；JwtAuthenticationFilter 即通过本服务获取权限集。</p>
 */
@Slf4j
@Service
public class AuthzQueryService {

    /** Redis 缓存 TTL（30 分钟，与 PRD §12 兜底窗口一致） */
    private static final Duration PERM_CACHE_TTL = Duration.ofMinutes(30);

    /** 资源域中文名映射（与 Flyway V23 内置数据对齐） */
    private static final Map<String, String> RESOURCE_DOMAIN_LABELS = Map.ofEntries(
            Map.entry("agent", "Agent 管理"),
            Map.entry("skill", "Skill 管理"),
            Map.entry("tool", "工具管理"),
            Map.entry("knowledge_base", "知识库"),
            Map.entry("evaluation", "评测"),
            Map.entry("debug_console", "调试台"),
            Map.entry("prompt", "Prompt 中心"),
            Map.entry("sandbox", "沙箱管理"),
            Map.entry("model", "模型管理"),
            Map.entry("system", "系统设置"),
            Map.entry("workspace", "空间管理"),
            Map.entry("role_manage", "角色管理"),
            Map.entry("user_role", "用户角色")
    );

    @Resource
    private RoleMapper roleMapper;
    @Resource
    private UserRoleBindingMapper userRoleBindingMapper;
    @Resource
    private UserRoleBindingRepository userRoleBindingRepository;
    @Resource
    private PermissionRegistry permissionRegistry;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 列出空间内全部角色（内置 SPACE 模板 + 该空间自定义）。
     */
    public List<RoleVO> listRolesInWorkspace(String workspaceNum) {
        List<RoleEntity> rows = roleMapper.listByScopeAndWorkspace(workspaceNum);
        List<RoleVO> result = new ArrayList<>(rows.size());
        for (RoleEntity row : rows) {
            RoleVO vo = new RoleVO();
            vo.setRoleNum(row.getNum());
            vo.setName(row.getName());
            vo.setDescription(row.getDescription());
            vo.setScope(row.getScope());
            vo.setBuiltin(row.getBuiltin() != null && row.getBuiltin() == 1);
            vo.setAssignedUserCount(
                    userRoleBindingMapper.countByRoleNumAndWorkspace(row.getNum(), workspaceNum));
            result.add(vo);
        }
        return result;
    }

    /**
     * 全平台角色总览（仅 platform_admin 调用）。
     */
    public List<RoleSummaryVO> listAllRolesForPlatformAdmin() {
        List<RoleEntity> rows = roleMapper.listAllForPlatformAdmin();
        return rows.stream().map(this::toSummary).collect(Collectors.toList());
    }

    /**
     * 仅平台级角色（scope=PLATFORM）。
     */
    public List<RoleSummaryVO> listPlatformRoles() {
        List<RoleEntity> rows = roleMapper.listAllForPlatformAdmin().stream()
                .filter(r -> "PLATFORM".equals(r.getScope()))
                .collect(Collectors.toList());
        return rows.stream().map(this::toSummary).collect(Collectors.toList());
    }

    private RoleSummaryVO toSummary(RoleEntity row) {
        RoleSummaryVO vo = new RoleSummaryVO();
        vo.setRoleNum(row.getNum());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription());
        vo.setScope(row.getScope());
        vo.setWorkspaceNum(row.getWorkspaceNum());
        vo.setBuiltin(row.getBuiltin() != null && row.getBuiltin() == 1);
        vo.setAssignedUserCount(userRoleBindingMapper.countByRoleNum(row.getNum()));
        vo.setPermissionCount(parseCodes(row.getPermissionCodes()).size());
        return vo;
    }

    /**
     * 角色权限明细（按资源域分组）。
     */
    public RoleDetailVO getRoleDetail(String roleNum) {
        RoleEntity row = roleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getNum, roleNum));
        if (row == null) {
            return null;
        }
        RoleDetailVO vo = new RoleDetailVO();
        vo.setRoleNum(row.getNum());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription());
        vo.setScope(row.getScope());
        vo.setWorkspaceNum(row.getWorkspaceNum());
        vo.setBuiltin(row.getBuiltin() != null && row.getBuiltin() == 1);
        vo.setAssignedUserCount(userRoleBindingMapper.countByRoleNum(row.getNum()));
        Set<String> own = parseCodes(row.getPermissionCodes());
        vo.setPermissionGroups(buildGroups(own));
        return vo;
    }

    /**
     * 全部权限元数据按资源域分组。
     *
     * @param scope 可选过滤：
     *              {@code "PLATFORM"} 仅返回 scope=PLATFORM 的权限域（DB 驱动，无硬编码）；
     *              {@code "SPACE"} 仅返回 scope=SPACE 的权限域；
     *              null / 其它值返回全集。
     */
    public List<PermissionGroupVO> listPermissions(String scope) {
        if ("PLATFORM".equals(scope)) {
            return buildGroupsFromRegistry(permissionRegistry.listByScope("PLATFORM"), null);
        }
        if ("SPACE".equals(scope)) {
            return buildGroupsFromRegistry(permissionRegistry.listByScope("SPACE"), null);
        }
        return buildGroups(null);
    }

    /** 全部权限元数据按资源域分组（不过滤）。 */
    public List<PermissionGroupVO> listPermissions() {
        return listPermissions(null);
    }

    /**
     * 当前空间所有成员-角色映射（编辑抽屉用）。
     */
    public Map<String, List<RoleVO>> listMemberRoles(String workspaceNum) {
        List<UserRoleBinding> bindings = userRoleBindingRepository.listByWorkspace(workspaceNum);
        if (bindings.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 收集所有角色 num 去重批量查 RoleEntity 装成 RoleVO 缓存
        Set<String> roleNums = bindings.stream()
                .flatMap(b -> b.getRoleNums() == null ? java.util.stream.Stream.empty() : b.getRoleNums().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, RoleVO> roleVoCache = new LinkedHashMap<>();
        for (String roleNum : roleNums) {
            RoleEntity entity = roleMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleEntity>()
                            .eq(RoleEntity::getNum, roleNum));
            if (entity == null) {
                continue;
            }
            RoleVO vo = new RoleVO();
            vo.setRoleNum(entity.getNum());
            vo.setName(entity.getName());
            vo.setDescription(entity.getDescription());
            vo.setScope(entity.getScope());
            vo.setBuiltin(entity.getBuiltin() != null && entity.getBuiltin() == 1);
            roleVoCache.put(roleNum, vo);
        }
        Map<String, List<RoleVO>> result = new LinkedHashMap<>();
        for (UserRoleBinding binding : bindings) {
            List<RoleVO> userRoleList = new ArrayList<>();
            if (binding.getRoleNums() != null) {
                for (String roleNum : binding.getRoleNums()) {
                    RoleVO vo = roleVoCache.get(roleNum);
                    if (vo != null) {
                        userRoleList.add(vo);
                    }
                }
            }
            result.put(binding.getUserId(), userRoleList);
        }
        return result;
    }

    /**
     * 当前用户在该空间持有的角色编号列表。
     */
    public List<String> listRoleNumsByUser(String userId, String workspaceNum) {
        UserRoleBinding binding = userRoleBindingRepository.findByUserAndWorkspace(userId, workspaceNum);
        if (binding == null || binding.getRoleNums() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(binding.getRoleNums());
    }

    /**
     * 列出全部平台管理员（workspace_num = SYSTEM 下的所有用户-角色映射，empNo → RoleVO 列表）。
     * <p>仅供 platform_admin 在「系统 → 用户与平台角色」页面查询使用。</p>
     */
    public Map<String, List<RoleVO>> listPlatformAdmins() {
        return listMemberRoles(ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants.PLATFORM_WORKSPACE_NUM);
    }

    /**
     * 判定平台管理员。
     */
    public boolean isPlatformAdmin(String userId) {
        return userRoleBindingMapper.countPlatformAdminRows(userId) > 0;
    }

    /**
     * 解析用户在指定空间的权限并集（含 Redis 缓存）。
     * <p>platform_admin 直接返回 PermissionRegistry 全集。</p>
     */
    public Set<String> resolveUserPermissions(String userId, String workspaceNum) {
        if (isPlatformAdmin(userId)) {
            return new LinkedHashSet<>(permissionRegistry.allCodes());
        }
        String cacheKey = AuthzRedisKeys.permKey(userId, workspaceNum);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<String> list = JSON.parseObject(cached, new TypeReference<List<String>>() {});
                if (list != null) {
                    return new LinkedHashSet<>(list);
                }
            }
        } catch (Exception ex) {
            log.warn("[AuthzQueryService] read perm cache failed key={}", cacheKey, ex);
        }
        Set<String> permissions = loadPermissionsFromDb(userId, workspaceNum);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    JSON.toJSONString(new ArrayList<>(permissions)), PERM_CACHE_TTL);
        } catch (Exception ex) {
            log.warn("[AuthzQueryService] write perm cache failed key={}", cacheKey, ex);
        }
        return permissions;
    }

    /**
     * Evict 单用户在指定空间的权限缓存（listener 调用）。
     */
    public void evictUserPermissionCache(String userId, String workspaceNum) {
        if (userId == null || workspaceNum == null) {
            return;
        }
        stringRedisTemplate.delete(AuthzRedisKeys.permKey(userId, workspaceNum));
    }

    // ---- 私有辅助 ----

    private Set<String> loadPermissionsFromDb(String userId, String workspaceNum) {
        UserRoleBinding binding = userRoleBindingRepository.findByUserAndWorkspace(userId, workspaceNum);
        if (binding == null || binding.getRoleNums() == null || binding.getRoleNums().isEmpty()) {
            return new HashSet<>();
        }
        List<String> roleNums = new ArrayList<>(binding.getRoleNums());
        List<String> codeListJson = roleMapper.listPermissionCodesByRoleNums(roleNums);
        Set<String> union = new LinkedHashSet<>();
        for (String codes : codeListJson) {
            union.addAll(parseCodes(codes));
        }
        // 防御性过滤：剔除已被移除的权限码
        union.retainAll(permissionRegistry.allCodes());
        return union;
    }

    private static Set<String> parseCodes(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {});
            return list == null ? new LinkedHashSet<>() : new LinkedHashSet<>(list);
        } catch (Exception ignore) {
            return new LinkedHashSet<>();
        }
    }

    /**
     * 按资源域分组权限元数据（全量）。
     *
     * @param selected 当前角色已选权限（getRoleDetail 场景非空；listPermissions 场景为 null）
     */
    private List<PermissionGroupVO> buildGroups(Set<String> selected) {
        return buildGroupsFromRegistry(permissionRegistry.listAll(), selected);
    }

    /**
     * 按资源域分组权限元数据（指定元数据集合）。
     *
     * @param metaMap  权限元数据集合（已按 scope 或全量过滤）
     * @param selected 当前角色已选权限（getRoleDetail 场景非空；listPermissions 场景为 null）
     */
    private List<PermissionGroupVO> buildGroupsFromRegistry(
            Map<String, PermissionMetadata> metaMap, Set<String> selected) {
        Map<String, PermissionGroupVO> groups = new LinkedHashMap<>();
        for (PermissionMetadata meta : metaMap.values()) {
            PermissionGroupVO group = groups.computeIfAbsent(meta.resourceDomain(), domain -> {
                PermissionGroupVO g = new PermissionGroupVO();
                g.setResourceDomain(domain);
                g.setResourceDomainName(RESOURCE_DOMAIN_LABELS.getOrDefault(domain, domain));
                g.setPermissions(new ArrayList<>());
                return g;
            });
            PermissionVO vo = new PermissionVO();
            vo.setCode(meta.code());
            vo.setName(meta.name());
            vo.setDescription(meta.description());
            if (selected != null) {
                vo.setSelected(selected.contains(meta.code()));
            }
            group.getPermissions().add(vo);
        }
        if (selected != null) {
            // 角色已选但 Registry 缺失时也保留（防御性，提示数据漂移）
            for (String orphan : selected) {
                if (permissionRegistry.findByCode(orphan) == null) {
                    PermissionGroupVO group = groups.computeIfAbsent("role_manage", domain -> {
                        PermissionGroupVO g = new PermissionGroupVO();
                        g.setResourceDomain(domain);
                        g.setResourceDomainName(RESOURCE_DOMAIN_LABELS.get(domain));
                        g.setPermissions(new ArrayList<>());
                        return g;
                    });
                    PermissionVO vo = new PermissionVO();
                    vo.setCode(orphan);
                    vo.setName(orphan);
                    vo.setDescription("（已下线权限）");
                    vo.setSelected(true);
                    group.getPermissions().add(vo);
                }
            }
        }
        return new ArrayList<>(groups.values());
    }
}
