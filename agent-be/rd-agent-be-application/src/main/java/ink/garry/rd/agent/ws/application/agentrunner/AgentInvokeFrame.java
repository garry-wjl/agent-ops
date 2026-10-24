package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.client.agentrunner.UserInputRequiredEvent;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.event.AgentEvent;
import lombok.Getter;

/**
 * 调试台 / Open SSE 统一帧。
 * <p>
 * 业务载荷二选一：粗粒度 {@link Event}（{@code /invoke}）或细粒度 {@link AgentEvent}
 * （{@code /stream}）；控制帧（沙箱状态 / HITL）与协议无关。
 */
@Getter
public final class AgentInvokeFrame {

    private final String sseEventName;
    private final Event agentEvent;
    private final AgentEvent scopeAgentEvent;
    private final SandboxStatusEvent sandboxStatus;
    private final UserInputRequiredEvent userInputRequired;
    private final Object userInputStatusPayload;

    private AgentInvokeFrame(String sseEventName, Event agentEvent, AgentEvent scopeAgentEvent,
                             SandboxStatusEvent sandboxStatus,
                             UserInputRequiredEvent userInputRequired, Object userInputStatusPayload) {
        this.sseEventName = sseEventName;
        this.agentEvent = agentEvent;
        this.scopeAgentEvent = scopeAgentEvent;
        this.sandboxStatus = sandboxStatus;
        this.userInputRequired = userInputRequired;
        this.userInputStatusPayload = userInputStatusPayload;
    }

    public static AgentInvokeFrame agent(Event event) {
        return new AgentInvokeFrame(null, event, null, null, null, null);
    }

    /** AgentScope 2.x 细粒度 {@link AgentEvent} 业务帧（{@code /command/stream}）。 */
    public static AgentInvokeFrame scopeAgent(AgentEvent event) {
        return new AgentInvokeFrame(null, null, event, null, null, null);
    }

    public static AgentInvokeFrame sandboxStatus(SandboxStatusEvent status) {
        return new AgentInvokeFrame(SandboxStatusEvent.SSE_EVENT, null, null, status, null, null);
    }

    public static AgentInvokeFrame userInputRequired(UserInputRequiredEvent event) {
        return new AgentInvokeFrame(UserInputRequiredEvent.SSE_EVENT, null, null, null, event, null);
    }

    public static AgentInvokeFrame userInputAccepted(Object payload) {
        return new AgentInvokeFrame(UserInputRequiredEvent.SSE_ACCEPTED, null, null, null, null, payload);
    }

    public static AgentInvokeFrame userInputRejected(Object payload) {
        return new AgentInvokeFrame(UserInputRequiredEvent.SSE_REJECTED, null, null, null, null, payload);
    }

    public boolean isAgentEvent() {
        return agentEvent != null;
    }

    public boolean isScopeAgentEvent() {
        return scopeAgentEvent != null;
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
