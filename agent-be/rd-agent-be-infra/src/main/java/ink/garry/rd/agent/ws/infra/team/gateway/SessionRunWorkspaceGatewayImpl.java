package ink.garry.rd.agent.ws.infra.team.gateway;

import ink.garry.rd.agent.ws.domain.team.gateway.SessionRunWorkspaceGateway;
import ink.garry.rd.agent.ws.infra.team.entity.SessionRunWorkspaceEntity;
import ink.garry.rd.agent.ws.infra.team.mapper.SessionRunWorkspaceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 把会话和运行目录前缀写入 {@code session_run_workspace}。
 */
@Component
public class SessionRunWorkspaceGatewayImpl implements SessionRunWorkspaceGateway {

    @Resource
    private SessionRunWorkspaceMapper sessionRunWorkspaceMapper;

    @Override
    public void bind(String sessionNum, String runNum, String subPath) {
        SessionRunWorkspaceEntity existing = sessionRunWorkspaceMapper.selectById(sessionNum);
        if (existing == null) {
            SessionRunWorkspaceEntity entity = new SessionRunWorkspaceEntity();
            entity.setSessionNum(sessionNum);
            entity.setRunNum(runNum);
            entity.setSubPath(subPath);
            entity.setCreateTime(LocalDateTime.now());
            sessionRunWorkspaceMapper.insert(entity);
            return;
        }
        existing.setRunNum(runNum);
        existing.setSubPath(subPath);
        sessionRunWorkspaceMapper.updateById(existing);
    }

    @Override
    public Optional<String> findSubPath(String sessionNum) {
        SessionRunWorkspaceEntity entity = sessionRunWorkspaceMapper.selectById(sessionNum);
        if (entity == null || entity.getSubPath() == null || entity.getSubPath().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(entity.getSubPath());
    }
}
