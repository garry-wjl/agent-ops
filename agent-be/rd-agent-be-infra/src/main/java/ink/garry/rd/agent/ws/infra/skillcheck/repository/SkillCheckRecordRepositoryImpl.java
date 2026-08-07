package ink.garry.rd.agent.ws.infra.skillcheck.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord;
import ink.garry.rd.agent.ws.domain.skillcheck.repository.SkillCheckRecordRepository;
import ink.garry.rd.agent.ws.infra.skillcheck.entity.SkillCheckRecordEntity;
import ink.garry.rd.agent.ws.infra.skillcheck.mapper.SkillCheckRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * SkillCheckRecord 仓储实现（v3.0）。
 * <p>
 * 遵守 impl-infra-module 约束：仅注入本聚合 {@link SkillCheckRecordMapper}；不持有 Gateway /
 * DomainEventPublisher / 其它聚合 Repository。检测记录常规仅 INSERT（不可变留痕）；保留
 * upsert 语义以兼容潜在的状态修订场景。存在性判定按业务编号 {@code num} 查库。
 */
@Repository
public class SkillCheckRecordRepositoryImpl implements SkillCheckRecordRepository {

    @Resource
    private SkillCheckRecordMapper skillCheckRecordMapper;

    @Override
    public void save(SkillCheckRecord aggregate) {
        Assert.notNull(aggregate, "SkillCheckRecord 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "SkillCheckRecord num 不能为空");

        SkillCheckRecordEntity existing = skillCheckRecordMapper.selectOne(
                new LambdaQueryWrapper<SkillCheckRecordEntity>()
                        .eq(SkillCheckRecordEntity::getNum, num));
        SkillCheckRecordEntity entity = SkillCheckRecordEntity.fromDomain(aggregate);
        if (existing == null) {
            skillCheckRecordMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            skillCheckRecordMapper.updateById(entity);
        }
    }

    @Override
    public SkillCheckRecord findByNum(String num) {
        SkillCheckRecordEntity entity = skillCheckRecordMapper.selectOne(
                new LambdaQueryWrapper<SkillCheckRecordEntity>()
                        .eq(SkillCheckRecordEntity::getNum, num));
        return SkillCheckRecordEntity.toDomain(entity);
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "SkillCheckRecord num 不能为空");
        skillCheckRecordMapper.delete(new LambdaQueryWrapper<SkillCheckRecordEntity>()
                .eq(SkillCheckRecordEntity::getNum, num));
    }
}
