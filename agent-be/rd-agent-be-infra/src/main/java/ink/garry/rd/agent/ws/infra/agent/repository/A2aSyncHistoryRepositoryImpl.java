package ink.garry.rd.agent.ws.infra.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import ink.garry.rd.agent.ws.domain.agent.repository.A2aSyncHistoryRepository;
import ink.garry.rd.agent.ws.infra.agent.entity.A2aSyncHistoryEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.A2aSyncHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * A2A 同步历史仓储实现（v2.6）。
 * <p>
 * 仅注入 {@link A2aSyncHistoryMapper}，按 impl-infra-module 约定不依赖其他 Bean。
 */
@Repository
@RequiredArgsConstructor
public class A2aSyncHistoryRepositoryImpl implements A2aSyncHistoryRepository {

    private final A2aSyncHistoryMapper mapper;

    @Override
    public void save(A2aSyncHistory history) {
        A2aSyncHistoryEntity entity = A2aSyncHistoryEntity.fromDomain(history);
        mapper.insert(entity);
        history.setId(entity.getId());
    }

    @Override
    public List<A2aSyncHistory> listByAgentNum(String agentNum, int limit) {
        if (agentNum == null || agentNum.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        LambdaQueryWrapper<A2aSyncHistoryEntity> wrapper = new LambdaQueryWrapper<A2aSyncHistoryEntity>()
                .eq(A2aSyncHistoryEntity::getAgentNum, agentNum)
                .orderByDesc(A2aSyncHistoryEntity::getSyncedAt)
                .last("LIMIT " + safeLimit);
        List<A2aSyncHistoryEntity> rows = mapper.selectList(wrapper);
        return rows.stream().map(A2aSyncHistoryEntity::toDomain).toList();
    }

    @Override
    public void purgeOldest(String agentNum, int keepCount) {
        if (agentNum == null || agentNum.isEmpty() || keepCount <= 0) {
            return;
        }
        // 1. 取该 agent 的第 keepCount+1 条 syncedAt 作为删除阈值
        LambdaQueryWrapper<A2aSyncHistoryEntity> cursor = new LambdaQueryWrapper<A2aSyncHistoryEntity>()
                .select(A2aSyncHistoryEntity::getSyncedAt)
                .eq(A2aSyncHistoryEntity::getAgentNum, agentNum)
                .orderByDesc(A2aSyncHistoryEntity::getSyncedAt)
                .last("LIMIT 1 OFFSET " + keepCount);
        List<A2aSyncHistoryEntity> threshold = mapper.selectList(cursor);
        if (threshold.isEmpty()) {
            return;
        }
        java.time.LocalDateTime cutoff = threshold.get(0).getSyncedAt();
        // 2. 物理删除早于该 syncedAt 的所有行（含相等以保证只剩 keepCount 条）
        LambdaQueryWrapper<A2aSyncHistoryEntity> delWrapper = new LambdaQueryWrapper<A2aSyncHistoryEntity>()
                .eq(A2aSyncHistoryEntity::getAgentNum, agentNum)
                .le(A2aSyncHistoryEntity::getSyncedAt, cutoff);
        mapper.delete(delWrapper);
    }
}
