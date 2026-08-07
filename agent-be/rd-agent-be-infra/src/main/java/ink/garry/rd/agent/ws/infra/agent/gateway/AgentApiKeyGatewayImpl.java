package ink.garry.rd.agent.ws.infra.agent.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentApiKeyGateway;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentApiKeyEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentApiKeyMapper;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AgentApiKeyGateway 实现：业务编码生成 + 仓储 3 方法之外的读能力。
 * <p>
 * 读方法（listByAgent / findByKeyHash / ...）返回的是<b>只读投影</b>领域对象（未装配 transient 依赖）；
 * 需要执行领域动作（touchUsed 等）的调用方应通过 {@code AgentApiKeyFactory.createByNum} 获取完整装配实体，
 * 与既有 {@code AgentVersionReadGateway} 风格一致。
 */
@Component
@RequiredArgsConstructor
public class AgentApiKeyGatewayImpl implements AgentApiKeyGateway {

    /** Agent 对外调用秘钥业务编号前缀 */
    private static final String API_KEY_PREFIX = "AK";

    private final AgentApiKeyMapper agentApiKeyMapper;
    private final BizNumGenerator bizNumGenerator;

    /** 生成 AgentApiKey 业务编号（前缀 AK）。 */
    @Override
    public String generateAgentApiKeyNum() {
        return bizNumGenerator.generate(API_KEY_PREFIX);
    }

    /** 列出某 Agent 下有效秘钥（deleted=0），按创建时间倒序。 */
    @Override
    public List<AgentApiKey> listByAgent(String agentNum) {
        List<AgentApiKeyEntity> rows = agentApiKeyMapper.selectList(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getAgentNum, agentNum)
                .eq(AgentApiKeyEntity::getDeleted, 0)
                .orderByDesc(AgentApiKeyEntity::getCreateTime));
        return rows.stream().map(AgentApiKeyEntity::toDomain).toList();
    }

    /** 统计某 Agent 下有效秘钥数量（用于 ≤50 上限校验）。 */
    @Override
    public long countByAgent(String agentNum) {
        Long cnt = agentApiKeyMapper.selectCount(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getAgentNum, agentNum)
                .eq(AgentApiKeyEntity::getDeleted, 0));
        return cnt == null ? 0L : cnt;
    }

    /** 按 keyHash 等值查询有效秘钥（唯一索引等值查），用于对外调用认证。 */
    @Override
    public AgentApiKey findByKeyHash(String keyHash) {
        AgentApiKeyEntity entity = agentApiKeyMapper.selectOne(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getKeyHash, keyHash)
                .eq(AgentApiKeyEntity::getDeleted, 0));
        return AgentApiKeyEntity.toDomain(entity);
    }

    /** 校验 num 对应秘钥是否存在且归属指定 agentNum（删除 / 查看归属一致性校验）。 */
    @Override
    public boolean existsByNumAndAgent(String num, String agentNum) {
        Long cnt = agentApiKeyMapper.selectCount(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getNum, num)
                .eq(AgentApiKeyEntity::getAgentNum, agentNum)
                .eq(AgentApiKeyEntity::getDeleted, 0));
        return cnt != null && cnt > 0;
    }
}
