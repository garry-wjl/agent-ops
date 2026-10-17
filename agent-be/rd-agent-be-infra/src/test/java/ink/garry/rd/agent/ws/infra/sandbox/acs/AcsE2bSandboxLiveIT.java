package ink.garry.rd.agent.ws.infra.sandbox.acs;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地联调：连真实 ACS Agent Sandbox（需 port-forward + 环境变量）。
 * <pre>
 *   kubectl -n sandbox-system port-forward svc/sandbox-manager 18080:8080
 *   kubectl -n sandbox-system port-forward svc/sandbox-gateway 17788:7788
 *   SANDBOX_LIVE_IT=true \\
 *   SANDBOX_API_KEY=$(cat ~/.kube/acs-agent-sandbox-apikey.txt) \\
 *   mvn -pl rd-agent-be-infra -Dtest=AcsE2bSandboxLiveIT -Dsurefire.failIfNoSpecifiedTests=false test
 * </pre>
 */
class AcsE2bSandboxLiveIT {

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void createWriteExecPersistKill() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SANDBOX_LIVE_IT")),
                "set SANDBOX_LIVE_IT=true to run live ACS IT");

        String apiKey = firstNonBlank(System.getenv("SANDBOX_API_KEY"),
                readFileQuiet(System.getProperty("user.home") + "/.kube/acs-agent-sandbox-apikey.txt"));
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "SANDBOX_API_KEY required");

        String domain = firstNonBlank(System.getenv("SANDBOX_DOMAIN"), "agent-sandbox.yldfuture.com");
        String manager = firstNonBlank(System.getenv("SANDBOX_MANAGER_ENDPOINT"), "http://127.0.0.1:18080");
        String gateway = firstNonBlank(System.getenv("SANDBOX_GATEWAY_ENDPOINT"), "http://127.0.0.1:17788");

        SandboxProperties props = new SandboxProperties();
        props.setProvider("acs");
        props.setApiKey(apiKey.trim());
        props.setDomain(domain);
        props.setProtocol("http");
        props.setManagerEndpoint(manager);
        props.setGatewayEndpoint(gateway);
        props.setTemplate("code-interpreter");
        props.setTtlMinutes(10);
        props.setRequestTimeoutSeconds(60);

        AcsE2bSandboxClient client = new AcsE2bSandboxClient();
        inject(client, "properties", props);
        inject(client, "objectMapper", new ObjectMapper());
        client.init();

        String marker = "live-it-" + UUID.randomUUID();
        String path = "/home/user/agentops-live-it.txt";
        String id = null;
        try {
            id = client.create(BigDecimal.valueOf(0.5), 1024, 10, null);
            assertTrue(id != null && !id.isBlank(), "sandboxId");
            assertTrue(client.isAlive(id), "alive after create");

            client.writeTextFile(id, path, marker);
            AcsE2bSandboxClient.ExecResult cat = client.exec(id, "cat " + path);
            assertEquals(0, cat.exitCode(), "cat exit: " + cat.stderr());
            assertTrue(cat.stdout().contains(marker), "stdout=" + cat.stdout());

            AcsE2bSandboxClient.ExecResult echo = client.exec(id,
                    "echo MORE >> " + path + " && cat " + path);
            assertEquals(0, echo.exitCode(), echo.stderr());
            assertTrue(echo.stdout().contains(marker), echo.stdout());
            assertTrue(echo.stdout().contains("MORE"), echo.stdout());

            // 再次读，确认会话内不丢
            AcsE2bSandboxClient.ExecResult again = client.exec(id, "cat " + path);
            assertEquals(0, again.exitCode());
            assertTrue(again.stdout().contains(marker) && again.stdout().contains("MORE"),
                    again.stdout());
        } finally {
            if (id != null) {
                client.kill(id);
            }
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String readFileQuiet(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            return null;
        }
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
