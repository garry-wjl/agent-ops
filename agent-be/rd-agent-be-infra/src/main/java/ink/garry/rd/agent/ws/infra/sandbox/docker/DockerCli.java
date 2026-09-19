package ink.garry.rd.agent.ws.infra.sandbox.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本机 Docker CLI 封装（ProcessBuilder，不引 docker-java）。
 * <p>
 * 供本地 {@code sandbox.mock=true} 时创建/探活/销毁容器与 exec / 写文件。
 */
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "true")
public class DockerCli {

    private static final Logger log = LoggerFactory.getLogger(DockerCli.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    /**
     * 后台常驻容器：{@code sleep} 循环，供反复 {@code docker exec}。
     *
     * @param name      容器名
     * @param image     镜像
     * @param cpu       CPU 核数（可空）
     * @param memoryMb  内存 MB
     * @param labels    额外 label（可空）
     * @return 容器 ID（短/长均可）
     */
    public String runDetached(String name, String image, String cpu, int memoryMb, Map<String, String> labels) {
        return runDetached(name, image, cpu, memoryMb, labels, null);
    }

    /**
     * @param hostWorkspace 宿主机目录，非空时绑定到容器 {@code /workspace}；本地模拟会话 OSS 隔离
     * @return 容器 ID
     */
    public String runDetached(String name, String image, String cpu, int memoryMb,
                               Map<String, String> labels, String hostWorkspace) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.add("--name");
        cmd.add(name);
        if (cpu != null && !cpu.isBlank()) {
            cmd.add("--cpus");
            cmd.add(cpu);
        }
        if (memoryMb > 0) {
            cmd.add("--memory");
            cmd.add(memoryMb + "m");
        }
        // 本地默认隔离外网，贴近 Harness DockerSandbox
        cmd.add("--network");
        cmd.add("none");
        if (hostWorkspace != null && !hostWorkspace.isBlank()) {
            cmd.add("-v");
            cmd.add(hostWorkspace + ":/workspace");
        }
        if (labels != null) {
            for (Map.Entry<String, String> e : labels.entrySet()) {
                cmd.add("--label");
                cmd.add(e.getKey() + "=" + e.getValue());
            }
        }
        cmd.add(image);
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("mkdir -p /workspace && while true; do sleep 3600; done");
        ExecResult r = run(cmd, null, DEFAULT_TIMEOUT);
        if (r.exitCode() != 0) {
            throw new IllegalStateException("docker run failed: " + r.stderrOrStdout());
        }
        String id = r.stdout().trim();
        if (id.isEmpty()) {
            throw new IllegalStateException("docker run returned empty container id");
        }
        log.info("[docker-cli] run name={} id={} image={}", name, shortId(id), image);
        return id;
    }

    /**
     * 强制删除容器。
     *
     * @param containerIdOrName 容器 id 或名
     */
    public void removeForce(String containerIdOrName) {
        if (containerIdOrName == null || containerIdOrName.isBlank()) {
            return;
        }
        ExecResult r = run(List.of("docker", "rm", "-f", containerIdOrName), null, Duration.ofSeconds(60));
        if (r.exitCode() != 0) {
            log.warn("[docker-cli] rm -f failed id={} err={}", shortId(containerIdOrName), r.stderrOrStdout());
        } else {
            log.info("[docker-cli] rm -f id={}", shortId(containerIdOrName));
        }
    }

    /**
     * 容器是否在跑。
     *
     * @param containerIdOrName 容器 id 或名
     * @return running
     */
    public boolean isRunning(String containerIdOrName) {
        if (containerIdOrName == null || containerIdOrName.isBlank()) {
            return false;
        }
        ExecResult r = run(
                List.of("docker", "inspect", "-f", "{{.State.Running}}", containerIdOrName),
                null,
                Duration.ofSeconds(30));
        if (r.exitCode() != 0) {
            return false;
        }
        return "true".equalsIgnoreCase(r.stdout().trim());
    }

