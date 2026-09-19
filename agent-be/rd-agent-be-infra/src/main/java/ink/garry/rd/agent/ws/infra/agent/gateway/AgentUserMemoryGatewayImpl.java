package ink.garry.rd.agent.ws.infra.agent.gateway;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.agent.AgentUserMemory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentUserMemoryGateway;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentUserMemoryEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentUserMemoryMapper;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link AgentUserMemoryGateway} 的 MyBatis 实现。
 */
@Component
public class AgentUserMemoryGatewayImpl implements AgentUserMemoryGateway {

    @Resource
    private AgentUserMemoryMapper agentUserMemoryMapper;

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public AgentUserMemory find(String workspaceNum, String agentNum, String userId) {
        if (StrUtil.hasBlank(workspaceNum, agentNum, userId)) {
            return null;
        }
        AgentUserMemoryEntity entity = agentUserMemoryMapper.selectOne(ownerQuery(workspaceNum, agentNum, userId));
        if (entity == null) {
            return null;
        }
        return toDomain(entity);
    }

    @Override
    public void upsert(AgentUserMemory memory) {
        if (memory == null || StrUtil.hasBlank(memory.getWorkspaceNum(), memory.getAgentNum(), memory.getUserId())) {
            return;
        }
        AgentUserMemoryEntity existing = agentUserMemoryMapper.selectOne(
                ownerQuery(memory.getWorkspaceNum(), memory.getAgentNum(), memory.getUserId()));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AgentUserMemoryEntity entity = new AgentUserMemoryEntity();
            entity.setNum(StrUtil.blankToDefault(memory.getNum(), bizNumGenerator.generate("AUM")));
            entity.setWorkspaceNum(memory.getWorkspaceNum());
            entity.setAgentNum(memory.getAgentNum());
            entity.setUserId(memory.getUserId());
            entity.setMemoryMd(memory.getMemoryMd());
            entity.setDailyLedgerJson(memory.getDailyLedgerJson());
            entity.setDeleted(0);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            agentUserMemoryMapper.insert(entity);
            memory.setNum(entity.getNum());
            return;
        }
        existing.setMemoryMd(memory.getMemoryMd());
        existing.setDailyLedgerJson(memory.getDailyLedgerJson());
        existing.setUpdateTime(now);
        agentUserMemoryMapper.updateById(existing);
        memory.setNum(existing.getNum());
    }

    private static LambdaQueryWrapper<AgentUserMemoryEntity> ownerQuery(
            String workspaceNum, String agentNum, String userId) {
        return new LambdaQueryWrapper<AgentUserMemoryEntity>()
                .eq(AgentUserMemoryEntity::getWorkspaceNum, workspaceNum)
                .eq(AgentUserMemoryEntity::getAgentNum, agentNum)
                .eq(AgentUserMemoryEntity::getUserId, userId)
                .eq(AgentUserMemoryEntity::getDeleted, 0);
    }

    private static AgentUserMemory toDomain(AgentUserMemoryEntity entity) {
        return AgentUserMemory.builder()
                .num(entity.getNum())
                .workspaceNum(entity.getWorkspaceNum())
                .agentNum(entity.getAgentNum())
                .userId(entity.getUserId())
                .memoryMd(entity.getMemoryMd())
                .dailyLedgerJson(entity.getDailyLedgerJson())
                .build();
    }
}
