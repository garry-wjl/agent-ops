package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import io.agentscope.core.agent.Event;
import lombok.Getter;

/**
 * 调试台 / Open SSE 统一帧：AgentScope {@link Event} 或 {@link SandboxStatusEvent}。
 */
@Getter
public final class AgentInvokeFrame {

    private final String sseEventName;
    private final Event agentEvent;
    private final SandboxStatusEvent sandboxStatus;

    private AgentInvokeFrame(String sseEventName, Event agentEvent, SandboxStatusEvent sandboxStatus) {
        this.sseEventName = sseEventName;
        this.agentEvent = agentEvent;
        this.sandboxStatus = sandboxStatus;
    }

    /**
     * @param event AgentScope 事件
     * @return 默认 data 帧（无 SSE event 名）
     */
    public static AgentInvokeFrame agent(Event event) {
        return new AgentInvokeFrame(null, event, null);
    }

    /**
     * @param status 沙箱状态
     * @return {@link SandboxStatusEvent#SSE_EVENT} 帧
     */
    public static AgentInvokeFrame sandboxStatus(SandboxStatusEvent status) {
        return new AgentInvokeFrame(SandboxStatusEvent.SSE_EVENT, null, status);
    }

    /** @return 是否为 Agent 事件帧 */
    public boolean isAgentEvent() {
        return agentEvent != null;
    }

    /** @return 是否为沙箱状态帧 */
    public boolean isSandboxStatus() {
        return sandboxStatus != null;
    }
}
