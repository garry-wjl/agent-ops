package ink.garry.rd.agent.ws.infra.session.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.infra.session.entity.SessionEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SessionRepositoryImpl implements SessionRepository {
    private final SessionMapper sessionMapper;

    @Override
    public void save(Session aggregate) {
        SessionEntity entity = SessionEntity.fromDomain(aggregate);
        if (entity.getId() == null) {
            sessionMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            sessionMapper.updateById(entity);
        }
    }

    @Override
    public Session findByNum(String num) {
        SessionEntity entity = sessionMapper.selectOne(new LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getNum, num)
                .eq(SessionEntity::getDeleted, 0));
        Session session = SessionEntity.toDomain(entity);
        if (session != null) {
            session.setSessionRepository(this);
        }
        return session;
    }

    @Override
    public void deleteByNum(String num) {
        // logic-delete: deleted 配置下，update(entity, wrapper) 会忽略 deleted 字段导致 SET 子句为空。
        // 用 delete(wrapper) 让 MyBatis-Plus 自动生成 UPDATE ... SET deleted=1 WHERE ...
        sessionMapper.delete(new LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getNum, num));
    }
}
