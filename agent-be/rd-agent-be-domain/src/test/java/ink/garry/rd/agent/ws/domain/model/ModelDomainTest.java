package ink.garry.rd.agent.ws.domain.model;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.model.dto.ModelDomainEventDTO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Model 聚合根 scope / workspace 一致性 + 领域事件载荷测试。
 * <p>覆盖方案 §4.2.3(scope 一致性规则)与 §4.2.6(事件载荷携带 scope)。
 * <p>Model 是纯状态机,无需 Spring 容器;用反射装配三个 transient 依赖为 null,
 * save() 内网关为 null 时只影响 num 生成,故这里只覆盖 domainValidate / 事件 DTO 语义。
 */
class ModelDomainTest {

    /** SPACE 模型必须带 workspaceNum,否则 domainValidate 拒绝。 */
    @Test
    void domainValidate_spaceModelWithoutWorkspace_shouldReject() {
        Model m = newModel(ModelScope.SPACE, null, "GPT-4o", "gpt-4o", "sk-x", "https://api.openai.com");
        assertThrows(RuntimeException.class, m::domainValidate);
    }

    /** SPACE 模型带 workspaceNum 时 domainValidate 通过。 */
    @Test
    void domainValidate_spaceModelWithWorkspace_shouldPass() {
        Model m = newModel(ModelScope.SPACE, "WS-001", "GPT-4o", "gpt-4o", "sk-x", "https://api.openai.com");
        assertDoesNotThrow(m::domainValidate);
    }

    /** PLATFORM 模型 workspaceNum 必须为空,否则 domainValidate 拒绝。 */
    @Test
    void domainValidate_platformModelWithWorkspace_shouldReject() {
        Model m = newModel(ModelScope.PLATFORM, "WS-001", "GPT-4o", "gpt-4o", "sk-x", "https://api.openai.com");
        assertThrows(RuntimeException.class, m::domainValidate);
    }

    /** PLATFORM 模型 workspaceNum 为空时 domainValidate 通过。 */
    @Test
    void domainValidate_platformModelWithoutWorkspace_shouldPass() {
        Model m = newModel(ModelScope.PLATFORM, null, "Claude", "claude-3", "sk-x", "https://api.anthropic.com");
        assertDoesNotThrow(m::domainValidate);
    }

    /** scope 为空时兜底为 SPACE(方案 §4.2 一致性规则)。 */
    @Test
    void domainValidate_nullScope_shouldFallbackToSpace() {
        Model m = newModel(null, "WS-001", "GPT-4o", "gpt-4o", "sk-x", "https://api.openai.com");
        assertDoesNotThrow(m::domainValidate);
        assertEquals(ModelScope.SPACE, m.getScope());
    }

    // ============================================================
    // 领域事件载荷:必须携带 scope 且不含 apiKey(方案 §4.2.6)
    // ============================================================

    /** MODEL_SAVED 事件载荷应携带 scope 字段(PLATFORM)。 */
    @Test
    void eventPayload_platformModel_shouldCarryScope() {
        Model m = newModel(ModelScope.PLATFORM, null, "Claude", "claude-3", "sk-secret", "https://api.anthropic.com");

        ModelDomainEventDTO dto = ModelDomainEventDTO.from(m, "EMP001");

        assertEquals(ModelScope.PLATFORM.name(), dto.getScope());
        assertEquals("Claude", dto.getName());
        // ModelDomainEventDTO 类本身不含 apiKey 字段(安全约束),反射自检确认无该字段
        assertDtoHasNoApiKeyField();
    }

    /** 事件载荷 SPACE 模型也应携带 scope。 */
    @Test
    void eventPayload_spaceModel_shouldCarryScope() {
        Model m = newModel(ModelScope.SPACE, "WS-001", "GPT-4o", "gpt-4o", "sk-secret", "https://api.openai.com");

        ModelDomainEventDTO dto = ModelDomainEventDTO.from(m, "EMP001");

        assertEquals(ModelScope.SPACE.name(), dto.getScope());
        assertEquals("WS-001", dto.getWorkspaceNum());
    }

    /** 反射自检:ModelDomainEventDTO 有 scope 字段(防止回退)。 */
    @Test
    void eventPayloadDto_shouldHaveScopeField() throws Exception {
        Field scopeField = ModelDomainEventDTO.class.getDeclaredField("scope");
        assertEquals(String.class, scopeField.getType());
    }

    /** 反射自检:ModelDomainEventDTO 不含任何 apiKey 相关字段(安全约束 §4.2.6)。 */
    @Test
    void eventPayloadDto_shouldNotHaveAnyApiKeyField() {
        assertDtoHasNoApiKeyField();
    }

    /** 断言事件载荷 DTO 上不存在 apiKey / apiKeyCipher / apiKeyPrefix 任一字段。 */
    private static void assertDtoHasNoApiKeyField() {
        for (Field f : ModelDomainEventDTO.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            if (n.contains("apikey")) {
                throw new AssertionError("事件载荷不得包含 apiKey 相关字段: " + f.getName());
            }
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static Model newModel(ModelScope scope, String workspaceNum, String name,
                                  String modelId, String apiKey, String baseUrl) {
        Model m = new Model();
        m.setScope(scope);
        m.setWorkspaceNum(workspaceNum);
        m.setName(name);
        m.setModelId(modelId);
        m.setApiKey(apiKey);
        m.setBaseUrl(baseUrl);
        m.setStatus(ModelStatus.ENABLED);
        return m;
    }
}
