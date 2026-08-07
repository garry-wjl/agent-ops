package ink.garry.rd.agent.ws.infra.session.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.session.InvocationTrace;
import ink.garry.rd.agent.ws.domain.session.repository.InvocationTraceRepository;
import ink.garry.rd.agent.ws.infra.session.entity.InvocationTraceEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.InvocationTraceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InvocationTraceRepositoryImpl implements InvocationTraceRepository {
    private final InvocationTraceMapper invocationTraceMapper;

    @Override
    public void save(InvocationTrace entity) {
        InvocationTraceEntity row = InvocationTraceEntity.fromDomain(entity);
        if (row.getId() == null) {
            invocationTraceMapper.insert(row);
            entity.setId(row.getId());
        } else {
            invocationTraceMapper.updateById(row);
        }
    }

    @Override
    public InvocationTrace findByNum(String num) {
        InvocationTraceEntity entity = invocationTraceMapper.selectOne(new LambdaQueryWrapper<InvocationTraceEntity>()
                .eq(InvocationTraceEntity::getNum, num)
                .eq(InvocationTraceEntity::getDeleted, 0));
        InvocationTrace trace = InvocationTraceEntity.toDomain(entity);
        if (trace != null) {
            trace.setInvocationTraceRepository(this);
        }
        return trace;
    }

    @Override
    public void deleteByNum(String num) {
        // logic-delete: deleted 配置下用 delete(wrapper) 自动 SET deleted=1。
        invocationTraceMapper.delete(new LambdaQueryWrapper<InvocationTraceEntity>()
                .eq(InvocationTraceEntity::getNum, num));
    }
}
