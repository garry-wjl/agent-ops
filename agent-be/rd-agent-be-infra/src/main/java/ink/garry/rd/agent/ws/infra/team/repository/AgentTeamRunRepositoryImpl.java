package ink.garry.rd.agent.ws.infra.team.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRunRepository;
import ink.garry.rd.agent.ws.infra.team.entity.AgentTeamRunEntity;
import ink.garry.rd.agent.ws.infra.team.mapper.AgentTeamRunMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link AgentTeamRunRepository} 的 MyBatis 实现。
 */
@Repository
public class AgentTeamRunRepositoryImpl implements AgentTeamRunRepository {

    @Resource
    private AgentTeamRunMapper agentTeamRunMapper;

    @Override
    public void insert(RunRecord record, String operatorId) {
        AgentTeamRunEntity entity = new AgentTeamRunEntity();
        entity.setNum(record.num());
        entity.setTeamNum(record.teamNum());
        entity.setWorkspaceNum(record.workspaceNum());
        entity.setTeamName(record.teamName());
        entity.setMode(record.mode());
        entity.setInputText(record.inputText());
        entity.setStatus(record.status());
        entity.setOssSubPath(record.ossSubPath());
        entity.setSnapshotJson(record.snapshotJson());
        entity.setRuntimeJson(record.runtimeJson());
        entity.setCreateNo(operatorId);
        entity.setUpdateNo(operatorId);
        entity.setDeleted(0);
        entity.setCreateTime(record.createTime() == null ? LocalDateTime.now() : record.createTime());
        entity.setUpdateTime(entity.getCreateTime());
        agentTeamRunMapper.insert(entity);
    }

    @Override
    public void updateRuntime(String num, String status, String runtimeJson, String operatorId) {
        AgentTeamRunEntity entity = agentTeamRunMapper.selectOne(new LambdaQueryWrapper<AgentTeamRunEntity>()
                .eq(AgentTeamRunEntity::getNum, num)
                .eq(AgentTeamRunEntity::getDeleted, 0));
        if (entity == null) {
            throw new IllegalArgumentException("运行不存在");
        }
        entity.setStatus(status);
        entity.setRuntimeJson(runtimeJson);
        entity.setUpdateNo(operatorId);
        entity.setUpdateTime(LocalDateTime.now());
        agentTeamRunMapper.updateById(entity);
    }

    @Override
    public java.util.Optional<RunRecord> find(String workspaceNum, String num) {
        AgentTeamRunEntity entity = agentTeamRunMapper.selectOne(alive(workspaceNum).eq(AgentTeamRunEntity::getNum, num));
        return java.util.Optional.ofNullable(entity).map(AgentTeamRunRepositoryImpl::toRecord);
    }

    @Override
    public List<RunRecord> list(String workspaceNum) {
        return agentTeamRunMapper.selectList(alive(workspaceNum).orderByDesc(AgentTeamRunEntity::getCreateTime))
                .stream().map(AgentTeamRunRepositoryImpl::toRecord).toList();
    }

    private static LambdaQueryWrapper<AgentTeamRunEntity> alive(String workspaceNum) {
        return new LambdaQueryWrapper<AgentTeamRunEntity>()
                .eq(AgentTeamRunEntity::getWorkspaceNum, workspaceNum)
                .eq(AgentTeamRunEntity::getDeleted, 0);
    }

    private static RunRecord toRecord(AgentTeamRunEntity entity) {
        return new RunRecord(entity.getNum(), entity.getTeamNum(), entity.getWorkspaceNum(), entity.getTeamName(),
                entity.getMode(), entity.getInputText(), entity.getStatus(), entity.getOssSubPath(),
                entity.getSnapshotJson(), entity.getRuntimeJson(), entity.getUpdateNo(), entity.getCreateTime());
    }
}
