package ink.garry.rd.agent.ws.infra.evaluation.task.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.repository.EvalTaskRepository;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class EvalTaskRepositoryImpl implements EvalTaskRepository {

    @Resource
    private EvalTaskMapper evalTaskMapper;

    @Override
    public void save(EvalTask aggregate) {
        Assert.notNull(aggregate, "任务不能为 null");
        Assert.notBlank(aggregate.getNum(), "任务 num 不能为空");
        EvalTaskEntity existing = evalTaskMapper.selectOne(new LambdaQueryWrapper<EvalTaskEntity>()
                .eq(EvalTaskEntity::getNum, aggregate.getNum()));
        EvalTaskEntity entity = EvalTaskEntity.fromDomain(aggregate);
        if (existing == null) {
            evalTaskMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            evalTaskMapper.updateById(entity);
        }
    }

    @Override
    public EvalTask findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        return EvalTaskEntity.toDomain(evalTaskMapper.selectOne(new LambdaQueryWrapper<EvalTaskEntity>()
                .eq(EvalTaskEntity::getNum, num)));
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "任务 num 不能为空");
        evalTaskMapper.delete(new LambdaQueryWrapper<EvalTaskEntity>().eq(EvalTaskEntity::getNum, num));
    }
}
