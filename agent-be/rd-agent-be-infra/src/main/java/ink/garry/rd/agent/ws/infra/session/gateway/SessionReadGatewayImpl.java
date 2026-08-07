package ink.garry.rd.agent.ws.infra.session.gateway;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.domain.session.Message;
import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionReadGateway;
import ink.garry.rd.agent.ws.domain.session.repository.MessageRepository;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.infra.session.entity.MessageEntity;
import ink.garry.rd.agent.ws.infra.session.entity.SessionEntity;
import ink.garry.rd.agent.ws.infra.session.mapper.MessageMapper;
import ink.garry.rd.agent.ws.infra.session.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionReadGatewayImpl implements SessionReadGateway {
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    @Override
    public PageResult<Session> pageQuery(String creatorUserId, String agentNum, Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null ? 1 : pageNo;
        int safePageSize = pageSize == null ? 20 : pageSize;
        Page<SessionEntity> page = Page.of(safePageNo, safePageSize);
        LambdaQueryWrapper<SessionEntity> wrapper = new LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getCreatorUserId, creatorUserId)
                .eq(SessionEntity::getDeleted, 0)
                .eq(StrUtil.isNotBlank(agentNum), SessionEntity::getAgentNum, agentNum)
                .orderByDesc(SessionEntity::getLastMessageAt)
                .orderByDesc(SessionEntity::getCreateTime);
        Page<SessionEntity> result = sessionMapper.selectPage(page, wrapper);
        List<Session> list = result.getRecords().stream()
                .map(SessionEntity::toDomain)
                .peek(s -> s.setSessionRepository(sessionRepository))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public List<Message> listMessages(String sessionNum, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        return messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getSessionNum, sessionNum)
                        .eq(MessageEntity::getDeleted, 0)
                        .orderByAsc(MessageEntity::getCreateTime)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(MessageEntity::toDomain)
                .peek(m -> m.setMessageRepository(messageRepository))
                .toList();
    }
}
