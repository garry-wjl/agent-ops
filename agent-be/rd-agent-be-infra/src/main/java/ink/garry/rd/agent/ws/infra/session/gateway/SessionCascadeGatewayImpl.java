package ink.garry.rd.agent.ws.infra.session.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionCascadeGateway;
import ink.garry.rd.agent.ws.infra.session.entity.InvocationTraceEntity;
import ink.garry.rd.agent.ws.infra.session.entity.MessageEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.InvocationTraceMapper;
import ink.garry.rd.agent.ws.infra.session.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话级联删除网关实现。
 * <p>
 * 使用 MyBatis-Plus 的 {@code delete(wrapper)} 而非 {@code update(entity, wrapper)}：
 * 在全局 {@code logic-delete-field: deleted} 配置下，自动生成 {@code UPDATE ... SET deleted=1}；
 * 否则手动 update + entity.setDeleted(1) 会被 logic-delete 忽略 deleted 字段，导致 SET 子句为空。
 */
@Component
@RequiredArgsConstructor
public class SessionCascadeGatewayImpl implements SessionCascadeGateway {
    private final MessageMapper messageMapper;
    private final InvocationTraceMapper invocationTraceMapper;

    @Override
    public void deleteMessagesBySessionNum(String sessionNum) {
        messageMapper.delete(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getSessionNum, sessionNum));
    }

    @Override
    public void deleteTracesBySessionNum(String sessionNum) {
        invocationTraceMapper.delete(new LambdaQueryWrapper<InvocationTraceEntity>()
                .eq(InvocationTraceEntity::getSessionNum, sessionNum));
    }
}
