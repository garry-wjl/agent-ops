package ink.garry.rd.agent.ws.infra.agent.factory;

import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentType;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AgentFactory 实现：装配 Agent 的全部基础设施依赖（Repository / Gateway / Publisher）。
 * <p>
 * v3.x 反转 §4.7：FactoryImpl 重新承担 {@link DomainEventPublisher} 注入，
 * application 层不再写 wire helper；工厂返回即「完整装配的聚合」。
 */
@Component
@RequiredArgsConstructor
public class AgentFactoryImpl implements AgentFactory {

    /** A2A Agent 平台侧负责人固定值（A2A 由 Nacos 自管，平台无负责人概念） */
    private static final String A2A_OWNER = "system";

    /** 默认工作空间编号；无 workspace 上下文时（如 A2A 发现）回退于此（infra 不依赖 client，就地定义） */
    private static final String DEFAULT_WORKSPACE_NUM = "WS-DEFAULT";

    private final AgentRepository agentRepository;
    private final AgentGateway agentGateway;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public Agent createConfigAgent(String name, String description, AgentType agentType, String ownerUserId,
                                   java.util.List<String> tags) {
        Agent agent = new Agent();
        agent.setName(name);
        agent.setDescription(description);
        agent.setTags(tags);
        agent.setCreationMode(CreationMode.CONFIG);
        agent.setAgentType(agentType == null ? AgentType.NORMAL : agentType);
        agent.setOwnerUserId(ownerUserId);
        agent.setStatus(AgentStatus.DRAFT_ONLY);
        agent.setSandbox(false);
        // 从当前请求的工作空间上下文注入归属空间；缺上下文时回退默认空间
        agent.setWorkspaceNum(resolveWorkspaceNum());
        return wire(agent);
    }

    @Override
    public Agent createA2aAgent(A2aSourceInfo source, String name, String description, AgentStatus status) {
        Agent agent = new Agent();
        agent.setName(name);
        agent.setDescription(description);
        agent.setCreationMode(CreationMode.A2A);
        agent.setAgentType(AgentType.NORMAL);
        agent.setOwnerUserId(A2A_OWNER);
        agent.setStatus(status);
        agent.setSandbox(false);
        agent.setA2aSource(source);
        agent.setNacosServiceKey(source == null ? null : source.resolveServiceKey());
        // A2A 由 Nacos 发现，无用户工作空间上下文，统一归默认空间
        agent.setWorkspaceNum(DEFAULT_WORKSPACE_NUM);
        return wire(agent);
    }

    @Override
    public Agent createByNum(String num) {
        return wire(agentRepository.findByNum(num));
    }

    private Agent wire(Agent agent) {
        if (agent == null) {
            return null;
        }
        agent.setAgentRepository(agentRepository);
        agent.setAgentGateway(agentGateway);
        agent.setDomainEventPublisher(domainEventPublisher);
        return agent;
    }

    /**
     * 解析当前请求的工作空间编号；无 workspace 上下文（如非空间路径创建）时回退默认空间。
     *
     * @return 工作空间业务编号
     */
    private String resolveWorkspaceNum() {
        String ws = WorkspaceContextHolder.currentWorkspaceNum();
        return (ws == null || ws.isBlank()) ? DEFAULT_WORKSPACE_NUM : ws;
    }
}
