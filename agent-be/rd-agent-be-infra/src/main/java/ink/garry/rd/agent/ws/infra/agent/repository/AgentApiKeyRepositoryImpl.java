package ink.garry.rd.agent.ws.infra.agent.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentApiKeyRepository;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentApiKeyEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Agent 对外调用秘钥仓储实现。
 * <p>
 * 严格遵循 infra 仓储约束：<b>仅注入本聚合 {@link AgentApiKeyMapper}</b>，不持有任何 Gateway /
 * DomainEventPublisher / 其他 Mapper / 其他 Repository；Entity ↔ Domain 转换只在本类完成，不外泄 Entity。
 * <p>
 * <b>存在性按业务编码 num 判定</b>：save 通过 {@code num} 查库决定 INSERT / UPDATE（业务身份，
 * 而非依赖 transient 主键 id 是否赋值）；findByNum / deleteByNum 对空白 num 做防御。
 */
@Repository
@RequiredArgsConstructor
public class AgentApiKeyRepositoryImpl implements AgentApiKeyRepository {

    private final AgentApiKeyMapper agentApiKeyMapper;

    /**
     * 持久化秘钥：按业务编码 {@code num} 查库判定新增 / 更新。
     * <ul>
     *   <li>num 命中已有行 → 回填其主键 id 后 {@code updateById}；</li>
     *   <li>未命中（含 num 为空）→ {@code insert} 并回填自增 id。</li>
     * </ul>
     *
     * @param aggregate 待保存的秘钥领域实体
     */
    @Override
    public void save(AgentApiKey aggregate) {
        AgentApiKeyEntity entity = AgentApiKeyEntity.fromDomain(aggregate);
        // 以业务编码 num 查库判定存在性（有行即存在），不依赖 transient id
        AgentApiKeyEntity existing = findEntityByNum(aggregate.getNum());
        if (existing == null) {
            agentApiKeyMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            agentApiKeyMapper.updateById(entity);
            aggregate.setId(existing.getId());
        }
    }

    /**
     * 按业务编号加载秘钥；num 为空或未命中返回 null。
     *
     * @param num 秘钥业务编号
     * @return 领域秘钥实体；不存在返回 null
     */
    @Override
    public AgentApiKey findByNum(String num) {
        return AgentApiKeyEntity.toDomain(findEntityByNum(num));
    }

    /**
     * 按业务编号逻辑删除秘钥；删除后该 key 认证立即失效。
     * <p>
     * logic-delete 配置下用 {@code delete(wrapper)} 让 MyBatis-Plus 自动生成
     * {@code UPDATE ... SET deleted=1 WHERE ...}（与 AgentRepositoryImpl 同思路，
     * 避免 {@code update(entity, wrapper)} 因忽略 deleted 字段导致 SET 子句为空）。
     *
     * @param num 秘钥业务编号；为空时直接返回，不发 SQL
     */
    @Override
    public void deleteByNum(String num) {
        if (StrUtil.isBlank(num)) {
            return;
        }
        agentApiKeyMapper.delete(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getNum, num));
    }

    /**
     * 按业务编码 num 查询有效（deleted=0）实体；num 为空或未命中返回 null。
     *
     * @param num 秘钥业务编号
     * @return 持久化实体；不存在返回 null
     */
    private AgentApiKeyEntity findEntityByNum(String num) {
        if (StrUtil.isBlank(num)) {
            return null;
        }
        return agentApiKeyMapper.selectOne(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getNum, num)
                .eq(AgentApiKeyEntity::getDeleted, 0));
    }
}
