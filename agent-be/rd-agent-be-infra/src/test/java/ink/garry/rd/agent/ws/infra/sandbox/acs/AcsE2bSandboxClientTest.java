package ink.garry.rd.agent.ws.infra.sandbox.acs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcsE2bSandboxClientTest {

    private HttpServer manager;
    private HttpServer gateway;
    private AcsE2bSandboxClient client;
    private final AtomicInteger creates = new AtomicInteger();
    private final AtomicReference<String> lastTimeoutBody = new AtomicReference<>();
    private final AtomicReference<String> lastCreateBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        manager = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        manager.createContext("/sandboxes", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(method) && "/sandboxes".equals(path)) {
                creates.incrementAndGet();
                lastCreateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = """
                        {"sandboxID":"sbx-test-1","envdAccessToken":"tok-1","state":"running"}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
                return;
            }
            if ("GET".equals(method) && path.startsWith("/sandboxes/")) {
                if (!"/sandboxes/sbx-test-1".equals(path)) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                    return;
                }
                byte[] body = """
                        {"sandboxID":"sbx-test-1","envdAccessToken":"tok-1","state":"running"}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
                return;
            }
            if ("DELETE".equals(method) && path.startsWith("/sandboxes/")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if ("POST".equals(method) && path.endsWith("/timeout")) {
                byte[] req = exchange.getRequestBody().readAllBytes();
                lastTimeoutBody.set(new String(req, StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        gateway.createContext("/files", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = "[{\"name\":\"a.txt\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Process/Start: minimal connect envelope with exit 0
        gateway.createContext("/process.Process/Start", exchange -> {
            String json = "{\"event\":{\"end\":{\"exited\":true,\"status\":\"exit status 0\",\"exitCode\":0}}}";
            byte[] msg = json.getBytes(StandardCharsets.UTF_8);
            byte[] envelope = new byte[5 + msg.length];
            envelope[0] = 0;
            envelope[1] = (byte) ((msg.length >> 24) & 0xff);
            envelope[2] = (byte) ((msg.length >> 16) & 0xff);
            envelope[3] = (byte) ((msg.length >> 8) & 0xff);
            envelope[4] = (byte) (msg.length & 0xff);
            System.arraycopy(msg, 0, envelope, 5, msg.length);
            exchange.sendResponseHeaders(200, envelope.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(envelope);
            }
        });

        manager.start();
        gateway.start();

        SandboxProperties props = new SandboxProperties();
        props.setProvider("acs");
        props.setApiKey("e2b_test");
        props.setDomain("agent-sandbox.test");
        props.setProtocol("http");
        props.setManagerEndpoint("http://127.0.0.1:" + manager.getAddress().getPort());
        props.setGatewayEndpoint("http://127.0.0.1:" + gateway.getAddress().getPort());
        props.setTemplate("code-interpreter");
        props.setRequestTimeoutSeconds(10);

        client = new AcsE2bSandboxClient();
        inject(client, "properties", props);
        inject(client, "objectMapper", new ObjectMapper());
        client.init();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop(0);
        }
        if (gateway != null) {
            gateway.stop(0);
        }
    }

    @Test
    void createKillAliveExecWrite() throws Exception {
        String id = client.create(BigDecimal.ONE, 1024, 10, null);
        assertEquals("sbx-test-1", id);
        assertEquals(1, creates.get());
        assertTrue(client.isAlive(id));

        client.writeTextFile(id, "/home/user/a.txt", "hello");
        AcsE2bSandboxClient.ExecResult result = client.exec(id, "echo hi");
        assertEquals(0, result.exitCode());

        client.kill(id);
        // after kill, mock still returns running on GET — isAlive uses GET; kill just clears token
        assertFalse(client.isAlive("missing-id"));
    }

    @Test
    void renewPostsTimeout() {
        client.renew("sbx-test-1", 30);
        assertTrue(lastTimeoutBody.get() != null && lastTimeoutBody.get().contains("\"timeout\":1800"));
    }

    @Test
    void createPostsEnvVarsInBody() {
        client.create(BigDecimal.ONE, 1024, 10, Map.of("API_KEY", "secret", "MODE", "prod"));
        String body = lastCreateBody.get();
        assertTrue(body != null && body.contains("\"envVars\""), body);
        assertTrue(body.contains("\"API_KEY\":\"secret\"") || body.contains("\"API_KEY\" : \"secret\""), body);
        assertTrue(body.contains("\"MODE\":\"prod\"") || body.contains("\"MODE\" : \"prod\""), body);
        assertTrue(body.contains("\"templateID\":\"code-interpreter\""), body);
    }

    @Test
    void createOmitsEnvVarsWhenEmpty() {
        client.create(BigDecimal.ONE, 1024, 10, Map.of());
        String body = lastCreateBody.get();
        assertTrue(body != null && !body.contains("envVars"), body);
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
