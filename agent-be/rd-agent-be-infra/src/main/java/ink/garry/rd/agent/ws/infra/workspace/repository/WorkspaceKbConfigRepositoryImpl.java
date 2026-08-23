package ink.garry.rd.agent.ws.infra.workspace.repository;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.workspace.repository.WorkspaceKbConfigRepository;
import ink.garry.rd.agent.ws.domain.workspace.valueobject.WorkspaceKbConfig;
import ink.garry.rd.agent.ws.infra.workspace.entity.WorkspaceKbConfigEntity;
import ink.garry.rd.agent.ws.infra.workspace.mapper.WorkspaceKbConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class WorkspaceKbConfigRepositoryImpl implements WorkspaceKbConfigRepository {

    @Resource
    private WorkspaceKbConfigMapper workspaceKbConfigMapper;

    @Override
    public void save(WorkspaceKbConfig config, String operatorId) {
        WorkspaceKbConfigEntity existing = workspaceKbConfigMapper.selectOne(new LambdaQueryWrapper<WorkspaceKbConfigEntity>()
                .eq(WorkspaceKbConfigEntity::getWorkspaceNum, config.getWorkspaceNum()));
        WorkspaceKbConfigEntity entity = new WorkspaceKbConfigEntity();
        entity.setWorkspaceNum(config.getWorkspaceNum());
        entity.setConfigJson(JSON.toJSONString(config));
        entity.setDeleted(0);
        entity.setUpdateNo(operatorId);
        entity.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            entity.setCreateNo(operatorId);
            entity.setCreateTime(LocalDateTime.now());
            workspaceKbConfigMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            entity.setCreateNo(existing.getCreateNo());
            entity.setCreateTime(existing.getCreateTime());
            workspaceKbConfigMapper.updateById(entity);
        }
    }

    @Override
    public WorkspaceKbConfig findByWorkspaceNum(String workspaceNum) {
        WorkspaceKbConfigEntity entity = workspaceKbConfigMapper.selectOne(new LambdaQueryWrapper<WorkspaceKbConfigEntity>()
                .eq(WorkspaceKbConfigEntity::getWorkspaceNum, workspaceNum));
        if (entity == null || entity.getConfigJson() == null) {
            return null;
        }
        WorkspaceKbConfig config = JSON.parseObject(entity.getConfigJson(), WorkspaceKbConfig.class);
        if (config != null) {
            config.setWorkspaceNum(workspaceNum);
        }
        return config;
    }
}
