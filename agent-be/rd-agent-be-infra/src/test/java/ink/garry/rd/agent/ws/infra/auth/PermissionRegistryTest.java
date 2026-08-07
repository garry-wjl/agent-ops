package ink.garry.rd.agent.ws.infra.auth;

import ink.garry.rd.agent.ws.domain.auth.permission.PermissionMetadata;
import ink.garry.rd.agent.ws.infra.auth.permission.PermissionRegistry;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.PermissionEntity;
import ink.garry.rd.agent.ws.infra.auth.permission.mapper.PermissionMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PermissionRegistry 无缓存测试：每次调用直接查 DB。
 */
class PermissionRegistryTest {

    @Test
    void findAll_shouldQueryDbEachCall() {
        MapperStub stub = mapperStub(List.of(
                List.of(
                        entity("model:read", "查看模型", "model"),
                        entity("agent:create", "创建 Agent", "agent"),
                        entity("role_manage:create", "新建角色", "role_manage")
                )
        ), Map.of());
        PermissionRegistry registry = registryWith(stub.mapper);

        Map<String, PermissionMetadata> result = registry.listAll();

        assertEquals(3, result.size());
        assertTrue(result.containsKey("model:read"));
        assertTrue(result.containsKey("agent:create"));
        assertTrue(result.containsKey("role_manage:create"));
        assertEquals(1, stub.listAllCalls.get());
    }

    @Test
    void findAll_calledTwice_shouldQueryDbTwice() {
        MapperStub stub = mapperStub(List.of(
                List.of(entity("a", "A", "test")),
                List.of(entity("a", "A", "test"), entity("b", "B", "test"))
        ), Map.of());
        PermissionRegistry registry = registryWith(stub.mapper);

        Map<String, PermissionMetadata> first = registry.listAll();
        assertEquals(1, first.size());

        Map<String, PermissionMetadata> second = registry.listAll();
        assertEquals(2, second.size());

        assertEquals(2, stub.listAllCalls.get());
    }

    @Test
    void findByCode_shouldQueryDb() {
        MapperStub stub = mapperStub(List.of(), Map.of(
                "model:read", entity("model:read", "查看模型", "model")
        ));
        PermissionRegistry registry = registryWith(stub.mapper);

        PermissionMetadata meta = registry.findByCode("model:read");

        assertNotNull(meta);
        assertEquals("model:read", meta.code());
        assertEquals("查看模型", meta.name());
        assertEquals(1, stub.selectByIdCalls.getOrDefault("model:read", new AtomicInteger()).get());
    }

    @Test
    void findByCode_notFound_shouldReturnNull() {
        PermissionRegistry registry = registryWith(mapperStub(List.of(), Map.of()).mapper);

        assertNull(registry.findByCode("nonexistent"));
    }

    @Test
    void findByCode_null_shouldReturnNull() {
        MapperStub stub = mapperStub(List.of(), Map.of());
        PermissionRegistry registry = registryWith(stub.mapper);

        assertNull(registry.findByCode(null));
        assertTrue(stub.selectByIdCalls.isEmpty());
    }

    @Test
    void allCodes_shouldReturnCodeSet() {
        PermissionRegistry registry = registryWith(mapperStub(List.of(
                List.of(
                        entity("a", "A", "test"),
                        entity("b", "B", "test")
                )
        ), Map.of()).mapper);

        Set<String> codes = registry.allCodes();

        assertEquals(Set.of("a", "b"), codes);
    }

    @Test
    void containsAll_allPresent_shouldReturnTrue() {
        PermissionRegistry registry = registryWith(mapperStub(List.of(
                List.of(
                        entity("a", "A", "test"),
                        entity("b", "B", "test"),
                        entity("c", "C", "test")
                )
        ), Map.of()).mapper);

        assertTrue(registry.containsAll(Set.of("a", "b")));
    }

    @Test
    void containsAll_someMissing_shouldReturnFalse() {
        PermissionRegistry registry = registryWith(mapperWithSingleList(entity("a", "A", "test")));

        assertFalse(registry.containsAll(Set.of("a", "b")));
    }

    @Test
    void containsAll_nullOrEmpty_shouldReturnTrue() {
        PermissionRegistry registry = registryWith(mapperStub(List.of(), Map.of()).mapper);

        assertTrue(registry.containsAll(null));
        assertTrue(registry.containsAll(Set.of()));
    }

    private static PermissionMapper mapperWithSingleList(PermissionEntity... entities) {
        return mapperStub(List.of(Arrays.asList(entities)), Map.of()).mapper;
    }

    private static MapperStub mapperStub(List<List<PermissionEntity>> listResults,
                                         Map<String, PermissionEntity> selectByIdResults) {
        MapperStub stub = new MapperStub();
        ArrayDeque<List<PermissionEntity>> lists = new ArrayDeque<>(listResults);
        stub.mapper = (PermissionMapper) Proxy.newProxyInstance(
                PermissionMapper.class.getClassLoader(),
                new Class<?>[]{PermissionMapper.class},
                (proxy, method, args) -> {
                    if ("listAllOrderBySortOrder".equals(method.getName())) {
                        stub.listAllCalls.incrementAndGet();
                        return lists.isEmpty() ? List.of() : lists.removeFirst();
                    }
                    if ("selectById".equals(method.getName())) {
                        String code = args == null || args.length == 0 ? null : String.valueOf(args[0]);
                        stub.selectByIdCalls.computeIfAbsent(code, ignored -> new AtomicInteger()).incrementAndGet();
                        return selectByIdResults.get(code);
                    }
                    if ("toString".equals(method.getName())) {
                        return "PermissionMapperProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return stub;
    }

    private static PermissionRegistry registryWith(PermissionMapper mapper) {
        PermissionRegistry registry = new PermissionRegistry();
        try {
            var field = PermissionRegistry.class.getDeclaredField("permissionMapper");
            field.setAccessible(true);
            field.set(registry, mapper);
            return registry;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PermissionEntity entity(String code, String name, String domain) {
        PermissionEntity e = new PermissionEntity();
        e.setCode(code);
        e.setName(name);
        e.setResourceDomain(domain);
        e.setDescription("desc");
        e.setSortOrder(1);
        return e;
    }

    private static class MapperStub {
        private PermissionMapper mapper;
        private final AtomicInteger listAllCalls = new AtomicInteger();
        private final Map<String, AtomicInteger> selectByIdCalls = new HashMap<>();
    }
}
