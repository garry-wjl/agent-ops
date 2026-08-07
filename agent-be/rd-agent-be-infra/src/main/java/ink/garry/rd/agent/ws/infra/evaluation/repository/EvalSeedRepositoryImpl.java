package ink.garry.rd.agent.ws.infra.evaluation.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvalSeedRepository;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvalSeedEntity;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvalSeedMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * EvalSeed 仓储实现：仅注入 EvalSeedMapper。
 */
@Repository
@RequiredArgsConstructor
public class EvalSeedRepositoryImpl implements EvalSeedRepository {

    private final EvalSeedMapper evalSeedMapper;

    @Override
    public void save(EvalSeed aggregate) {
        EvalSeedEntity entity = EvalSeedEntity.fromDomain(aggregate);
        if (entity.getId() == null) {
            evalSeedMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            evalSeedMapper.updateById(entity);
        }
    }

    @Override
    public EvalSeed findByNum(String num) {
        EvalSeedEntity entity = evalSeedMapper.selectOne(new LambdaQueryWrapper<EvalSeedEntity>()
                .eq(EvalSeedEntity::getNum, num)
                .eq(EvalSeedEntity::getDeleted, 0));
        EvalSeed s = EvalSeedEntity.toDomain(entity);
        if (s != null) {
            s.setEvalSeedRepository(this);
        }
        return s;
    }

    @Override
    public void deleteByNum(String num) {
        EvalSeedEntity update = new EvalSeedEntity();
        update.setDeleted(1);
        evalSeedMapper.update(update, new LambdaQueryWrapper<EvalSeedEntity>().eq(EvalSeedEntity::getNum, num));
    }
}
