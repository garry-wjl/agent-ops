package ink.garry.rd.agent.ws.application.agentrunner.tool;

import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionError;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionLogs;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionResult;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.OutputMessage;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.RunInSessionRequest;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxSession;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 远程沙箱执行工具：把"在隔离容器里执行命令 / 运行 Python / 读写文件"暴露给 Agent。
 * <p>
 * <b>双层复用</b>：容器按 {@code agentNum} 复用(同一 Agent 所有会话共享文件 / 依赖),bash session
 * 按 {@code sessionNum} 复用(各会话 cd / 环境变量独立)。两者均由
 * 路由,故本工具每次 Agent 运行 new 一个绑定 (agentNum, sessionNum) 的实例,不可做成无状态单例 Bean。
 * <p>
 * <b>命令执行走 runInSession</b>:复用同一 bash 上下文,既让 cd / 环境变量在多次命令间延续,又规避
 * 一次性 {@code commands().run()} 在同容器连续调用时的 409 Conflict。
 * <p>
 * <b>线程模型</b>:沙箱调用是阻塞 HTTP,统一 {@code subscribeOn(boundedElastic)},避免阻塞 reactor
 * 事件循环线程。每次操作 try-with-resources 关闭句柄——仅释放 HTTP 客户端,<b>不销毁容器 / session</b>。
 */
@Slf4j
public class SandboxTool {

    /**
     * 沙箱运行环境系统提示词片段：绑定沙箱的 Agent 装配时前置到 sysPrompt，
     * 让模型在对话一开始就知晓沙箱已预装的环境，并遵循「先探测后安装」，避免重复初始化环境
     * （如已装 Node.js 仍强行安装）。镜像固定为 {@code opensandbox/code-interpreter}。
     */
    public static final String SANDBOX_ENV_SYSTEM_PROMPT = """
            ## 沙箱运行环境（务必遵守）
            你的代码沙箱基于固定镜像 opensandbox/code-interpreter，已预装常用运行时与工具：\
            Python 3（python3 / pip）、Node.js（node / npm / npx）、git、curl 及常用 Unix 工具。
            在安装或初始化任何运行时 / 依赖之前，必须先用命令探测其是否已存在（例如 `node -v`、\
            `python3 -V`、`which npm`、`pip show <包名>`）；已存在则直接使用，禁止重复安装或重复\
            初始化环境。同一会话内已安装的内容会在后续命令中持续保留，无需重装。""";

    /** Agent 编号;决定路由到哪个复用容器。 */
    private final String sandboxId;

    /** 会话编号;决定容器内复用哪个 bash session。 */
    private final String sessionNum;

    /** 容器环境变量(仅容器首建时注入)。 */
    private final Map<String, String> env;

    /** 沙箱服务。 */
    private final SandboxRunner sandboxRunner;

    /** 沙箱 TTL 分钟数。 */
    private final Integer ttlMinutes;

