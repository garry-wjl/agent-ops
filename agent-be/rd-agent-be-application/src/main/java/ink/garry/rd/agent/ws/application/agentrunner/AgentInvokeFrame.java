package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.client.agentrunner.UserInputRequiredEvent;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import io.agentscope.core.agent.Event;
import lombok.Getter;

/**
 * 调试台 / Open SSE 统一帧：AgentScope {@link Event}、沙箱状态或用户表单 HITL。
 */
@Getter
public final class AgentInvokeFrame {

    private final String sseEventName;
    private final Event agentEvent;
    private final SandboxStatusEvent sandboxStatus;
    private final UserInputRequiredEvent userInputRequired;
    private final Object userInputStatusPayload;

    private AgentInvokeFrame(String sseEventName, Event agentEvent, SandboxStatusEvent sandboxStatus,
                             UserInputRequiredEvent userInputRequired, Object userInputStatusPayload) {
        this.sseEventName = sseEventName;
        this.agentEvent = agentEvent;
        this.sandboxStatus = sandboxStatus;
        this.userInputRequired = userInputRequired;
        this.userInputStatusPayload = userInputStatusPayload;
    }

    public static AgentInvokeFrame agent(Event event) {
        return new AgentInvokeFrame(null, event, null, null, null);
    }

    public static AgentInvokeFrame sandboxStatus(SandboxStatusEvent status) {
        return new AgentInvokeFrame(SandboxStatusEvent.SSE_EVENT, null, status, null, null);
    }

    public static AgentInvokeFrame userInputRequired(UserInputRequiredEvent event) {
        return new AgentInvokeFrame(UserInputRequiredEvent.SSE_EVENT, null, null, event, null);
    }

    public static AgentInvokeFrame userInputAccepted(Object payload) {
        return new AgentInvokeFrame(UserInputRequiredEvent.SSE_ACCEPTED, null, null, null, payload);
    }

    public static AgentInvokeFrame userInputRejected(Object payload) {
        return new AgentInvokeFrame(UserInputRequiredEvent.SSE_REJECTED, null, null, null, payload);
    }

    public boolean isAgentEvent() {
        return agentEvent != null;
    }

    public boolean isSandboxStatus() {
        return sandboxStatus != null;
    }

    public boolean isUserInputRequired() {
        return userInputRequired != null;
    }

    public boolean isUserInputStatus() {
        return userInputStatusPayload != null
                && (UserInputRequiredEvent.SSE_ACCEPTED.equals(sseEventName)
                || UserInputRequiredEvent.SSE_REJECTED.equals(sseEventName));
    }
}