    /**
     * 在容器内执行 shell 命令。
     *
     * @param containerId 容器
     * @param env         环境变量（可空）
     * @param workdir     工作目录（可空）
     * @param command     shell 命令
     * @return 退出码与输出
     */
    public ExecResult exec(String containerId, Map<String, String> env, String workdir, String command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        if (env != null) {
            for (Map.Entry<String, String> e : env.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                cmd.add("-e");
                cmd.add(e.getKey() + "=" + (e.getValue() != null ? e.getValue() : ""));
            }
        }
        if (workdir != null && !workdir.isBlank()) {
            cmd.add("-w");
            cmd.add(workdir);
        }
        cmd.add(containerId);
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(command);
        return run(cmd, null, DEFAULT_TIMEOUT);
    }

    /**
     * 向容器写入 UTF-8 文本文件（覆盖）。
     *
     * @param containerId  容器
     * @param absolutePath 绝对路径
     * @param utf8Content  内容
     */
    public void writeTextFile(String containerId, String absolutePath, String utf8Content) {
        byte[] bytes = utf8Content != null ? utf8Content.getBytes(StandardCharsets.UTF_8) : new byte[0];
        // 先确保父目录存在
        String parent = parentDir(absolutePath);
        if (parent != null) {
            ExecResult mkdir = exec(containerId, null, null, "mkdir -p " + shellSingleQuote(parent));
            if (mkdir.exitCode() != 0) {
                throw new IllegalStateException("docker mkdir failed: " + mkdir.stderrOrStdout());
            }
        }
        List<String> cmd = List.of(
                "docker", "exec", "-i", containerId, "sh", "-c",
                "cat > " + shellSingleQuote(absolutePath));
        ExecResult r = run(cmd, bytes, DEFAULT_TIMEOUT);
        if (r.exitCode() != 0) {
            throw new IllegalStateException("docker writeFile failed: " + r.stderrOrStdout());
        }
    }

    /**
     * 执行外部进程。
     *
     * @param command     命令行
     * @param stdinBytes  标准输入（可空）
     * @param timeout     超时
     * @return 结果
     */
    public ExecResult run(List<String> command, byte[] stdinBytes, Duration timeout) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            if (stdinBytes != null) {
                try (OutputStream os = p.getOutputStream()) {
                    os.write(stdinBytes);
                    os.flush();
                }
            } else {
                p.getOutputStream().close();
            }
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread tOut = gobble(p.getInputStream(), stdout);
            Thread tErr = gobble(p.getErrorStream(), stderr);
            tOut.start();
            tErr.start();
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IllegalStateException("docker command timeout: " + String.join(" ", command));
            }
            tOut.join(5_000);
            tErr.join(5_000);
            return new ExecResult(
                    p.exitValue(),
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("docker command interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("docker command failed to start (is Docker running?): " + e.getMessage(), e);
        }
    }

    private static Thread gobble(InputStream in, ByteArrayOutputStream out) {
        return new Thread(() -> {
            try (in) {
                in.transferTo(out);
            } catch (IOException ignored) {
                // process ended
            }
        }, "docker-cli-stream");
    }

    static String shellSingleQuote(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String parentDir(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }
        int idx = absolutePath.lastIndexOf('/');
        if (idx <= 0) {
            return null;
        }
        return absolutePath.substring(0, idx);
    }

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 12 ? id : id.substring(0, 12);
    }

    /**
     * CLI 执行结果。
     *
     * @param exitCode 退出码
     * @param stdout   标准输出
     * @param stderr   标准错误
     */
    public record ExecResult(int exitCode, String stdout, String stderr) {
        /**
         * 优先 stderr，否则 stdout。
         *
         * @return 文本
         */
        public String stderrOrStdout() {
            if (stderr != null && !stderr.isBlank()) {
                return stderr.trim();
            }
            return stdout != null ? stdout.trim() : "";
        }
    }
}
