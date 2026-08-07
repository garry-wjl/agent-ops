package ink.garry.rd.agent.ws.application.sandbox.runner;

import com.alibaba.opensandbox.sandbox.Sandbox;

/**
 * 一次工具调用所需的沙箱执行上下文：Agent 级复用的容器句柄 + 会话级 execd bash session。
 * <p>
 * {@link #sandbox} 句柄实现 {@link AutoCloseable}(关闭仅释放 HTTP 客户端,不销毁容器),
 * 调用方应 try-with-resources 使用。命令执行须走 {@code sandbox.commands().runInSession(execdSessionId, ...)}
 * 以复用同一 bash 上下文(cd / 环境变量延续),避免一次性 {@code run()} 的 409 冲突。
 *
 * @param sandbox        Agent 级复用容器的句柄
 * @param execdSessionId 会话级 bash session id(用于 runInSession)
 */
public record SandboxSession(Sandbox sandbox, String execdSessionId) implements AutoCloseable {

    @Override
    public void close() {
        sandbox.close();
    }
}
