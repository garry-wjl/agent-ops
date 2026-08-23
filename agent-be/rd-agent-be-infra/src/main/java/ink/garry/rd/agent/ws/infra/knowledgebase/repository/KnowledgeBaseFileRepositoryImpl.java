package ink.garry.rd.agent.ws.infra.knowledgebase.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseFileRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexStatus;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseFileEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseFileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class KnowledgeBaseFileRepositoryImpl implements KnowledgeBaseFileRepository {

    @Resource
    private KnowledgeBaseFileMapper knowledgeBaseFileMapper;

    @Override
    public void save(KnowledgeBaseFile file) {
        Assert.notNull(file, "KnowledgeBaseFile 不能为 null");
        KnowledgeBaseFileEntity existing = knowledgeBaseFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                .eq(KnowledgeBaseFileEntity::getNum, file.getNum()));
        KnowledgeBaseFileEntity entity = KnowledgeBaseFileEntity.fromDomain(file);
        if (existing == null) {
            knowledgeBaseFileMapper.insert(entity);
            file.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            knowledgeBaseFileMapper.updateById(entity);
        }
    }

    @Override
    public KnowledgeBaseFile findByNum(String fileNum) {
        KnowledgeBaseFileEntity entity = knowledgeBaseFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                .eq(KnowledgeBaseFileEntity::getNum, fileNum));
        return KnowledgeBaseFileEntity.toDomain(entity);
    }

    @Override
    public List<KnowledgeBaseFile> listByKbNum(String kbNum) {
        return knowledgeBaseFileMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                        .eq(KnowledgeBaseFileEntity::getKbNum, kbNum))
                .stream()
                .map(KnowledgeBaseFileEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countByKbNum(String kbNum) {
        Long count = knowledgeBaseFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                .eq(KnowledgeBaseFileEntity::getKbNum, kbNum));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public int countIndexedByKbNum(String kbNum) {
        Long count = knowledgeBaseFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                .eq(KnowledgeBaseFileEntity::getKbNum, kbNum)
                .eq(KnowledgeBaseFileEntity::getIndexStatus, KbIndexStatus.READY.name()));
        return count == null ? 0 : count.intValue();
    }
}
