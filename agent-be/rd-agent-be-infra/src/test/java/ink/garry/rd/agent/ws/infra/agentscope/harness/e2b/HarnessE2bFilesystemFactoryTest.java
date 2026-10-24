package ink.garry.rd.agent.ws.infra.agentscope.harness.e2b;

import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.harness.agent.IsolationScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HarnessE2bFilesystemFactory}：隔离级别解析与 E2B 选项装配。
 */
class HarnessE2bFilesystemFactoryTest {

    private HarnessE2bFilesystemFactory factory;
    private SandboxProperties props;

    @BeforeEach
    void setUp() {
        factory = new HarnessE2bFilesystemFactory();
        props = new SandboxProperties();
        props.setApiKey("test-key");
        props.setDomain("agent-sandbox.example.com");
        props.setProtocol("https");
        props.setTemplate("code-interpreter");
        props.setTtlMinutes(10L);
        ReflectionTestUtils.setField(factory, "sandboxProperties", props);
        ReflectionTestUtils.setField(factory, "ossEndpoint", "https://oss.example.com");
        ReflectionTestUtils.setField(factory, "ossAccessKey", "ak");
        ReflectionTestUtils.setField(factory, "ossSecretKey", "sk");
        ReflectionTestUtils.setField(factory, "ossBucketName", "bucket");
    }

    @Test
    void parseIsolationScope_defaultsAndValid() {
        assertEquals(IsolationScope.SESSION, HarnessE2bFilesystemFactory.parseIsolationScope(null));
        assertEquals(IsolationScope.SESSION, HarnessE2bFilesystemFactory.parseIsolationScope(""));
        assertEquals(IsolationScope.SESSION, HarnessE2bFilesystemFactory.parseIsolationScope("bogus"));
        assertEquals(IsolationScope.USER, HarnessE2bFilesystemFactory.parseIsolationScope("user"));
        assertEquals(IsolationScope.AGENT, HarnessE2bFilesystemFactory.parseIsolationScope("AGENT"));
        assertEquals(IsolationScope.GLOBAL, HarnessE2bFilesystemFactory.parseIsolationScope("GLOBAL"));
    }

    @Test
    void create_assemblesSessionScopeAndApiBaseUrl() {
        E2bFilesystemSpec spec = factory.create(
                "SESSION", "WS-1", "AGT-1", Map.of("FOO", "bar"));
        assertNotNull(spec);
        assertEquals(IsolationScope.SESSION, spec.getIsolationScope());
    }

    @Test
    void create_userIsolation() {
        E2bFilesystemSpec spec = factory.create("USER", "WS-1", "AGT-1", null);
        assertEquals(IsolationScope.USER, spec.getIsolationScope());
    }

    @Test
    void create_requiresApiKey() {
        props.setApiKey("");
        assertThrows(IllegalStateException.class,
                () -> factory.create("SESSION", "WS", "AGT", null));
    }

    @Test
    void resolveApiBaseUrl_fromDomain() {
        assertEquals("https://api.agent-sandbox.example.com", props.resolveApiBaseUrl());
        props.setManagerEndpoint("127.0.0.1:18080");
        assertEquals("https://127.0.0.1:18080", props.resolveApiBaseUrl());
    }
}
