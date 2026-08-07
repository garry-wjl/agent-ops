package ink.garry.rd.agent.ws.infra.model.gateway;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import ink.garry.rd.agent.ws.infra.common.util.SecretCipher;
import ink.garry.rd.agent.ws.infra.model.entity.ModelEntity;
import ink.garry.rd.agent.ws.infra.model.mapper.ModelMapper;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelCredentialResolver} 单元测试。
 * <p>覆盖方案 §5.1「注释/测试补强」要求：按 num 解密、ENABLED 守卫、跨 scope 不串用（运行时不感知 scope，
 * 仅按 num + status 解析凭证，授权已由保存/发布阶段完成）。
 */
class ModelCredentialResolverTest {

    private ModelCredentialResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ModelCredentialResolver();
        injectField("secretCipher", new SecretCipher("test-cipher-key"));
    }

    /** ENABLED 系统模型：按 num 解析并返回明文 apiKey(对应方案「跨 scope 不串用」)。 */
    @Test
    void resolve_enabledPlatformModel_shouldReturnDecryptedCredential() {
        SecretCipher cipher = new SecretCipher("test-cipher-key");
        ModelEntity platform = entity("MDL-PLATFORM", null, "PLATFORM", "ENABLED");
        platform.setApiKeyCipher(cipher.encrypt("sk-platform-secret"));

        injectField("modelMapper", mapperReturning(platform));

        ModelCredential cred = resolver.resolve("MDL-PLATFORM");

        assertEquals("gpt-4o", cred.modelId());
        assertEquals("https://api.example.com", cred.baseUrl());
        assertEquals("sk-platform-secret", cred.apiKey());
    }

    /** ENABLED 空间模型同样可解析：证明 resolver 只按 num + status，不按 scope 授权(方案 §6.4.1)。 */
    @Test
    void resolve_enabledSpaceModel_shouldReturnDecryptedCredentialRegardlessOfScope() {
        SecretCipher cipher = new SecretCipher("test-cipher-key");
        ModelEntity space = entity("MDL-SPACE", "WS-001", "SPACE", "ENABLED");
        space.setApiKeyCipher(cipher.encrypt("sk-space-secret"));

        injectField("modelMapper", mapperReturning(space));

        ModelCredential cred = resolver.resolve("MDL-SPACE");

        assertEquals("sk-space-secret", cred.apiKey());
    }

    /** 跨 scope「不串用」：调用方传哪个 num 就解析哪个记录的密文，不会被 scope 逻辑改写。 */
    @Test
    void resolve_twoModelsDifferentScope_shouldResolveCorrectCipherPerNum() {
        SecretCipher cipher = new SecretCipher("test-cipher-key");
        ModelEntity platform = entity("MDL-PLATFORM", null, "PLATFORM", "ENABLED");
        platform.setApiKeyCipher(cipher.encrypt("sk-platform"));

        ModelEntity space = entity("MDL-SPACE", "WS-001", "SPACE", "ENABLED");
        space.setApiKeyCipher(cipher.encrypt("sk-space"));

        // 同一测试中按 num 顺序返回两次不同记录
        injectField("modelMapper", mapperReturning(platform, space));

        assertEquals("sk-platform", resolver.resolve("MDL-PLATFORM").apiKey());
        assertEquals("sk-space", resolver.resolve("MDL-SPACE").apiKey());
    }

    /** 模型不存在(或已软删)抛 MODEL_NOT_AVAILABLE。 */
    @Test
    void resolve_notFound_shouldThrowModelNotAvailable() {
        injectField("modelMapper", mapperReturning((ModelEntity) null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.resolve("MDL-MISSING"));

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("不存在"));
    }

    /** 非启用态(DRAFT / DISABLED)抛 MODEL_NOT_AVAILABLE。 */
    @Test
    void resolve_notEnabled_shouldThrowModelNotAvailable() {
        ModelEntity draft = entity("MDL-DRAFT", "WS-001", "SPACE", "DRAFT");

        injectField("modelMapper", mapperReturning(draft));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.resolve("MDL-DRAFT"));

        assertTrue(ex.getMessage().contains("未启用"));
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static ModelEntity entity(String num, String workspaceNum, String scope, String status) {
        ModelEntity e = new ModelEntity();
        e.setNum(num);
        e.setWorkspaceNum(workspaceNum);
        e.setScope(scope);
        e.setName(num + "-name");
        e.setModelId("gpt-4o");
        e.setBaseUrl("https://api.example.com");
        e.setStatus(status);
        return e;
    }

    /**
     * 构造按调用顺序依次返回 {@code selectOne} 结果的 ModelMapper stub。
     *
     * @param results 按调用顺序排列的 selectOne 返回值(可含 null 表示查不到)
     * @return ModelMapper 代理
     */
    @SuppressWarnings("unchecked")
    private static ModelMapper mapperReturning(ModelEntity... results) {
        // 用 ArrayList + 游标而非 ArrayDeque:前者允许 null 元素(表示「查不到」语义)
        java.util.List<ModelEntity> queue = new java.util.ArrayList<>(java.util.Arrays.asList(results));
        java.util.concurrent.atomic.AtomicInteger cursor = new java.util.concurrent.atomic.AtomicInteger();
        return (ModelMapper) Proxy.newProxyInstance(
                ModelMapper.class.getClassLoader(),
                new Class<?>[]{ModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectOne".equals(method.getName()) && method.getParameterCount() == 1
                            && Wrapper.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        int i = cursor.getAndIncrement();
                        return i < queue.size() ? queue.get(i) : null;
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
            var field = ModelCredentialResolver.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(resolver, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
