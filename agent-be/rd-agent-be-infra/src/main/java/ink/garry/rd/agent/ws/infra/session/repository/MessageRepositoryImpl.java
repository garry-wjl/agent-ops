package ink.garry.rd.agent.ws.infra.session.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.session.Message;
import ink.garry.rd.agent.ws.domain.session.repository.MessageRepository;
import ink.garry.rd.agent.ws.infra.session.entity.MessageEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageRepositoryImpl implements MessageRepository {
    private final MessageMapper messageMapper;

    @Override
    public void save(Message entity) {
        MessageEntity row = MessageEntity.fromDomain(entity);
        if (row.getId() == null) {
            messageMapper.insert(row);
            entity.setId(row.getId());
        } else {
            messageMapper.updateById(row);
        }
    }

    @Override
    public Message findByNum(String num) {
        MessageEntity entity = messageMapper.selectOne(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getNum, num)
                .eq(MessageEntity::getDeleted, 0));
        Message message = MessageEntity.toDomain(entity);
        if (message != null) {
            message.setMessageRepository(this);
        }
        return message;
    }

    @Override
    public void deleteByNum(String num) {
        // logic-delete: deleted 配置下，update(entity, wrapper) 会忽略 deleted 字段。
        // 用 delete(wrapper) 让 MyBatis-Plus 自动 SET deleted=1。
        messageMapper.delete(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getNum, num));
    }
}
