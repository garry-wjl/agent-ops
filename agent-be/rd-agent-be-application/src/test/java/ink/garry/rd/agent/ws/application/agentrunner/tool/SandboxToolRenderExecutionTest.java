package ink.garry.rd.agent.ws.application.agentrunner.tool;

import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionError;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionLogs;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionResult;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.OutputMessage;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SandboxTool#renderExecution} 渲染逻辑单元测试。
 * <p>
 * 覆盖核心回归:{@code runInSession} 跑 shell / python3 时,命令输出通过 {@code stdout} 事件落在
 * {@link Execution#getLogs()},而非 {@link Execution#getResult()}。渲染必须读到 logs.stdout/stderr,
 * 否则 Agent 只能拿到一行 exitCode(历史 Bug:执行命令 / Python 拿不到返回值)。
 */
class SandboxToolRenderExecutionTest {

    /** 反射调用私有 renderExecution;工具实例的依赖对渲染无影响,全部传 null。 */
    private String render(Execution execution) throws Exception {
        SandboxTool tool = new SandboxTool(null, null, null, null, null);
        Method m = SandboxTool.class.getDeclaredMethod("renderExecution", Execution.class);
        m.setAccessible(true);
        ToolResultBlock block = (ToolResultBlock) m.invoke(tool, execution);
        return ((TextBlock) block.getOutput().get(0)).getText();
    }

    private OutputMessage msg(String text, boolean isError) {
        return new OutputMessage(text, 0L, isError);
    }

    private ExecutionResult result(String text) {
        return new ExecutionResult(text, 0L, Map.of());
    }

    private Execution execution(List<OutputMessage> stdout,
                                List<OutputMessage> stderr,
                                List<ExecutionResult> results,
                                ExecutionError error,
                                Integer exitCode) {
        ExecutionLogs logs = new ExecutionLogs(stdout, stderr);
        return new Execution(null, null, results, error, null, exitCode, logs);
    }

    @Test
    void shouldRenderStdoutFromLogs() throws Exception {
        // 回归核心:shell 命令输出来自 logs.stdout,result 为空
        Execution execution = execution(
                List.of(msg("hello world\n", false)),
                List.of(),
                List.of(),
                null,
                0);

        String rendered = render(execution);

        assertTrue(rendered.contains("hello world"), "必须包含 logs.stdout 的输出");
        assertTrue(rendered.contains("[exitCode] 0"));
    }

    @Test
    void shouldRenderStderrFromLogs() throws Exception {
        Execution execution = execution(
                List.of(),
                List.of(msg("command not found", true)),
                List.of(),
                null,
                127);

        String rendered = render(execution);

        assertTrue(rendered.contains("[stderr] command not found"));
        assertTrue(rendered.contains("[exitCode] 127"));
    }

    @Test
    void shouldRenderBothStdoutAndStderr() throws Exception {
        Execution execution = execution(
                List.of(msg("out line", false)),
                List.of(msg("err line", true)),
                List.of(),
                null,
                0);

        String rendered = render(execution);

        assertTrue(rendered.contains("out line"));
        assertTrue(rendered.contains("[stderr] err line"));
    }

    @Test
    void shouldAppendRichResultWhenPresent() throws Exception {
        // Jupyter 式富返回值:作为补充段落追加,不覆盖 stdout
        Execution execution = execution(
                List.of(msg("printed", false)),
                List.of(),
                List.of(result("42")),
                null,
                0);

        String rendered = render(execution);

        assertTrue(rendered.contains("printed"));
        assertTrue(rendered.contains("[result] 42"));
    }

    @Test
    void shouldRenderErrorSection() throws Exception {
        Execution execution = execution(
                List.of(),
                List.of(),
                List.of(),
                new ExecutionError("ValueError", "bad input", 0L, List.of()),
                1);

        String rendered = render(execution);

        assertTrue(rendered.contains("[error] ValueError: bad input"));
        assertTrue(rendered.contains("[exitCode] 1"));
    }

    @Test
    void shouldRenderUnknownExitCodeWhenNull() throws Exception {
        Execution execution = execution(List.of(), List.of(), List.of(), null, null);

        String rendered = render(execution);

        assertTrue(rendered.contains("[exitCode] unknown"));
    }

    @Test
    void shouldRenderWhenNoOutputProduced() throws Exception {
        // 命令无任何输出:logs/result 均空,仍须输出 exitCode 而不 NPE
        Execution execution = new Execution();
        execution.setExitCode(0);

        String rendered = render(execution);

        assertFalse(rendered.isBlank());
        assertTrue(rendered.contains("[exitCode] 0"));
    }
}
