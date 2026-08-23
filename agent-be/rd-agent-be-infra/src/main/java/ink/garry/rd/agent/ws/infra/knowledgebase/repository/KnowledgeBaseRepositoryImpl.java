package ink.garry.rd.agent.ws.infra.knowledgebase.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseRepository;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeBaseRepositoryImpl implements KnowledgeBaseRepository {

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public void save(KnowledgeBase aggregate) {
        Assert.notNull(aggregate, "KnowledgeBase 不能为 null");
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getNum, aggregate.getNum()));
        KnowledgeBaseEntity entity = KnowledgeBaseEntity.fromDomain(aggregate);
        if (existing == null) {
            knowledgeBaseMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            knowledgeBaseMapper.updateById(entity);
        }
    }

    @Override
    public KnowledgeBase findByNum(String kbNum) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getNum, kbNum));
        return KnowledgeBaseEntity.toDomain(entity);
    }

    @Override
    public boolean existsByWorkspaceAndName(String workspaceNum, String name, String excludeNum) {
        Long count = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getWorkspaceNum, workspaceNum)
                .eq(KnowledgeBaseEntity::getName, name)
                .ne(excludeNum != null && !excludeNum.isBlank(), KnowledgeBaseEntity::getNum, excludeNum));
        return count != null && count > 0;
    }
}
