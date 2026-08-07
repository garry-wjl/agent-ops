package ink.garry.rd.agent.ws.application.model;

import ink.garry.rd.agent.ws.client.model.dto.ModelDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelSelectableDTO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.model.entity.ModelEntity;
import ink.garry.rd.agent.ws.infra.model.mapper.ModelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelQueryService scope/selectable 行为测试。
 */
class ModelQueryServiceScopeTest {

    private ModelMapper modelMapper;

    private ModelQueryService service;

    @BeforeEach
    void setUp() {
        service = new ModelQueryService();
    }

    @Test
    void listSelectable_shouldReturnEnabledPlatformAndCurrentWorkspaceModelsWithoutApiKey() {
        ModelEntity platform = model("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        platform.setApiKeyPrefix("sk-platform");
        ModelEntity space = model("MDL-SPACE", "WS-001", ModelScope.SPACE.name(), "ENABLED");
        space.setApiKeyPrefix("sk-space");

        modelMapper = mapperWith(List.of(List.of(platform), List.of(space)), List.of());
        injectField("modelMapper", modelMapper);

        List<ModelSelectableDTO> result = service.listSelectable("WS-001");

        assertEquals(2, result.size());
        assertEquals("PLATFORM", result.get(0).getScope());
        assertEquals("SPACE", result.get(1).getScope());
        assertNull(result.get(0).getApiKeyMasked());
        assertNull(result.get(1).getApiKeyMasked());
    }

    @Test
    void requireSelectableEnabled_shouldRejectSpaceModelFromOtherWorkspace() {
        ModelEntity otherSpace = model("MDL-OTHER", "WS-002", ModelScope.SPACE.name(), "ENABLED");
        modelMapper = mapperWith(List.of(), List.of(otherSpace));
        injectField("modelMapper", modelMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireSelectableEnabled("MDL-OTHER", "WS-001"));

        assertTrue(ex.getMessage().contains("无权访问"));
    }

    @Test
    void requireSelectableEnabled_shouldAllowPlatformModel() {
        ModelEntity platform = model("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        modelMapper = mapperWith(List.of(), List.of(platform));
        injectField("modelMapper", modelMapper);

        ModelDTO result = service.requireSelectableEnabled("MDL-PLATFORM", "WS-001");

        assertEquals("MDL-PLATFORM", result.getNum());
        assertEquals(ModelScope.PLATFORM.name(), result.getScope());
    }

    /**
     * 正向脱敏断言(方案 §13):空间模型 detail 路径仍应带 apiKeyMasked 脱敏串。
     * <p>补齐原测试只断言 selectable 两端为 null 的缺口,确保脱敏回归可见。
     */
    @Test
    void getDetail_spaceModel_shouldKeepMaskedApiKey() {
        ModelEntity space = model("MDL-SPACE", "WS-001", ModelScope.SPACE.name(), "ENABLED");
        space.setApiKeyPrefix("sk-abcd");
        modelMapper = mapperWith(List.of(), List.of(space));
        injectField("modelMapper", modelMapper);

        ModelDTO detail = service.getDetail("MDL-SPACE", ModelScope.SPACE, "WS-001").getModel();

        assertNotNull(detail);
        assertEquals("sk-abcd****", detail.getApiKeyMasked());
    }

    /** 空间模型脱敏:requireSelectableEnabled 返回的空间模型也应带脱敏串。 */
    @Test
    void requireSelectableEnabled_spaceModel_shouldKeepMaskedApiKey() {
        ModelEntity space = model("MDL-SPACE", "WS-001", ModelScope.SPACE.name(), "ENABLED");
        space.setApiKeyPrefix("sk-space");
        modelMapper = mapperWith(List.of(), List.of(space));
        injectField("modelMapper", modelMapper);

        ModelDTO result = service.requireSelectableEnabled("MDL-SPACE", "WS-001");

        assertEquals("sk-space****", result.getApiKeyMasked());
    }

    /** 系统模型脱敏:requireSelectableEnabled 返回的 PLATFORM 模型 apiKeyMasked 必须为 null。 */
    @Test
    void requireSelectableEnabled_platformModel_shouldNotExposeMaskedKey() {
        ModelEntity platform = model("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        platform.setApiKeyPrefix("sk-platform");
        modelMapper = mapperWith(List.of(), List.of(platform));
        injectField("modelMapper", modelMapper);

        ModelDTO result = service.requireSelectableEnabled("MDL-PLATFORM", "WS-001");

        assertNull(result.getApiKeyMasked(), "系统模型出参不得携带脱敏 Key");
    }

    private static ModelEntity model(String num, String workspaceNum, String scope, String status) {
        ModelEntity entity = new ModelEntity();
        entity.setNum(num);
        entity.setWorkspaceNum(workspaceNum);
        entity.setScope(scope);
        entity.setName(num + "-name");
        entity.setModelId(num + "-model");
        entity.setBaseUrl("https://example.com");
        entity.setStatus(status);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static ModelMapper mapperWith(List<List<ModelEntity>> selectListResults,
                                          List<ModelEntity> selectOneResults) {
        List<List<ModelEntity>> lists = new ArrayList<>(selectListResults);
        List<ModelEntity> ones = new ArrayList<>(selectOneResults);
        AtomicInteger listIndex = new AtomicInteger();
        AtomicInteger oneIndex = new AtomicInteger();
        return (ModelMapper) Proxy.newProxyInstance(
                ModelMapper.class.getClassLoader(),
                new Class<?>[]{ModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return lists.get(listIndex.getAndIncrement());
                    }
                    if ("selectOne".equals(method.getName())) {
                        return ones.get(oneIndex.getAndIncrement());
                    }
                    if ("toString".equals(method.getName())) {
                        return "ModelMapperProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private void injectField(String name, Object value) {
        try {
            var field = ModelQueryService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
