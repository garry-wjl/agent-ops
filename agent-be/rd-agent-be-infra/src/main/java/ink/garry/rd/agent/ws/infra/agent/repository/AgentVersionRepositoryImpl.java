package ink.garry.rd.agent.ws.infra.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentVersionRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentVersionEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * AgentVersion 仓储实现：仅注入 AgentVersionMapper。
 * <p>
 * v3.0：支持 DRAFT 行 INSERT/UPDATE 与物理删除（草稿无审计价值；与 v2.x AgentDraftRepositoryImpl 保持
 * 一致的旁路 logic-delete 思路）；PUBLISHED / ARCHIVED 行仍走 logic-delete。
 */
@Repository
@RequiredArgsConstructor
public class AgentVersionRepositoryImpl implements AgentVersionRepository {

    private final AgentVersionMapper agentVersionMapper;

    /**
     * 持久化版本：DRAFT 行允许 INSERT/UPDATE config_snapshot；PUBLISHED/ARCHIVED 行常规仅 INSERT 或翻转标记。
     */
    @Override
    public void save(AgentVersion aggregate) {
        AgentVersionEntity entity = AgentVersionEntity.fromDomain(aggregate);
        if (entity.getId() == null) {
            agentVersionMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            agentVersionMapper.updateById(entity);
        }
    }

    /** 按业务编号加载版本（含 DRAFT / PUBLISHED / ARCHIVED）。 */
    @Override
    public AgentVersion findByNum(String num) {
        AgentVersionEntity entity = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getNum, num)
                .eq(AgentVersionEntity::getDeleted, 0));
        AgentVersion v = AgentVersionEntity.toDomain(entity);
        if (v != null) {
            v.setAgentVersionRepository(this);
        }
        return v;
    }

    /**
     * 按业务编号删除版本：
     * <ul>
     *   <li>DRAFT 行：物理删除（旁路 logic-delete，避免后续创建草稿时唯一约束冲突）；</li>
     *   <li>PUBLISHED / ARCHIVED 行：logic-delete（保留审计）。</li>
     * </ul>
     * 实现路径：先按 num 加载实体判断状态。
     */
    @Override
    public void deleteByNum(String num) {
        AgentVersionEntity existing = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getNum, num));
        if (existing == null) {
            return;
        }
        if (AgentVersionStatus.DRAFT.name().equals(existing.getStatus())) {
            // 物理删除（草稿无审计价值；与 v2.x AgentDraftRepositoryImpl 修复同思路）
            agentVersionMapper.deleteById(existing.getId());
        } else {
            AgentVersionEntity update = new AgentVersionEntity();
            update.setDeleted(1);
            agentVersionMapper.update(update, new LambdaQueryWrapper<AgentVersionEntity>()
                    .eq(AgentVersionEntity::getNum, num));
        }
    }
}
