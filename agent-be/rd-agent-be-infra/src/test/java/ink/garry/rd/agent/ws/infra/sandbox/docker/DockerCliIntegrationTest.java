package ink.garry.rd.agent.ws.infra.sandbox.docker;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker CLI 冒烟（本机有 Docker 时跑；无则 skip）。
 */
class DockerCliIntegrationTest {

    @Test
    void createExecKill() {
        DockerCli cli = new DockerCli();
        DockerCli.ExecResult version = cli.run(List.of("docker", "version", "--format", "{{.Server.Version}}"),
                null, Duration.ofSeconds(15));
        Assumptions.assumeTrue(version.exitCode() == 0, "Docker daemon not available");

        String name = "agentops-test-" + System.currentTimeMillis();
        String id = null;
        try {
            id = cli.runDetached(name, "ubuntu:22.04", "0.5", 256, Map.of("agentops.test", "1"));
            assertTrue(cli.isRunning(id));
            DockerCli.ExecResult echo = cli.exec(id, null, "/workspace", "echo hello-docker");
            assertEquals(0, echo.exitCode());
            assertTrue(echo.stdout().contains("hello-docker"));
            cli.writeTextFile(id, "/workspace/t.txt", "abc");
            DockerCli.ExecResult cat = cli.exec(id, null, null, "cat /workspace/t.txt");
            assertEquals(0, cat.exitCode());
            assertEquals("abc", cat.stdout());
        } finally {
            if (id != null) {
                cli.removeForce(id);
                assertFalse(cli.isRunning(id));
            }
        }
    }

    @Test
    void shellSingleQuoteEscapes() {
        assertEquals("'a'\\''b'", DockerCli.shellSingleQuote("a'b"));
    }
}
