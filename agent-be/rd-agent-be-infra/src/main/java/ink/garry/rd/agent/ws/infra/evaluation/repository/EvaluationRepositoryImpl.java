package ink.garry.rd.agent.ws.infra.evaluation.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationRepository;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationEntity;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvaluationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Evaluation 仓储实现：仅注入 EvaluationMapper；负责 Entity ↔ Domain 转换。
 */
@Repository
@RequiredArgsConstructor
public class EvaluationRepositoryImpl implements EvaluationRepository {

    private final EvaluationMapper evaluationMapper;

    @Override
    public void save(Evaluation aggregate) {
        EvaluationEntity entity = EvaluationEntity.fromDomain(aggregate);
        if (entity.getId() == null) {
            evaluationMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            evaluationMapper.updateById(entity);
        }
    }

    @Override
    public Evaluation findByNum(String num) {
        EvaluationEntity entity = evaluationMapper.selectOne(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getNum, num)
                .eq(EvaluationEntity::getDeleted, 0));
        Evaluation eval = EvaluationEntity.toDomain(entity);
        if (eval != null) {
            eval.setEvaluationRepository(this);
        }
        return eval;
    }

    @Override
    public void deleteByNum(String num) {
        EvaluationEntity update = new EvaluationEntity();
        update.setDeleted(1);
        evaluationMapper.update(update, new LambdaQueryWrapper<EvaluationEntity>().eq(EvaluationEntity::getNum, num));
    }
}
