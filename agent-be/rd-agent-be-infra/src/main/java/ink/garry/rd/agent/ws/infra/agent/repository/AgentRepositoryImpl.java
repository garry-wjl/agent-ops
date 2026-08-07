package ink.garry.rd.agent.ws.infra.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Agent 仓储实现：注入 AgentMapper 与 DomainEventPublisher，负责 Entity ↔ Domain 转换
 * 并确保反序列化后的 Agent 聚合根持有完整的基础设施依赖。
 */
@Repository
@RequiredArgsConstructor
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper agentMapper;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public void save(Agent aggregate) {
        AgentEntity entity = AgentEntity.fromDomain(aggregate);
        if (entity.getId() == null) {
            agentMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            agentMapper.updateById(entity);
        }
    }

    @Override
    public Agent findByNum(String num) {
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getNum, num)
                .eq(AgentEntity::getDeleted, 0));
        Agent agent = AgentEntity.toDomain(entity);
        if (agent != null) {
            agent.setAgentRepository(this);
            agent.setDomainEventPublisher(domainEventPublisher);
        }
        return agent;
    }

    /**
     * 按业务编号逻辑删除 Agent。
     * <p>
     * logic-delete: deleted 配置下，{@code update(entity, wrapper)} 会忽略 deleted 字段导致 SET 子句为空。
     * 用 {@code delete(wrapper)} 让 MyBatis-Plus 自动生成 {@code UPDATE ... SET deleted=1 WHERE ...}。
     * </p>
     */
    @Override
    public void deleteByNum(String num) {
        agentMapper.delete(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getNum, num));
    }
}
