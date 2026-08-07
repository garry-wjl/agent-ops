package ink.garry.rd.agent.ws.infra.evaluation.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationCaseRepository;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationCaseEntity;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvaluationCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * EvaluationCase 仓储实现：仅注入 EvaluationCaseMapper。
 */
@Repository
@RequiredArgsConstructor
public class EvaluationCaseRepositoryImpl implements EvaluationCaseRepository {

    private final EvaluationCaseMapper evaluationCaseMapper;

    @Override
    public void save(EvaluationCase aggregate) {
        EvaluationCaseEntity entity = EvaluationCaseEntity.fromDomain(aggregate);
        if (entity.getId() == null) {
            evaluationCaseMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            evaluationCaseMapper.updateById(entity);
        }
    }

    @Override
    public EvaluationCase findByNum(String num) {
        EvaluationCaseEntity entity = evaluationCaseMapper.selectOne(new LambdaQueryWrapper<EvaluationCaseEntity>()
                .eq(EvaluationCaseEntity::getNum, num)
                .eq(EvaluationCaseEntity::getDeleted, 0));
        EvaluationCase c = EvaluationCaseEntity.toDomain(entity);
        if (c != null) {
            c.setEvaluationCaseRepository(this);
        }
        return c;
    }

    @Override
    public void deleteByNum(String num) {
        EvaluationCaseEntity update = new EvaluationCaseEntity();
        update.setDeleted(1);
        evaluationCaseMapper.update(update, new LambdaQueryWrapper<EvaluationCaseEntity>().eq(EvaluationCaseEntity::getNum, num));
    }
}
