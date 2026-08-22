package ink.garry.rd.agent.ws.infra.evaluation.dataset.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.repository.EvalDatasetRepository;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 评测集仓储实现：仅注入 Mapper。
 */
@Repository
public class EvalDatasetRepositoryImpl implements EvalDatasetRepository {

    @Resource
    private EvalDatasetMapper evalDatasetMapper;

    @Override
    public void save(EvalDataset aggregate) {
        Assert.notNull(aggregate, "评测集不能为 null");
        Assert.notBlank(aggregate.getNum(), "评测集 num 不能为空");
        EvalDatasetEntity existing = evalDatasetMapper.selectOne(new LambdaQueryWrapper<EvalDatasetEntity>()
                .eq(EvalDatasetEntity::getNum, aggregate.getNum()));
        EvalDatasetEntity entity = EvalDatasetEntity.fromDomain(aggregate);
        if (existing == null) {
            evalDatasetMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            evalDatasetMapper.updateById(entity);
        }
    }

    @Override
    public EvalDataset findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        return EvalDatasetEntity.toDomain(evalDatasetMapper.selectOne(new LambdaQueryWrapper<EvalDatasetEntity>()
                .eq(EvalDatasetEntity::getNum, num)));
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "评测集 num 不能为空");
        evalDatasetMapper.delete(new LambdaQueryWrapper<EvalDatasetEntity>()
                .eq(EvalDatasetEntity::getNum, num));
    }
}
