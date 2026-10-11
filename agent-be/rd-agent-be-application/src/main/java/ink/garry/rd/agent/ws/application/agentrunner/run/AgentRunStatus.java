package ink.garry.rd.agent.ws.application.agentrunner.run;

/**
 * Agent 调用 run 状态（与 HTTP 订阅解耦）。
 */
public enum AgentRunStatus {
    RUNNING,
    DONE,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == DONE || this == FAILED || this == CANCELLED;
    }
}
