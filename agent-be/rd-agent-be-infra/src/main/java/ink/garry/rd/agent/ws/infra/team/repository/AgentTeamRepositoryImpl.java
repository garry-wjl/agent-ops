package ink.garry.rd.agent.ws.infra.team.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.team.repository.AgentTeamRepository;
import ink.garry.rd.agent.ws.infra.team.entity.AgentTeamEntity;
import ink.garry.rd.agent.ws.infra.team.mapper.AgentTeamMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link AgentTeamRepository} 的 MyBatis 实现。
 */
@Repository
public class AgentTeamRepositoryImpl implements AgentTeamRepository {

    @Resource
    private AgentTeamMapper agentTeamMapper;

    @Override
    public void insert(TeamRecord record, String operatorId) {
        AgentTeamEntity entity = toEntity(record);
        entity.setCreateNo(operatorId);
        entity.setUpdateNo(operatorId);
        entity.setDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(entity.getCreateTime());
        agentTeamMapper.insert(entity);
    }

    @Override
    public void update(TeamRecord record, String operatorId) {
        AgentTeamEntity existing = require(record.workspaceNum(), record.num());
        existing.setName(record.name());
        existing.setDescription(record.description());
        existing.setMode(record.mode());
        existing.setStatus(record.status());
        existing.setDefinitionJson(record.definitionJson());
        existing.setUpdateNo(operatorId);
        existing.setUpdateTime(LocalDateTime.now());
        agentTeamMapper.updateById(existing);
    }

    @Override
    public java.util.Optional<TeamRecord> find(String workspaceNum, String num) {
        AgentTeamEntity entity = agentTeamMapper.selectOne(alive(workspaceNum).eq(AgentTeamEntity::getNum, num));
        return java.util.Optional.ofNullable(entity).map(AgentTeamRepositoryImpl::toRecord);
    }

    @Override
    public List<TeamRecord> list(String workspaceNum) {
        return agentTeamMapper.selectList(alive(workspaceNum).orderByDesc(AgentTeamEntity::getUpdateTime))
                .stream().map(AgentTeamRepositoryImpl::toRecord).toList();
    }

    @Override
    public void softDelete(String workspaceNum, String num, String operatorId) {
        AgentTeamEntity existing = require(workspaceNum, num);
        existing.setDeleted(1);
        existing.setUpdateNo(operatorId);
        existing.setUpdateTime(LocalDateTime.now());
        agentTeamMapper.updateById(existing);
    }

    private AgentTeamEntity require(String workspaceNum, String num) {
        AgentTeamEntity entity = agentTeamMapper.selectOne(alive(workspaceNum).eq(AgentTeamEntity::getNum, num));
        if (entity == null) {
            throw new IllegalArgumentException("团队不存在");
        }
        return entity;
    }

    private static LambdaQueryWrapper<AgentTeamEntity> alive(String workspaceNum) {
        return new LambdaQueryWrapper<AgentTeamEntity>()
                .eq(AgentTeamEntity::getWorkspaceNum, workspaceNum)
                .eq(AgentTeamEntity::getDeleted, 0);
    }

    private static AgentTeamEntity toEntity(TeamRecord record) {
        AgentTeamEntity entity = new AgentTeamEntity();
        entity.setNum(record.num());
        entity.setWorkspaceNum(record.workspaceNum());
        entity.setName(record.name());
        entity.setDescription(record.description());
        entity.setMode(record.mode());
        entity.setStatus(record.status());
        entity.setDefinitionJson(record.definitionJson());
        return entity;
    }

    private static TeamRecord toRecord(AgentTeamEntity entity) {
        return new TeamRecord(entity.getNum(), entity.getWorkspaceNum(), entity.getName(), entity.getDescription(),
                entity.getMode(), entity.getStatus(), entity.getDefinitionJson(), entity.getUpdateNo(), entity.getUpdateTime());
    }
}