    /**
     * @param sandboxId       当前运行的 Agent 编号(容器粒度)
     * @param sessionNum     当前运行的会话编号(bash session 粒度)
     * @param env            容器环境变量(可空)
     */
    public SandboxTool(String sandboxId, String sessionNum, Map<String, String> env, SandboxRunner sandboxRunner, Integer ttlMinutes) {
        this.sandboxId = sandboxId;
        this.sessionNum = sessionNum;
        this.env = env;
        this.sandboxRunner = sandboxRunner;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * 在沙箱中执行一条 shell 命令并返回输出。
     *
     * @param command 要执行的完整 shell 命令,例如 {@code "ls -al /workspace"}
     * @return stdout/stderr 文本 + 退出码;失败返回错误说明
     */
    @Tool(
            name = "execute_command",
            description = "Execute a shell command inside an isolated sandbox and return its "
                    + "stdout/stderr and exit code. The sandbox image (opensandbox/code-interpreter) "
                    + "ALREADY has common runtimes preinstalled: Python 3 (python3/pip), Node.js "
                    + "(node/npm/npx), git, curl and standard Unix tools. State (working directory, "
                    + "env vars, files, installed packages) persists across calls within the same "
                    + "session. IMPORTANT: before installing or initializing any runtime or package, "
                    + "first verify whether it already exists (e.g. `node -v`, `which npm`, "
                    + "`pip show <pkg>`); never reinstall or re-initialize what is already present.")
    public Mono<ToolResultBlock> executeCommand(
            @ToolParam(name = "command", description = "The full shell command to execute")
            String command) {
        return Mono.fromCallable(() -> {
                    try (SandboxSession session = sandboxRunner.obtainSession(sandboxId, sessionNum, env, ttlMinutes)) {
                        Execution execution = session.sandbox().commands()
                                .runInSession(session.execdSessionId(), runRequest(command));
                        return renderExecution(execution);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("execute_command failed, agentNum={}, sessionNum={}, command={}",
                            sandboxId, sessionNum, command, e);
                    return Mono.just(ToolResultBlock.error("命令执行失败: " + e.getMessage()));
                });
    }

    /**
     * 在沙箱中运行一段 Python 代码并返回输出。
     * <p>
     * 代码先写入容器临时文件,再以 {@code python3} 执行,避免命令行转义问题。
     *
     * @param code 要执行的 Python 源码
     * @return stdout/stderr 文本 + 退出码;失败返回错误说明
     */
    @Tool(
            name = "execute_python",
            description = "Run a Python code snippet inside an isolated sandbox and return its "
                    + "stdout/stderr and exit code. The interpreter is python3, already installed in "
                    + "the sandbox image (opensandbox/code-interpreter) together with pip; many common "
                    + "libraries are preinstalled. State persists across calls within the same session. "
                    + "IMPORTANT: before pip-installing a package, first check whether it is already "
                    + "available (e.g. `pip show <pkg>` or importing it); do not reinstall packages "
                    + "that already exist.")
    public Mono<ToolResultBlock> executePython(
            @ToolParam(name = "code", description = "The Python source code to execute")
            String code) {
        return Mono.fromCallable(() -> {
                    try (SandboxSession session = sandboxRunner.obtainSession(sandboxId, sessionNum, env, ttlMinutes)) {
                        String scriptPath = "/tmp/agent_" + UUID.randomUUID().toString().replace("-", "") + ".py";
                        session.sandbox().files().writeFile(scriptPath, code);
                        Execution execution = session.sandbox().commands()
                                .runInSession(session.execdSessionId(), runRequest("python3 " + scriptPath));
                        return renderExecution(execution);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("execute_python failed, agentNum={}, sessionNum={}", sandboxId, sessionNum, e);
                    return Mono.just(ToolResultBlock.error("Python 执行失败: " + e.getMessage()));
                });
    }

    /**
     * 读取沙箱中指定文件的文本内容。
     *
     * @param filePath 容器内文件绝对路径
     * @return 文件文本内容;失败返回错误说明
     */
    @Tool(
            name = "read_sandbox_file",
            description = "Read the text content of a file inside the sandbox by absolute path.")
    public Mono<ToolResultBlock> readFile(
            @ToolParam(name = "file_path", description = "Absolute path of the file inside the sandbox")
            String filePath) {
        return Mono.fromCallable(() -> {
                    try (SandboxSession session = sandboxRunner.obtainSession(sandboxId, sessionNum, env, ttlMinutes)) {
                        String content = session.sandbox().files().readFile(filePath);
                        return ToolResultBlock.text(content);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("read_sandbox_file failed, sandboxId={}, sessionNum={}, path={}",
                            sandboxId, sessionNum, filePath, e);
                    return Mono.just(ToolResultBlock.error("读取文件失败: " + e.getMessage()));
                });
    }

    /**
     * 向沙箱中指定路径写入文本文件(不存在则创建,存在则覆盖)。
     *
     * @param filePath 容器内文件绝对路径
     * @param content  要写入的文本内容
     * @return 写入成功提示;失败返回错误说明
     */
    @Tool(
            name = "write_sandbox_file",
            description = "Write text content to a file inside the sandbox at the given absolute "
                    + "path (creates or overwrites). Use this to prepare scripts or data before "
                    + "executing commands.")
    public Mono<ToolResultBlock> writeFile(
            @ToolParam(name = "file_path", description = "Absolute path of the file inside the sandbox")
            String filePath,
            @ToolParam(name = "content", description = "The text content to write")
            String content) {
        return Mono.fromCallable(() -> {
                    try (SandboxSession session = sandboxRunner.obtainSession(sandboxId, sessionNum, env, ttlMinutes)) {
                        session.sandbox().files().writeFile(filePath, content);
                        return ToolResultBlock.text("File written: " + filePath);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("write_sandbox_file failed, agentNum={}, sessionNum={}, path={}",
                            sandboxId, sessionNum, filePath, e);
                    return Mono.just(ToolResultBlock.error("写入文件失败: " + e.getMessage()));
                });
    }

    /** 构造 runInSession 请求(仅 command;Kotlin 便捷重载含 inline-class 参数 Java 不可直接调)。 */
    private RunInSessionRequest runRequest(String command) {
        return RunInSessionRequest.builder()
                .command(command)
                .build();
    }

    /**
     * 把沙箱执行结果渲染成 Agent 可读的文本块(stdout + stderr + 富结果 + error + exitCode)。
     * <p>
     * <b>关键</b>:{@code runInSession} 跑 shell 命令 / {@code python3 脚本} 时,命令打印的内容通过
     * SSE {@code stdout}/{@code stderr} 事件回来,落在 {@link Execution#getLogs()} 里;而
     * {@link Execution#getResult()} 只装 Jupyter 式"最后一个表达式的富返回值",对 shell 执行几乎恒为空。
     * 故必须优先读 {@code logs.stdout/stderr},否则 Agent 只能拿到一行 exitCode,拿不到真正的输出。
     */
    private ToolResultBlock renderExecution(Execution execution) {
        StringBuilder sb = new StringBuilder();

        ExecutionLogs logs = execution.getLogs();
        if (logs != null) {
            String stdout = joinMessages(logs.getStdout());
            if (!stdout.isEmpty()) {
                sb.append(stdout);
            }
            String stderr = joinMessages(logs.getStderr());
            if (!stderr.isEmpty()) {
                appendSection(sb, "stderr", stderr);
            }
        }

        // 富结果(Jupyter kernel 返回值 / 展示数据):shell 执行通常为空,有则补充在后。
        List<ExecutionResult> results = execution.getResult();
        if (results != null && !results.isEmpty()) {
            String richResult = results.stream()
                    .map(ExecutionResult::getText)
                    .collect(Collectors.joining());
            if (!richResult.isEmpty()) {
                appendSection(sb, "result", richResult);
            }
        }

        ExecutionError error = execution.getError();
        if (error != null) {
            sb.append("\n[error] ").append(error.getName()).append(": ").append(error.getValue());
        }
        Integer exitCode = execution.getExitCode();
        sb.append("\n[exitCode] ").append(exitCode == null ? "unknown" : exitCode);
        return ToolResultBlock.text(sb.toString());
    }

    /** 把一组输出消息按顺序拼成纯文本;入参为空返回空串。 */
    private String joinMessages(List<OutputMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(OutputMessage::getText)
                .collect(Collectors.joining());
    }

    /** 追加一个带标签的输出段落(段前留空行分隔,已有内容时才加空行)。 */
    private void appendSection(StringBuilder sb, String label, String content) {
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append("[").append(label).append("] ").append(content);
    }
}
