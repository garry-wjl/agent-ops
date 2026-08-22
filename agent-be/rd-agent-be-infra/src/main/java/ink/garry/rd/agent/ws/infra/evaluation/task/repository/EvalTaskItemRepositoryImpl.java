package ink.garry.rd.agent.ws.infra.evaluation.task.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;
import ink.garry.rd.agent.ws.domain.evaluation.task.repository.EvalTaskItemRepository;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskItemEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskItemMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class EvalTaskItemRepositoryImpl implements EvalTaskItemRepository {

    @Resource
    private EvalTaskItemMapper evalTaskItemMapper;

    @Override
    public void save(EvalTaskItem aggregate) {
        Assert.notNull(aggregate, "用例不能为 null");
        Assert.notBlank(aggregate.getNum(), "用例 num 不能为空");
        EvalTaskItemEntity existing = evalTaskItemMapper.selectOne(new LambdaQueryWrapper<EvalTaskItemEntity>()
                .eq(EvalTaskItemEntity::getNum, aggregate.getNum()));
        EvalTaskItemEntity entity = EvalTaskItemEntity.fromDomain(aggregate);
        if (existing == null) {
            evalTaskItemMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            evalTaskItemMapper.updateById(entity);
        }
    }

    @Override
    public EvalTaskItem findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        return EvalTaskItemEntity.toDomain(evalTaskItemMapper.selectOne(new LambdaQueryWrapper<EvalTaskItemEntity>()
                .eq(EvalTaskItemEntity::getNum, num)));
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "用例 num 不能为空");
        evalTaskItemMapper.delete(new LambdaQueryWrapper<EvalTaskItemEntity>().eq(EvalTaskItemEntity::getNum, num));
    }
}
