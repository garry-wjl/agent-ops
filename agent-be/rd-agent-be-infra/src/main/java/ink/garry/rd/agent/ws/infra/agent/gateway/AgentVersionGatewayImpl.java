package ink.garry.rd.agent.ws.infra.agent.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentVersionGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentVersionRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentVersionEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentVersionMapper;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AgentVersionGateway 实现：合并原 AgentNumGateway（generateAgentVersionNum）与
 * AgentVersionReadGateway（读能力 + currentFlag 翻转）。
 * <p>
 * 按"一聚合一个 Gateway"约定收敛：{@link #findByAgentNumAndStatus} 用于按状态查找（典型用例：找草稿）；
 * {@link #listByAgentNum} 按 update_time 倒序兼容 DRAFT 行（DRAFT 没有 published_at）。
 */
@Component
@RequiredArgsConstructor
public class AgentVersionGatewayImpl implements AgentVersionGateway {

    /** AgentVersion 业务编号前缀（v3.0 起 DRAFT 行也用此前缀） */
    private static final String VERSION_PREFIX = "AVN";

    private final AgentVersionMapper agentVersionMapper;
    private final AgentVersionRepository agentVersionRepository;
    private final BizNumGenerator bizNumGenerator;

    /** 生成 AgentVersion 业务编号（前缀 AVN）；v3.0 起 DRAFT 行也用此方法生成 num。 */
    @Override
    public String generateAgentVersionNum() {
        return bizNumGenerator.generate(VERSION_PREFIX);
    }

    /** 查找当前在线版本（current_flag=1 且 status=PUBLISHED）；无返回 null。 */
    @Override
    public AgentVersion findCurrent(String agentNum) {
        AgentVersionEntity entity = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getCurrentFlag, 1)
                .eq(AgentVersionEntity::getStatus, AgentVersionStatus.PUBLISHED.name())
                .eq(AgentVersionEntity::getDeleted, 0));
        return wire(AgentVersionEntity.toDomain(entity));
    }

    /** 按 agentNum + versionNum 查找（含 PUBLISHED / ARCHIVED；DRAFT 无 versionNum）。 */
    @Override
    public AgentVersion findByAgentNumAndVersionNum(String agentNum, String versionNum) {
        AgentVersionEntity entity = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getVersionNum, versionNum)
                .eq(AgentVersionEntity::getDeleted, 0));
        return wire(AgentVersionEntity.toDomain(entity));
    }

    /**
     * v3.0：按 agentNum + status 查找（典型用例：findByAgentNumAndStatus(agentNum, DRAFT)）。
     * 同 agentNum 下 DRAFT / PUBLISHED 至多 1 行；ARCHIVED 多行时返回 update_time 最新一条。
     */
    @Override
    public AgentVersion findByAgentNumAndStatus(String agentNum, AgentVersionStatus status) {
        if (status == null) {
            return null;
        }
        AgentVersionEntity entity = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getStatus, status.name())
                .eq(AgentVersionEntity::getDeleted, 0)
                .orderByDesc(AgentVersionEntity::getUpdateTime)
                .last("LIMIT 1"));
        return wire(AgentVersionEntity.toDomain(entity));
    }

    /**
     * 列出某 Agent 的所有版本行（DRAFT + PUBLISHED + ARCHIVED）。
     * <p>
     * v3.0：按 update_time 倒序兼容 DRAFT 行（DRAFT 没有 published_at）。
     */
    @Override
    public List<AgentVersion> listByAgentNum(String agentNum, int limit) {
        int safeLimit = limit <= 0 ? 50 : limit;
        List<AgentVersionEntity> rows = agentVersionMapper.selectList(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentNum, agentNum)
                .eq(AgentVersionEntity::getDeleted, 0)
                .orderByDesc(AgentVersionEntity::getUpdateTime)
                .last("LIMIT " + safeLimit));
        return rows.stream().map(AgentVersionEntity::toDomain).peek(this::wire).toList();
    }

    /**
     * 翻转 current 标记 + 同步 status：oldVersionId 置 currentFlag=0 / status=ARCHIVED；
     * newVersionId 置 currentFlag=1 / status=PUBLISHED。需事务保护。
     */
    @Override
    @Transactional
    public void switchCurrent(Long oldVersionId, Long newVersionId) {
        if (oldVersionId != null) {
            agentVersionMapper.update(null, new LambdaUpdateWrapper<AgentVersionEntity>()
                    .eq(AgentVersionEntity::getId, oldVersionId)
                    .set(AgentVersionEntity::getCurrentFlag, 0)
                    .set(AgentVersionEntity::getStatus, AgentVersionStatus.ARCHIVED.name()));
        }
        if (newVersionId != null) {
            agentVersionMapper.update(null, new LambdaUpdateWrapper<AgentVersionEntity>()
                    .eq(AgentVersionEntity::getId, newVersionId)
                    .set(AgentVersionEntity::getCurrentFlag, 1)
                    .set(AgentVersionEntity::getStatus, AgentVersionStatus.PUBLISHED.name()));
        }
    }

    private AgentVersion wire(AgentVersion v) {
        if (v != null) {
            v.setAgentVersionRepository(agentVersionRepository);
        }
        return v;
    }
}
