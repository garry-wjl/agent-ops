package ink.garry.rd.agent.ws.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池：SSE 订阅、领域事件处理、Agent invoke 等场景。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "agentInvokeExecutor")
    public Executor agentInvokeExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(8);
        exec.setMaxPoolSize(64);
        exec.setQueueCapacity(256);
        exec.setThreadNamePrefix("agent-invoke-");
        exec.setKeepAliveSeconds(60);
        exec.initialize();
        return exec;
    }

    /**
     * 评测异步线程池：core=4 / max=16 / queue=200，对齐评测技术方案 §11.1。
     * <p>
     * 用于 EvalWorker.runAuto 异步执行评测；CallerRuns 兜底防止 OOM。
     */
    @Bean(name = "evaluationExecutor")
    public Executor evaluationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("eval-worker-");
        exec.setKeepAliveSeconds(60);
        exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    /**
     * 沙箱供给异步线程池：core=2 / max=8 / queue=64，对齐沙箱管理技术方案 §6.2.3。
     * <p>
     * 承载 {@code SandboxRunner} 的 {@code @EventListener + @Async("sandboxProvisionExecutor")}：
     * 监听 {@code SANDBOX_SUBMITTED} 异步建容器 + 健康检查，监听 {@code SANDBOX_OFFLINED} /
     * {@code SANDBOX_DELETED} 异步 kill 容器，从而「提交动作立即返回、容器供给异步进行」。
     * 容器创建 / 销毁多为外部 OpenSandbox IO 调用，池不必大；CallerRuns 兜底防止队列打满丢任务。
     */
    @Bean(name = "sandboxProvisionExecutor")
    public Executor sandboxProvisionExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(64);
        exec.setThreadNamePrefix("sandbox-provision-");
        exec.setKeepAliveSeconds(60);
        exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
