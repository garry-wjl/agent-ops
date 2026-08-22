package ink.garry.rd.agent.ws.infra.evaluation.grader.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.grader.EvalGrader;
import ink.garry.rd.agent.ws.domain.evaluation.grader.repository.EvalGraderRepository;
import ink.garry.rd.agent.ws.infra.evaluation.grader.entity.EvalGraderEntity;
import ink.garry.rd.agent.ws.infra.evaluation.grader.mapper.EvalGraderMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class EvalGraderRepositoryImpl implements EvalGraderRepository {

    @Resource
    private EvalGraderMapper evalGraderMapper;

    @Override
    public void save(EvalGrader aggregate) {
        Assert.notNull(aggregate, "评估器不能为 null");
        Assert.notBlank(aggregate.getNum(), "评估器 num 不能为空");
        EvalGraderEntity existing = evalGraderMapper.selectOne(new LambdaQueryWrapper<EvalGraderEntity>()
                .eq(EvalGraderEntity::getNum, aggregate.getNum()));
        EvalGraderEntity entity = EvalGraderEntity.fromDomain(aggregate);
        if (existing == null) {
            evalGraderMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            evalGraderMapper.updateById(entity);
        }
    }

    @Override
    public EvalGrader findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        return EvalGraderEntity.toDomain(evalGraderMapper.selectOne(new LambdaQueryWrapper<EvalGraderEntity>()
                .eq(EvalGraderEntity::getNum, num)));
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "评估器 num 不能为空");
        evalGraderMapper.delete(new LambdaQueryWrapper<EvalGraderEntity>().eq(EvalGraderEntity::getNum, num));
    }
}
