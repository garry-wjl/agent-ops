package ink.garry.rd.agent.ws.infra.knowledgebase.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KbIndexTask;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KbIndexTaskRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexTaskStatus;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KbIndexTaskEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KbIndexTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class KbIndexTaskRepositoryImpl implements KbIndexTaskRepository {

    @Resource
    private KbIndexTaskMapper kbIndexTaskMapper;

    @Override
    public void save(KbIndexTask task) {
        Assert.notNull(task, "KbIndexTask 不能为 null");
        KbIndexTaskEntity existing = kbIndexTaskMapper.selectOne(new LambdaQueryWrapper<KbIndexTaskEntity>()
                .eq(KbIndexTaskEntity::getNum, task.getNum()));
        KbIndexTaskEntity entity = KbIndexTaskEntity.fromDomain(task);
        if (existing == null) {
            kbIndexTaskMapper.insert(entity);
            task.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            kbIndexTaskMapper.updateById(entity);
        }
    }

    @Override
    public KbIndexTask findByNum(String taskNum) {
        KbIndexTaskEntity entity = kbIndexTaskMapper.selectOne(new LambdaQueryWrapper<KbIndexTaskEntity>()
                .eq(KbIndexTaskEntity::getNum, taskNum));
        return KbIndexTaskEntity.toDomain(entity);
    }

    @Override
    public List<KbIndexTask> listPending(int limit) {
        return kbIndexTaskMapper.selectList(new LambdaQueryWrapper<KbIndexTaskEntity>()
                        .eq(KbIndexTaskEntity::getStatus, KbIndexTaskStatus.PENDING.name())
                        .orderByAsc(KbIndexTaskEntity::getCreateTime)
                        .last("LIMIT " + limit))
                .stream()
                .map(KbIndexTaskEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<KbIndexTask> listByKbNumAndStatus(String kbNum, String status) {
        return kbIndexTaskMapper.selectList(new LambdaQueryWrapper<KbIndexTaskEntity>()
                        .eq(KbIndexTaskEntity::getKbNum, kbNum)
                        .eq(KbIndexTaskEntity::getStatus, status))
                .stream()
                .map(KbIndexTaskEntity::toDomain)
                .collect(Collectors.toList());
    }
}
