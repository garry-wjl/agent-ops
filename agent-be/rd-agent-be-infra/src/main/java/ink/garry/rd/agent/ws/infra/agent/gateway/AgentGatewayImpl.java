package ink.garry.rd.agent.ws.infra.agent.gateway;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentRepository;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * AgentGateway 实现：合并原 AgentNumGateway（业务编码生成）与 AgentReadGateway（读能力）。
 * <p>
 * 按"一聚合一个 Gateway"约定收敛：sandbox=1 的 Agent 不出现在分页 / 名字校验中；
 * 名称唯一性边界由 owner 改为 workspace。
 */
@Component
@RequiredArgsConstructor
public class AgentGatewayImpl implements AgentGateway {

    /** Agent 业务编号前缀 */
    private static final String AGENT_PREFIX = "AGT";

    private final AgentMapper agentMapper;
    private final AgentRepository agentRepository;
    private final BizNumGenerator bizNumGenerator;

    /** 生成 Agent 业务编号（前缀 AGT）。 */
    @Override
    public String generateAgentNum() {
        return bizNumGenerator.generate(AGENT_PREFIX);
    }

    /** 生成 traceId（去掉短横线的 UUID）。 */
    @Override
    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public PageResult<Agent> pageQuery(AgentPageCondition c) {
        int pageNo = c.pageNo() == null ? 1 : c.pageNo();
        int pageSize = c.pageSize() == null ? 20 : c.pageSize();
        Page<AgentEntity> page = Page.of(pageNo, pageSize);

        LambdaQueryWrapper<AgentEntity> w = new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getDeleted, 0)
                .eq(AgentEntity::getSandbox, 0)
                .eq(StrUtil.isNotBlank(c.creationMode()), AgentEntity::getCreationMode, c.creationMode())
                .eq(StrUtil.isNotBlank(c.agentType()), AgentEntity::getAgentType, c.agentType())
                .eq(StrUtil.isNotBlank(c.status()), AgentEntity::getStatus, c.status())
                .and(StrUtil.isNotBlank(c.keyword()), q -> q
                        .like(AgentEntity::getName, c.keyword())
                        .or().like(AgentEntity::getDescription, c.keyword()))
                .orderByDesc(AgentEntity::getUpdateTime);

        Page<AgentEntity> result = agentMapper.selectPage(page, w);
        List<Agent> list = result.getRecords().stream()
                .map(AgentEntity::toDomain)
                .peek(a -> a.setAgentRepository(agentRepository))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    /** 校验同 workspace 下 name 是否已存在（排除 deleted=1、sandbox=1）。 */
    @Override
    public boolean existsByWorkspaceAndName(String workspaceNum, String name) {
        Long cnt = agentMapper.selectCount(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceNum, workspaceNum)
                .eq(AgentEntity::getName, name)
                .eq(AgentEntity::getSandbox, 0)
                .eq(AgentEntity::getDeleted, 0));
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean allAreNormal(List<String> agentNums) {
        if (agentNums == null || agentNums.isEmpty()) {
            return true;
        }
        Long cnt = agentMapper.selectCount(new LambdaQueryWrapper<AgentEntity>()
                .in(AgentEntity::getNum, agentNums)
                .eq(AgentEntity::getAgentType, "NORMAL")
                .eq(AgentEntity::getDeleted, 0));
        return cnt != null && cnt == agentNums.size();
    }
}
